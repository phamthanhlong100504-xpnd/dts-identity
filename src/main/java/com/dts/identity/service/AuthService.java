package com.dts.identity.service;

import com.dts.identity.config.SecurityProperties;
import com.dts.identity.dto.request.*;
import com.dts.identity.dto.response.AuthResponse;
import com.dts.identity.dto.response.AuthResponse.UserInfo;
import com.dts.identity.entity.*;
import com.dts.identity.entity.VerificationCode.VerificationType;
import com.dts.identity.exception.BusinessException;
import com.dts.identity.repository.*;
import com.dts.identity.security.JwtProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final SecurityProperties securityProperties;

    // ==================== LOGIN ====================

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByIdentifier(request.username())
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.UNAUTHORIZED));

        // Check brute-force lock
        if (user.isLocked()) {
            throw new BusinessException(
                    "Account locked until " + user.getLockedUntil() + ". Please try again later.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        // Check banned
        if (user.getStatus() == User.UserStatus.BANNED) {
            throw new BusinessException("Account has been banned", HttpStatus.FORBIDDEN);
        }

        // Verify password
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            incrementFailedAttempts(user);
            throw new BusinessException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        // Clear failed attempts on success
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return buildAuthResponse(user, request.deviceInfo());
    }

    // ==================== REGISTER ====================

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check duplicates
        if (userRepository.existsByUsernameAndDeletedAtIsNull(request.username())) {
            throw new BusinessException("Username already taken");
        }
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
            throw new BusinessException("Email already registered");
        }
        if (userRepository.existsByPhoneNumberAndDeletedAtIsNull(request.phoneNumber())) {
            throw new BusinessException("Phone number already registered");
        }

        // Assign STUDENT role by default
        Role studentRole = roleRepository.findByName(Role.RoleType.ROLE_STUDENT)
                .orElseThrow(() -> new BusinessException("Default role not found", HttpStatus.INTERNAL_SERVER_ERROR));

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .birthOfDate(request.birthOfDate())
                .phoneNumber(request.phoneNumber())
                .status(User.UserStatus.PENDING)
                .build();
        user = userRepository.save(user);

        // Assign role
        UserRole userRole = UserRole.builder()
                .userId(user.getId())
                .roleId(studentRole.getId())
                .build();
        userRoleRepository.save(userRole);

        // Generate OTP for email verification
        generateAndSendVerificationCode(user, VerificationType.REGISTER);

        log.info("User registered: id={}, username={}", user.getId(), user.getUsername());
        return buildAuthResponse(user, null);
    }

    // ==================== REFRESH TOKEN ====================

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        Claims claims;
        try {
            claims = jwtProvider.validateRefreshToken(request.refreshToken());
        } catch (Exception e) {
            throw new BusinessException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);
        }

        // Verify refresh token exists in DB and is not revoked
        String tokenHash = hashToken(request.refreshToken());
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException("Refresh token not recognized", HttpStatus.UNAUTHORIZED));

        if (!storedToken.isValid()) {
            throw new BusinessException("Refresh token has been revoked or expired", HttpStatus.UNAUTHORIZED);
        }

        // Revoke old token (rotation)
        storedToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(storedToken);

        UUID userId = jwtProvider.getUserId(claims);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.UNAUTHORIZED));

        if (user.getDeletedAt() != null) {
            throw new BusinessException("Account has been deleted", HttpStatus.UNAUTHORIZED);
        }

        return buildAuthResponse(user, storedToken.getDeviceInfo());
    }

    // ==================== LOGOUT ====================

    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
        log.info("User logged out: id={}", userId);
    }

    // ==================== VERIFICATION ====================

    @Transactional
    public void verifyCode(VerifyCodeRequest request) {
        User user = userRepository.findByIdentifier(request.identifier())
                .orElseThrow(() -> new BusinessException("User not found"));

        VerificationType type;
        try {
            type = VerificationType.valueOf(request.type().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid verification type: " + request.type());
        }

        VerificationCode code = verificationCodeRepository
                .findLatestByUserIdAndType(user.getId(), type)
                .orElseThrow(() -> new BusinessException("No verification code found. Please request a new one."));

        if (!code.isValid()) {
            throw new BusinessException("Verification code has expired or already used");
        }

        if (code.getAttempts() >= securityProperties.verificationCode().maxAttempts()) {
            throw new BusinessException("Too many failed attempts. Please request a new code.");
        }

        if (!hashToken(request.code()).equals(code.getCodeHash())) {
            code.setAttempts(code.getAttempts() + 1);
            verificationCodeRepository.save(code);
            throw new BusinessException("Invalid verification code");
        }

        // Mark used
        code.setUsedAt(Instant.now());
        verificationCodeRepository.save(code);

        // Apply type-specific logic
        switch (type) {
            case REGISTER -> {
                user.setEmailVerifiedAt(Instant.now());
                user.setStatus(User.UserStatus.ACTIVE);
                userRepository.save(user);
                log.info("User verified via REGISTER: id={}", user.getId());
            }
            case CHANGE_EMAIL -> {
                user.setEmailVerifiedAt(Instant.now());
                userRepository.save(user);
                log.info("Email change verified: id={}", user.getId());
            }
            case RESET_PASSWORD -> {
                // Password is reset via a separate endpoint; just mark for now
                log.info("Reset password OTP verified: id={}", user.getId());
            }
        }
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BusinessException("Old password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Revoke all existing tokens on password change (security best practice)
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
        log.info("Password changed for user: id={}", userId);
    }

    // ==================== INTERNAL HELPERS ====================

    private AuthResponse buildAuthResponse(User user, String deviceInfo) {
        List<String> roleNames = getUserRoles(user.getId());
        List<String> permissionNames = getUserPermissions(user.getId());

        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getUsername(), roleNames, permissionNames);
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        // Store refresh token
        RefreshToken tokenEntity = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hashToken(refreshToken))
                .deviceInfo(deviceInfo)
                .expiresAt(Instant.now().plusMillis(jwtProvider.getAccessExpirationMs()))
                .build();
        refreshTokenRepository.save(tokenEntity);

        UserInfo userInfo = new UserInfo(
                user.getId(), user.getUsername(), user.getEmail(),
                user.getFullName(), roleNames, permissionNames);

        return new AuthResponse(accessToken, refreshToken, "Bearer",
                jwtProvider.getAccessExpirationMs() / 1000, userInfo);
    }

    private List<String> getUserRoles(UUID userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> ur.getRole().getName().name())
                .collect(Collectors.toList());
    }

    private List<String> getUserPermissions(UUID userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .flatMap(ur -> rolePermissionRepository.findByRoleId(ur.getRoleId()).stream())
                .map(rp -> rp.getPermission().getName())
                .distinct()
                .collect(Collectors.toList());
    }

    private void incrementFailedAttempts(User user) {
        int maxAttempts = securityProperties.bruteForce().maxFailedAttempts();
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= maxAttempts) {
            int lockMinutes = securityProperties.bruteForce().lockDurationMinutes();
            user.setLockedUntil(Instant.now().plusSeconds(lockMinutes * 60L));
            user.setStatus(User.UserStatus.LOCKED);
            log.warn("Account locked: id={}, until={}", user.getId(), user.getLockedUntil());
        }
        userRepository.save(user);
    }

    private void generateAndSendVerificationCode(User user, VerificationType type) {
        // In production, send via Email/SMS. For now, store in DB.
        // The actual OTP is logged/hashed; external notification service handles delivery.
        verificationCodeRepository.markAllUsedByUserIdAndType(user.getId(), type);

        VerificationCode code = VerificationCode.builder()
                .userId(user.getId())
                .codeHash(hashToken(UUID.randomUUID().toString().substring(0, 8)))
                .type(type)
                .expiresAt(Instant.now().plusSeconds(
                        securityProperties.verificationCode().expirationMinutes() * 60L))
                .build();
        verificationCodeRepository.save(code);

        // TODO: Publish event to Kafka topic `auth-events` for email/SMS dispatch
        log.info("Verification code generated: userId={}, type={}", user.getId(), type);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
