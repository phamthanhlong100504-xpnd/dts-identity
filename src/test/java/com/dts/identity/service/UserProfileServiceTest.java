package com.dts.identity.service;

import com.dts.identity.dto.request.UpdateMyProfileRequest;
import com.dts.identity.dto.response.UserResponse;
import com.dts.identity.entity.User;
import com.dts.identity.exception.BusinessException;
import com.dts.identity.repository.PermissionRepository;
import com.dts.identity.repository.RefreshTokenRepository;
import com.dts.identity.repository.RolePermissionRepository;
import com.dts.identity.repository.RoleRepository;
import com.dts.identity.repository.UserRepository;
import com.dts.identity.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OutboxPublisher outboxPublisher;

    @InjectMocks
    private UserService userService;

    @Test
    void changingEmailInvalidatesOldVerificationAndPreservesOtherFields() {
        UUID userId = UUID.randomUUID();
        Instant verifiedAt = Instant.parse("2026-01-01T00:00:00Z");
        LocalDate birthday = LocalDate.of(2000, 1, 1);

        User user = User.builder()
                .id(userId)
                .username("student")
                .email("old@example.test")
                .fullName("Old Name")
                .birthOfDate(birthday)
                .phoneNumber("0123456789")
                .selectedLicenseClass("A1")
                .status(User.UserStatus.ACTIVE)
                .emailVerifiedAt(verifiedAt)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMyProfileRequest request = new UpdateMyProfileRequest(
                null,
                "new@example.test",
                " New Name ",
                null,
                null
        );

        UserResponse response = userService.updateMyProfile(userId, request);

        assertEquals("new@example.test", response.email());
        assertEquals("New Name", response.fullName());
        assertNull(response.emailVerifiedAt());

        assertEquals("student", response.username());
        assertEquals(birthday, response.birthOfDate());
        assertEquals("0123456789", response.phoneNumber());
        assertEquals("A1", response.selectedLicenseClass());
        assertEquals(User.UserStatus.ACTIVE, response.status());
    }

    @Test
    void unchangedEmailKeepsVerification() {
        UUID userId = UUID.randomUUID();
        Instant verifiedAt = Instant.parse("2026-01-01T00:00:00Z");

        User user = User.builder()
                .id(userId)
                .username("student")
                .email("student@example.test")
                .fullName("Old Name")
                .selectedLicenseClass("B1")
                .status(User.UserStatus.ACTIVE)
                .emailVerifiedAt(verifiedAt)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMyProfileRequest request = new UpdateMyProfileRequest(
                null,
                "student@example.test",
                "New Name",
                null,
                null
        );

        UserResponse response = userService.updateMyProfile(userId, request);

        assertEquals(verifiedAt, response.emailVerifiedAt());
        assertEquals("B1", response.selectedLicenseClass());
        assertEquals("New Name", response.fullName());
    }

    @Test
    void duplicateEmailDoesNotWriteProfile() {
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("student")
                .email("old@example.test")
                .fullName("Original Name")
                .selectedLicenseClass("A1")
                .status(User.UserStatus.ACTIVE)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(
                "taken@example.test"
        )).thenReturn(true);

        UpdateMyProfileRequest request = new UpdateMyProfileRequest(
                null,
                "taken@example.test",
                "Changed Name",
                null,
                null
        );

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> userService.updateMyProfile(userId, request)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("BIZ-409", error.getErrorCode());
        assertEquals("old@example.test", user.getEmail());
        assertEquals("Original Name", user.getFullName());

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(outboxPublisher);
    }
}