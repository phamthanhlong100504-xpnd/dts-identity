package com.dts.identity.service;

import com.dts.identity.config.SecurityProperties;
import com.dts.identity.dto.request.RefreshTokenRequest;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

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

    // ==================== REFRESH TOKEN - HAPPY CASE ====================

    @Test
    @DisplayName("refreshToken - Happy Case: Valid token, rotation works")
    void refreshToken_HappyCase_ValidToken() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        Claims claims = mock(Claims.class);
        when(jwtProvider.validateRefreshToken("valid-refresh-token")).thenReturn(claims);
        when(jwtProvider.getUserId(claims)).thenReturn(user.getId());

        RefreshToken storedToken = new RefreshToken();
        storedToken.setTokenHash("hash-of-valid-refresh-token");
        storedToken.setExpiresAt(Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUserId(user.getId())).thenReturn(List.of(userRole));
        when(jwtProvider.generateAccessToken(any(), anyString(), anyList(), anyList())).thenReturn("new-access-token");
        when(jwtProvider.generateRefreshToken(any())).thenReturn("new-refresh-token");
        when(jwtProvider.getRefreshExpirationMs()).thenReturn(604800000L);
        when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.refreshToken(request);

        assertNotNull(response);
        assertEquals("new-access-token", response.accessToken());
        assertEquals("new-refresh-token", response.refreshToken());
        // Old token should be revoked
        assertNotNull(storedToken.getRevokedAt());
        // New token should be saved
        verify(refreshTokenRepository).save(argThat(t ->
                t.getTokenHash().equals(sha256Base64("new-refresh-token")) &&
                t.getUserId().equals(user.getId())
        ));
    }

    private String sha256Base64(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    @Test
    @DisplayName("refreshToken - Invalid JWT (malformed/expired)")
    void refreshToken_InvalidJwt_ThrowsException() {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-token");
        when(jwtProvider.validateRefreshToken("invalid-token")).thenThrow(new RuntimeException("Invalid JWT"));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refreshToken(request));
        assertEquals("Invalid or expired refresh token", exception.getMessage());
        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("refreshToken - Token not found in database")
    void refreshToken_NotFoundInDb_ThrowsException() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        Claims claims = mock(Claims.class);
        when(jwtProvider.validateRefreshToken("valid-refresh-token")).thenReturn(claims);
        when(jwtProvider.getUserId(claims)).thenReturn(user.getId());
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refreshToken(request));
        assertEquals("Refresh token not recognized", exception.getMessage());
    }

    @Test
    @DisplayName("refreshToken - Token expired (expiresAt in past)")
    void refreshToken_Expired_ThrowsException() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        Claims claims = mock(Claims.class);
        when(jwtProvider.validateRefreshToken("valid-refresh-token")).thenReturn(claims);
        when(jwtProvider.getUserId(claims)).thenReturn(user.getId());

        RefreshToken storedToken = new RefreshToken();
        storedToken.setTokenHash("hash");
        storedToken.setExpiresAt(Instant.now().minusSeconds(3600)); // Expired

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refreshToken(request));
        assertEquals("Refresh token has been revoked or expired", exception.getMessage());
    }

    @Test
    @DisplayName("refreshToken - Token already revoked")
    void refreshToken_Revoked_ThrowsException() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        Claims claims = mock(Claims.class);
        when(jwtProvider.validateRefreshToken("valid-refresh-token")).thenReturn(claims);
        when(jwtProvider.getUserId(claims)).thenReturn(user.getId());

        RefreshToken storedToken = new RefreshToken();
        storedToken.setTokenHash("hash");
        storedToken.setExpiresAt(Instant.now().plusSeconds(3600));
        storedToken.setRevokedAt(Instant.now().minusSeconds(100)); // Already revoked

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refreshToken(request));
        assertEquals("Refresh token has been revoked or expired", exception.getMessage());
    }

    @Test
    @DisplayName("refreshToken - User not found (deleted)")
    void refreshToken_UserNotFound_ThrowsException() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        Claims claims = mock(Claims.class);
        when(jwtProvider.validateRefreshToken("valid-refresh-token")).thenReturn(claims);
        when(jwtProvider.getUserId(claims)).thenReturn(user.getId());

        RefreshToken storedToken = new RefreshToken();
        storedToken.setTokenHash("hash");
        storedToken.setExpiresAt(Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(userRepository.findById(user.getId())).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refreshToken(request));
        assertEquals("User not found", exception.getMessage());
    }

    @Test
    @DisplayName("refreshToken - User deleted (deletedAt set)")
    void refreshToken_UserDeleted_ThrowsException() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        Claims claims = mock(Claims.class);
        when(jwtProvider.validateRefreshToken("valid-refresh-token")).thenReturn(claims);
        when(jwtProvider.getUserId(claims)).thenReturn(user.getId());

        RefreshToken storedToken = new RefreshToken();
        storedToken.setTokenHash("hash");
        storedToken.setExpiresAt(Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        user.setDeletedAt(Instant.now());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refreshToken(request));
        assertEquals("Account has been deleted", exception.getMessage());
    }

    // ==================== REFRESH TOKEN ROTATION ====================

    @Test
    @DisplayName("refreshToken - Each refresh creates new token and revokes old (rotation)")
    void refreshToken_Rotation_RevokesOldCreatesNew() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        Claims claims = mock(Claims.class);
        when(jwtProvider.validateRefreshToken("valid-refresh-token")).thenReturn(claims);
        when(jwtProvider.getUserId(claims)).thenReturn(user.getId());

        RefreshToken storedToken = new RefreshToken();
        storedToken.setTokenHash("hash");
        storedToken.setExpiresAt(Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUserId(user.getId())).thenReturn(List.of(userRole));
        when(jwtProvider.generateAccessToken(any(), anyString(), anyList(), anyList())).thenReturn("new-access");
        when(jwtProvider.generateRefreshToken(any())).thenReturn("new-refresh");
        when(jwtProvider.getRefreshExpirationMs()).thenReturn(604800000L);
        when(jwtProvider.getAccessExpirationMs()).thenReturn(86400000L);

        authService.refreshToken(request);

        // Old token revoked
        assertNotNull(storedToken.getRevokedAt());
        // New token saved
        verify(refreshTokenRepository).save(argThat(t ->
                t.getUserId().equals(user.getId()) &&
                !t.getTokenHash().equals("hash") // Different hash
        ));
    }

    @Test
    @DisplayName("refreshToken - Multiple rapid refreshes use same old token should fail after first")
    void refreshToken_ReuseOldToken_Fails() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        Claims claims = mock(Claims.class);
        when(jwtProvider.validateRefreshToken("valid-refresh-token")).thenReturn(claims);
        when(jwtProvider.getUserId(claims)).thenReturn(user.getId());

        RefreshToken storedToken = new RefreshToken();
        storedToken.setTokenHash("hash");
        storedToken.setExpiresAt(Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUserId(user.getId())).thenReturn(List.of(userRole));
        when(jwtProvider.generateAccessToken(any(), anyString(), anyList(), anyList())).thenReturn("new-access");
        when(jwtProvider.generateRefreshToken(any())).thenReturn("new-refresh");

        // First refresh succeeds
        authService.refreshToken(request);
        assertNotNull(storedToken.getRevokedAt());

        // Second refresh with same token should fail (already revoked)
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refreshToken(request));
        assertEquals("Refresh token has been revoked or expired", exception.getMessage());
    }

    // ==================== LOGOUT REVOKES ALL TOKENS ====================

    @Test
    @DisplayName("logout - Revokes all refresh tokens for user")
    void logout_RevokesAllTokens() {
        authService.logout(user.getId());
        verify(refreshTokenRepository).revokeAllByUserId(eq(user.getId()), any(Instant.class));
    }

    // ==================== PASSWORD CHANGE REVOKES TOKENS ====================

    @Test
    @DisplayName("changePassword - Revokes all refresh tokens")
    void changePassword_RevokesAllTokens() {
        user.setPassword("encodedPassword");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("encodedNew");

        authService.changePassword(user.getId(), new com.dts.identity.dto.request.ChangePasswordRequest("old", "new"));

        verify(refreshTokenRepository).revokeAllByUserId(eq(user.getId()), any(Instant.class));
    }

    // ==================== RBAC TESTS ====================

    @Test
    @DisplayName("validateToken - Returns roles and permissions from JWT")
    void validateToken_ReturnsRolesAndPermissions() {
        Claims claims = mock(Claims.class);
        when(jwtProvider.validateAccessToken("valid-access-token")).thenReturn(claims);
        when(jwtProvider.getUserId(claims)).thenReturn(user.getId());
        when(claims.get("username", String.class)).thenReturn("testuser");
        when(jwtProvider.getRoles(claims)).thenReturn(List.of("ROLE_STUDENT", "ROLE_ADMIN"));
        when(jwtProvider.getPermissions(claims)).thenReturn(List.of("user:read", "user:write", "exam:create"));

        com.dts.identity.dto.response.TokenValidationResponse response = authService.validateToken("valid-access-token");

        assertTrue(response.valid());
        assertEquals("testuser", response.username());
        assertEquals(2, response.roles().size());
        assertEquals(3, response.permissions().size());
    }

    @Test
    @DisplayName("validateToken - Invalid/expired token returns invalid")
    void validateToken_InvalidToken_ReturnsInvalid() {
        when(jwtProvider.validateAccessToken("invalid-token")).thenThrow(new RuntimeException("Invalid"));

        com.dts.identity.dto.response.TokenValidationResponse response = authService.validateToken("invalid-token");

        assertFalse(response.valid());
    }

    @Test
    @DisplayName("validateToken - Null token returns invalid")
    void validateToken_NullToken_ReturnsInvalid() {
        com.dts.identity.dto.response.TokenValidationResponse response = authService.validateToken(null);
        assertFalse(response.valid());
    }

    @Test
    @DisplayName("validateToken - Blank token returns invalid")
    void validateToken_BlankToken_ReturnsInvalid() {
        com.dts.identity.dto.response.TokenValidationResponse response = authService.validateToken("   ");
        assertFalse(response.valid());
    }

    @Test
    @DisplayName("validateToken - Expired token returns invalid")
    void validateToken_ExpiredToken_ReturnsInvalid() {
        Claims claims = mock(Claims.class);
        when(jwtProvider.validateAccessToken("expired-token")).thenReturn(claims);
        when(jwtProvider.isTokenExpired(claims)).thenReturn(true);

        com.dts.identity.dto.response.TokenValidationResponse response = authService.validateToken("expired-token");

        assertFalse(response.valid());
    }
}