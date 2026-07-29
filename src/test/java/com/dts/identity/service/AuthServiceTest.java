package com.dts.identity.service;

import com.dts.identity.config.SecurityProperties;
import com.dts.identity.dto.request.LoginRequest;
import com.dts.identity.dto.request.RegisterRequest;
import com.dts.identity.dto.response.AuthResponse;
import com.dts.identity.entity.*;
import com.dts.identity.exception.BusinessException;
import com.dts.identity.repository.*;
import com.dts.identity.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
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

    private User testUser;
    private Role studentRole;
    private UUID userId;
    private static final String RAW_PASSWORD = "Test@1234";
    private static final String ENCODED_PASSWORD = "$2a$12$encodedPasswordHash";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        studentRole = Role.builder()
                .id(UUID.randomUUID())
                .name(Role.ROLE_STUDENT)
                .build();

        testUser = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .password(ENCODED_PASSWORD)
                .fullName("Test User")
                .birthOfDate(LocalDate.of(2000, 1, 1))
                .phoneNumber("0123456789")
                .status(User.UserStatus.ACTIVE)
                .build();

        lenient().when(securityProperties.bruteForce())
                .thenReturn(new SecurityProperties.BruteForceProperties(5, 15));
        lenient().when(securityProperties.verificationCode())
                .thenReturn(new SecurityProperties.VerificationCodeProperties(15, 3));
    }

    @Test
    @DisplayName("Register new user successfully")
    void register_Success() {
        RegisterRequest request = new RegisterRequest(
                "testuser", "test@example.com", RAW_PASSWORD,
                "Test User", LocalDate.of(2000, 1, 1), "0123456789");

        when(userRepository.existsByUsernameAndDeletedAtIsNull("testuser")).thenReturn(false);
        when(userRepository.existsByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndDeletedAtIsNull("0123456789")).thenReturn(false);
        when(roleRepository.findByName(Role.ROLE_STUDENT)).thenReturn(Optional.of(studentRole));
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userRoleRepository.save(any(UserRole.class))).thenReturn(new UserRole());
        when(verificationCodeRepository.markAllUsedByUserIdAndType(any(), any())).thenReturn(0);
        when(verificationCodeRepository.save(any(VerificationCode.class))).thenReturn(new VerificationCode());
        when(userRoleRepository.findByUserId(userId)).thenReturn(List.of());
        when(jwtProvider.generateAccessToken(any(), anyString(), anyList(), anyList()))
                .thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(any()))
                .thenReturn("refresh-token");
        when(jwtProvider.getRefreshExpirationMs()).thenReturn(604800000L);
        when(jwtProvider.getAccessExpirationMs()).thenReturn(900000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(new RefreshToken());

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900L, response.expiresIn());
        assertNotNull(response.user());
        assertEquals("testuser", response.user().username());
        assertEquals("test@example.com", response.user().email());

        verify(userRepository).save(any(User.class));
        verify(userRoleRepository).save(any(UserRole.class));
        verify(verificationCodeRepository).save(any(VerificationCode.class));
    }

    @Test
    @DisplayName("Register fails when username already exists")
    void register_UsernameAlreadyTaken() {
        RegisterRequest request = new RegisterRequest(
                "testuser", "new@example.com", RAW_PASSWORD,
                "Test User", LocalDate.of(2000, 1, 1), "0123456789");

        when(userRepository.existsByUsernameAndDeletedAtIsNull("testuser")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(request));
        assertEquals("BIZ-409", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Username already taken"));
    }

    @Test
    @DisplayName("Register fails when email already exists")
    void register_EmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest(
                "newuser", "test@example.com", RAW_PASSWORD,
                "Test User", LocalDate.of(2000, 1, 1), "0123456789");

        when(userRepository.existsByUsernameAndDeletedAtIsNull("newuser")).thenReturn(false);
        when(userRepository.existsByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(request));
        assertEquals("BIZ-409", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Email already registered"));
    }

    @Test
    @DisplayName("Register fails when phone already exists")
    void register_PhoneAlreadyExists() {
        RegisterRequest request = new RegisterRequest(
                "newuser", "new@example.com", RAW_PASSWORD,
                "Test User", LocalDate.of(2000, 1, 1), "0123456789");

        when(userRepository.existsByUsernameAndDeletedAtIsNull("newuser")).thenReturn(false);
        when(userRepository.existsByEmailAndDeletedAtIsNull("new@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndDeletedAtIsNull("0123456789")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(request));
        assertEquals("BIZ-409", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Phone number already registered"));
    }

    @Test
    @DisplayName("Login succeeds with valid credentials")
    void login_Success() {
        LoginRequest request = new LoginRequest("testuser", RAW_PASSWORD, null);

        when(userRepository.findByIdentifier("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(userRoleRepository.findByUserId(userId)).thenReturn(List.of());
        when(jwtProvider.generateAccessToken(any(), anyString(), anyList(), anyList()))
                .thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(any()))
                .thenReturn("refresh-token");
        when(jwtProvider.getRefreshExpirationMs()).thenReturn(604800000L);
        when(jwtProvider.getAccessExpirationMs()).thenReturn(900000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(new RefreshToken());

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());

        verify(userRepository).save(argThat(user ->
                user.getFailedLoginAttempts() == 0 && user.getLastLoginAt() != null));
    }

    @Test
    @DisplayName("Login fails with wrong password")
    void login_WrongPassword() {
        LoginRequest request = new LoginRequest("testuser", "WrongPass", null);

        when(userRepository.findByIdentifier("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPass", ENCODED_PASSWORD)).thenReturn(false);

        assertThrows(BusinessException.class, () -> authService.login(request));
        verify(userRepository).save(argThat(user -> user.getFailedLoginAttempts() > 0));
    }

    @Test
    @DisplayName("Login fails with non-existent user")
    void login_UserNotFound() {
        LoginRequest request = new LoginRequest("nonexistent", RAW_PASSWORD, null);

        when(userRepository.findByIdentifier("nonexistent")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(BusinessException.class, () -> authService.login(request));
    }
}
