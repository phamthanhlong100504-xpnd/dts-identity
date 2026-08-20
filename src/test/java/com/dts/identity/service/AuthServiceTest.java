package com.dts.identity.service;

import com.dts.identity.config.SecurityProperties;
import com.dts.identity.dto.request.LoginRequest;
import com.dts.identity.dto.request.RefreshTokenRequest;
import com.dts.identity.dto.request.RegisterRequest;
import com.dts.identity.dto.response.AuthResponse;
import com.dts.identity.entity.RefreshToken;
import com.dts.identity.entity.Role;
import com.dts.identity.entity.User;
import com.dts.identity.entity.UserRole;
import com.dts.identity.exception.BusinessException;
import com.dts.identity.repository.*;
import com.dts.identity.security.JwtProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private VerificationCodeRepository verificationCodeRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private SecurityProperties securityProperties;
    @Mock private OutboxPublisher outboxPublisher;
    @Mock private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    private User user;
    private Role role;
    private UserRole userRole;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .password("encodedPassword")
                .status(User.UserStatus.ACTIVE)
                .failedLoginAttempts(0)
                .build();

        role = Role.builder()
                .id(UUID.randomUUID())
                .name(Role.ROLE_STUDENT)
                .build();

        userRole = UserRole.builder()
                .userId(user.getId())
                .roleId(role.getId())
                .role(role)
                .build();
    }

    // ==================== LOGIN ====================

    @Test
    @DisplayName("login - Path 1: User Not Found")
    void login_UserNotFound() {
        LoginRequest request = new LoginRequest("unknown", "password", "device");
        when(userRepository.findByIdentifier("unknown")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> authService.login(request));
        verify(passwordEncoder).matches(eq("password"), anyString());
    }

    @Test
    @DisplayName("login - Path 2: Account Locked")
    void login_AccountLocked() {
        user.setStatus(User.UserStatus.LOCKED);
        user.setLockedUntil(Instant.now().plusSeconds(3600));
        LoginRequest request = new LoginRequest("testuser", "password", "device");
        when(userRepository.findByIdentifier("testuser")).thenReturn(Optional.of(user));

        assertThrows(BusinessException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("login - Path 3: Account Banned")
    void login_AccountBanned() {
        user.setStatus(User.UserStatus.BANNED);
        LoginRequest request = new LoginRequest("testuser", "password", "device");
        when(userRepository.findByIdentifier("testuser")).thenReturn(Optional.of(user));

        assertThrows(BusinessException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("login - Path 4: Wrong Password")
    void login_WrongPassword() {
        LoginRequest request = new LoginRequest("testuser", "wrong", "device");
        when(userRepository.findByIdentifier("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encodedPassword")).thenReturn(false);
        
        SecurityProperties.BruteForceProperties bruteForceMock = mock(SecurityProperties.BruteForceProperties.class);
        when(securityProperties.bruteForce()).thenReturn(bruteForceMock);
        when(bruteForceMock.maxFailedAttempts()).thenReturn(5);

        assertThrows(BusinessException.class, () -> authService.login(request));
        verify(userRepository).save(user); // saving failed attempts
        assertEquals(1, user.getFailedLoginAttempts());
    }

    @Test
    @DisplayName("login - Path 5: Happy Case")
    void login_HappyCase() {
        LoginRequest request = new LoginRequest("testuser", "password", "device");
        when(userRepository.findByIdentifier("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encodedPassword")).thenReturn(true);
        when(userRoleRepository.findByUserId(user.getId())).thenReturn(List.of(userRole));
        when(jwtProvider.generateAccessToken(any(), anyString(), anyList(), anyList())).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtProvider.getRefreshExpirationMs()).thenReturn(604800000L);
        when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(userRepository).save(user); // clear failed attempts
    }

    // ==================== REGISTER ====================

    @Test
    @DisplayName("register - Path 1: Username Taken")
    void register_UsernameTaken() {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "pass", "Full", LocalDate.now(), "0123");
        when(userRepository.existsByUsernameAndDeletedAtIsNull("testuser")).thenReturn(true);

        assertThrows(BusinessException.class, () -> authService.register(request));
    }

    @Test
    @DisplayName("register - Path 2: Email Taken")
    void register_EmailTaken() {
        RegisterRequest request = new RegisterRequest("newuser", "test@example.com", "pass", "Full", LocalDate.now(), "0123");
        when(userRepository.existsByUsernameAndDeletedAtIsNull("newuser")).thenReturn(false);
        when(userRepository.existsByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(true);

        assertThrows(BusinessException.class, () -> authService.register(request));
    }

    @Test
    @DisplayName("register - Path 3: Happy Case")
    void register_HappyCase() {
        RegisterRequest request = new RegisterRequest("newuser", "new@example.com", "pass", "Full", LocalDate.now(), "0123");
        when(userRepository.existsByUsernameAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(roleRepository.findByName(Role.ROLE_STUDENT)).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("pass")).thenReturn("encoded");
        
        User savedUser = User.builder().id(UUID.randomUUID()).username("newuser").build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        SecurityProperties.VerificationCodeProperties verifyMock = mock(SecurityProperties.VerificationCodeProperties.class);
        when(securityProperties.verificationCode()).thenReturn(verifyMock);
        when(verifyMock.expirationMinutes()).thenReturn(15);
        
        when(jwtProvider.generateAccessToken(any(), any(), anyList(), anyList())).thenReturn("access");
        when(jwtProvider.generateRefreshToken(any())).thenReturn("refresh");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        verify(userRoleRepository).save(any(UserRole.class));
        verify(verificationCodeRepository).save(any());
        verify(outboxPublisher, atLeastOnce()).publish(anyString(), anyString(), anyString(), anyString(), anyMap());
    }

    // ==================== LOGOUT ====================

    @Test
    @DisplayName("logout - Happy Case")
    void logout_HappyCase() {
        authService.logout(user.getId());
        verify(refreshTokenRepository).revokeAllByUserId(eq(user.getId()), any(Instant.class));
    }
    // ==================== REFRESH TOKEN ====================
    @Test
    @DisplayName("refreshToken - Happy Case")
    void refreshToken_HappyCase() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        Claims claims = mock(Claims.class);
        when(jwtProvider.validateRefreshToken("valid-refresh-token")).thenReturn(claims);
        when(jwtProvider.getUserId(claims)).thenReturn(user.getId());

        RefreshToken storedToken = new RefreshToken();
        storedToken.setTokenHash("hash");
        storedToken.setExpiresAt(Instant.now().plusSeconds(3600));

        // Mock hashToken internals (we can just let it hash naturally and mock findByTokenHash)
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        when(userRoleRepository.findByUserId(user.getId())).thenReturn(List.of(userRole));
        when(jwtProvider.generateAccessToken(any(), anyString(), anyList(), anyList())).thenReturn("new-access");
        when(jwtProvider.generateRefreshToken(any())).thenReturn("new-refresh");

        AuthResponse response = authService.refreshToken(request);

        assertNotNull(response);
        assertEquals("new-access", response.accessToken());
        assertNotNull(storedToken.getRevokedAt()); // old token revoked
    }

    // ==================== FORGOT PASSWORD ====================
    @Test
    @DisplayName("forgotPassword - Happy Case")
    void forgotPassword_HappyCase() {
        com.dts.identity.dto.request.ForgotPasswordRequest request = new com.dts.identity.dto.request.ForgotPasswordRequest("test@example.com");
        when(userRepository.findByIdentifier("test@example.com")).thenReturn(Optional.of(user));
        
        SecurityProperties.VerificationCodeProperties verifyMock = mock(SecurityProperties.VerificationCodeProperties.class);
        when(securityProperties.verificationCode()).thenReturn(verifyMock);
        when(verifyMock.expirationMinutes()).thenReturn(15);

        authService.forgotPassword(request);

        verify(verificationCodeRepository).save(any());
        verify(outboxPublisher, atLeastOnce()).publish(anyString(), anyString(), anyString(), anyString(), anyMap());
    }

    // ==================== VALIDATE TOKEN ====================
    @Test
    @DisplayName("validateToken - Happy Case")
    void validateToken_HappyCase() {
        Claims claims = mock(Claims.class);
        when(jwtProvider.validateAccessToken("token")).thenReturn(claims);
        when(jwtProvider.getUserId(claims)).thenReturn(user.getId());
        when(claims.get("username", String.class)).thenReturn("testuser");
        when(jwtProvider.getRoles(claims)).thenReturn(List.of("ROLE_STUDENT"));
        when(jwtProvider.getPermissions(claims)).thenReturn(List.of());

        com.dts.identity.dto.response.TokenValidationResponse response = authService.validateToken("token");

        assertTrue(response.valid());
        assertEquals("testuser", response.username());
        assertEquals("ROLE_STUDENT", response.roles().get(0));
    }
}

