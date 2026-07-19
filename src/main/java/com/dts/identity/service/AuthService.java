package com.dts.identity.service;

import com.dts.identity.config.SecurityProperties;
import com.dts.identity.dto.request.*;
import com.dts.identity.dto.response.AuthResponse;
import com.dts.identity.dto.response.AuthResponse.UserInfo;
import com.dts.identity.dto.response.TokenValidationResponse;
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
import java.security.SecureRandom;
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
    private final OutboxPublisher outboxPublisher;

    private static final String INVALID_CREDENTIALS = "Invalid username or password";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ==================== LOGIN ====================

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Unified error message to prevent user enumeration
        // (không phân biệt "user not found" vs "wrong password")
        Optional<User> userOpt = userRepository.findByIdentifier(request.username());

        if (userOpt.isEmpty()) {
            // Simulate password check to prevent timing-based enumeration
            passwordEncoder.matches(request.password(),
                    "$2a$12$dummyDummyDummyDummyDummyDummyDummyDummyDummyDummy");
            throw new BusinessException(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }

        User user = userOpt.get();

        // Check brute-force lock
        if (user.isLocked()) {
            throw new BusinessException(
                    "Account locked until " + user.getLockedUntil() + ". Please try again later.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        // Check banned (same message as invalid credentials to not leak status)
        if (user.getStatus() == User.UserStatus.BANNED) {
            throw new BusinessException(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }

        // Verify password
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            incrementFailedAttempts(user);
            throw new BusinessException(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
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
        Role studentRole = roleRepository.findByName(Role.ROLE_STUDENT)
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

        if (!constantTimeEquals(hashToken(request.code()), code.getCodeHash())) {
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

    // ==================== FORGOT PASSWORD ====================

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // Always return success regardless of whether user exists (security best practice)
        Optional<User> userOpt = userRepository.findByIdentifier(request.identifier());
        if (userOpt.isEmpty()) {
            log.info("Forgot password requested for non-existent identifier: {}", request.identifier());
            return;
        }

        User user = userOpt.get();
        generateAndSendVerificationCode(user, VerificationType.RESET_PASSWORD);

        // Write to outbox for async Kafka delivery
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getId().toString());
        payload.put("email", user.getEmail());
        payload.put("username", user.getUsername());
        outboxPublisher.publish("auth-events", "password-reset-requested",
                user.getId().toString(), "USER", payload);

        log.info("Password reset OTP generated: userId={}", user.getId());
    }

    // ==================== RESET PASSWORD ====================

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByIdentifier(request.identifier())
                .orElseThrow(() -> new BusinessException("User not found"));

        // Verify OTP first
        VerificationCode code = verificationCodeRepository
                .findLatestByUserIdAndType(user.getId(), VerificationType.RESET_PASSWORD)
                .orElseThrow(() -> new BusinessException("No verification code found. Please request a new one."));

        if (!code.isValid()) {
            throw new BusinessException("Verification code has expired or already used");
        }

        if (code.getAttempts() >= securityProperties.verificationCode().maxAttempts()) {
            throw new BusinessException("Too many failed attempts. Please request a new code.");
        }

        if (!constantTimeEquals(hashToken(request.code()), code.getCodeHash())) {
            code.setAttempts(code.getAttempts() + 1);
            verificationCodeRepository.save(code);
            throw new BusinessException("Invalid verification code");
        }

        // Mark OTP used
        code.setUsedAt(Instant.now());
        verificationCodeRepository.save(code);

        // Update password and revoke all sessions
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());

        // Write to outbox for async Kafka delivery
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getId().toString());
        payload.put("username", user.getUsername());
        outboxPublisher.publish("auth-events", "password-reset-completed",
                user.getId().toString(), "USER", payload);

        log.info("Password reset completed: userId={}", user.getId());
    }

    // ==================== RESEND VERIFICATION ====================

    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        User user = userRepository.findByIdentifier(request.identifier())
                .orElseThrow(() -> new BusinessException("User not found"));

        VerificationType type;
        try {
            type = VerificationType.valueOf(request.type().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid verification type: " + request.type());
        }

        // Generate new OTP (old ones are auto-marked as used inside generateAndSendVerificationCode)
        generateAndSendVerificationCode(user, type);

        // Write to outbox for async Kafka delivery
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getId().toString());
        payload.put("email", user.getEmail());
        payload.put("username", user.getUsername());
        payload.put("verificationType", type.name());
        outboxPublisher.publish("auth-events", "verification-code-resent",
                user.getId().toString(), "USER", payload);

        log.info("Verification code resent: userId={}, type={}", user.getId(), type);
    }

    // ==================== TOKEN VALIDATION (for API Gateway) ====================

    public TokenValidationResponse validateToken(String token) {
        if (token == null || token.isBlank()) {
            return TokenValidationResponse.invalid();
        }

        try {
            Claims claims = jwtProvider.validateAccessToken(token);
            if (jwtProvider.isTokenExpired(claims)) {
                return TokenValidationResponse.invalid();
            }

            UUID userId = jwtProvider.getUserId(claims);
            String username = claims.get("username", String.class);
            List<String> roles = jwtProvider.getRoles(claims);
            List<String> permissions = jwtProvider.getPermissions(claims);

            return TokenValidationResponse.valid(userId, username, roles, permissions);
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return TokenValidationResponse.invalid();
        }
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
                .expiresAt(Instant.now().plusMillis(jwtProvider.getRefreshExpirationMs()))
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
                .filter(ur -> ur.getRole() != null)  // Bỏ qua role đã soft-delete
                .map(ur -> ur.getRole().getName())
                .collect(Collectors.toList());
    }

    private List<String> getUserPermissions(UUID userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .flatMap(ur -> rolePermissionRepository.findByRoleId(ur.getRoleId()).stream())
                .filter(rp -> rp.getPermission() != null)  // Bỏ qua permission đã soft-delete
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
        verificationCodeRepository.markAllUsedByUserIdAndType(user.getId(), type);

        // Sinh OTP 6 chữ số (0-9), dễ nhập qua SMS/email
        String otp = generateNumericOtp();
        VerificationCode code = VerificationCode.builder()
                .userId(user.getId())
                .codeHash(hashToken(otp))
                .type(type)
                .expiresAt(Instant.now().plusSeconds(
                        securityProperties.verificationCode().expirationMinutes() * 60L))
                .build();
        verificationCodeRepository.save(code);

        // Write to outbox for async Kafka delivery to Notification Service
        // (OTP được gửi kèm trong payload để Notification Service gửi SMS/email)
        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("userId", user.getId().toString());
        eventPayload.put("email", user.getEmail());
        eventPayload.put("username", user.getUsername());
        eventPayload.put("verificationType", type.name());
        eventPayload.put("otp", otp);  // Notification Service sẽ xóa sau khi gửi
        outboxPublisher.publish("auth-events", "verification-code-generated",
                user.getId().toString(), "USER", eventPayload);

        log.info("Verification code generated: userId={}, type={}", user.getId(), type);
    }

    /**
     * Sinh mã OTP 6 chữ số ngẫu nhiên (cryptographically secure).
     */
    private String generateNumericOtp() {
        int otp = 100000 + SECURE_RANDOM.nextInt(900000); // 100000 - 999999
        return String.valueOf(otp);
    }

    /**
     * So sánh 2 chuỗi hash theo constant-time để chống timing attack.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
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
