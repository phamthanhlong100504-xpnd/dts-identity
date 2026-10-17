package com.dts.identity.service;

import com.dts.identity.dto.request.AdminResetPasswordRequest;
import com.dts.identity.dto.request.UpdateLicenseRequest;
import com.dts.identity.dto.request.UpdateUserRequest;
import com.dts.identity.dto.response.UserResponse;
import com.dts.identity.entity.User;
import com.dts.identity.exception.BusinessException;
import com.dts.identity.repository.RefreshTokenRepository;
import com.dts.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OutboxPublisher outboxPublisher;

    @InjectMocks
    private UserService userService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .fullName("Test User")
                .status(User.UserStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("listUsers - Happy Case")
    void listUsers_HappyCase() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> userPage = new PageImpl<>(List.of(user));
        when(userRepository.findAll(pageable))
                .thenReturn(userPage);

        Page<UserResponse> result = userService.listUsers(pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(
                "testuser",
                result.getContent().get(0).username()
        );
    }

    @Test
    @DisplayName("getUser - Not Found")
    void getUser_NotFound() {
        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> userService.getUser(userId)
        );
    }

    @Test
    @DisplayName("getUser - Happy Case")
    void getUser_HappyCase() {
        user.setSelectedLicenseClass("B1");
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        UserResponse response = userService.getUser(userId);

        assertNotNull(response);
        assertEquals("testuser", response.username());
        assertEquals("B1", response.selectedLicenseClass());
    }

    @Test
    void updateSelectedLicenseClass_IsPerUserAndIdempotent() {
        UUID otherUserId = UUID.randomUUID();
        User otherUser = User.builder()
                .id(otherUserId)
                .selectedLicenseClass("B1")
                .build();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(userRepository.findById(otherUserId))
                .thenReturn(Optional.of(otherUser));
        when(userRepository.save(user))
                .thenReturn(user);

        var first = userService.updateSelectedLicenseClass(
                userId,
                new UpdateLicenseRequest("A1")
        );
        var switched = userService.updateSelectedLicenseClass(
                userId,
                new UpdateLicenseRequest("C1")
        );
        var repeated = userService.updateSelectedLicenseClass(
                userId,
                new UpdateLicenseRequest("C1")
        );

        assertEquals("A1", first.licenseClass());
        assertEquals("C1", switched.licenseClass());
        assertEquals("C1", repeated.licenseClass());
        assertEquals("C1", user.getSelectedLicenseClass());
        assertEquals(
                "B1",
                userService.getUser(otherUserId).selectedLicenseClass()
        );
        verify(userRepository, times(3)).save(user);
        verify(userRepository, never()).save(otherUser);
    }

    @Test
    void updateSelectedLicenseClass_RejectsUnknownValue() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> userService.updateSelectedLicenseClass(
                        userId,
                        new UpdateLicenseRequest("ABC")
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateSelectedLicenseClass_RejectsNullValue() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> userService.updateSelectedLicenseClass(
                        userId,
                        new UpdateLicenseRequest(null)
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("updateUser - Not Found")
    void updateUser_NotFound() {
        UpdateUserRequest request = new UpdateUserRequest(
                null,
                "new@email.com",
                "New Name",
                null,
                "0123",
                null
        );
        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> userService.updateUser(userId, request)
        );
    }

    @Test
    @DisplayName("updateUser - Happy Case")
    void updateUser_HappyCase() {
        UpdateUserRequest request = new UpdateUserRequest(
                null,
                "new@email.com",
                "New Name",
                null,
                "0123",
                null
        );
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        UserResponse response = userService.updateUser(userId, request);

        assertNotNull(response);
        assertEquals("new@email.com", user.getEmail());
        assertEquals("New Name", user.getFullName());
    }

    @Test
    @DisplayName("resetPassword - Happy Case")
    void resetPassword_HappyCase() {
        AdminResetPasswordRequest request =
                new AdminResetPasswordRequest("newPass");
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPass"))
                .thenReturn("encoded");

        userService.resetPassword(userId, request);

        assertEquals("encoded", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("deleteUser - Happy Case")
    void deleteUser_HappyCase() {
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        userService.deleteUser(userId);

        assertNotNull(user.getDeletedAt());
        verify(userRepository).save(user);
    }

    // ==================== CREATE USER ====================

    @Test
    @DisplayName("createUser - Username Taken")
    void createUser_UsernameTaken() {
        com.dts.identity.dto.request.CreateUserRequest request =
                new com.dts.identity.dto.request.CreateUserRequest(
                        "testuser",
                        "test@new.com",
                        "pass",
                        "Full",
                        null,
                        null,
                        null
                );
        when(userRepository.existsByUsernameAndDeletedAtIsNull("testuser"))
                .thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> userService.createUser(request)
        );
    }

    @Test
    @DisplayName("createUser - Happy Case")
    void createUser_HappyCase() {
        com.dts.identity.dto.request.CreateUserRequest request =
                new com.dts.identity.dto.request.CreateUserRequest(
                        "newuser",
                        "test@new.com",
                        "pass",
                        "Full",
                        null,
                        null,
                        null
                );
        when(userRepository.existsByUsernameAndDeletedAtIsNull("newuser"))
                .thenReturn(false);
        when(userRepository.existsByEmailAndDeletedAtIsNull("test@new.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("pass"))
                .thenReturn("encoded");
        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
    }
}