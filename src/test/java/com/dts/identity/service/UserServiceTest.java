package com.dts.identity.service;

import com.dts.identity.dto.request.AdminResetPasswordRequest;
import com.dts.identity.dto.request.UpdateUserRequest;
import com.dts.identity.dto.response.UserResponse;
import com.dts.identity.entity.User;
import com.dts.identity.exception.BusinessException;
import com.dts.identity.repository.UserRepository;
import com.dts.identity.repository.RefreshTokenRepository;
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
        when(userRepository.findAll(pageable)).thenReturn(userPage);

        Page<UserResponse> result = userService.listUsers(pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("testuser", result.getContent().get(0).username());
    }

    @Test
    @DisplayName("getUser - Not Found")
    void getUser_NotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> userService.getUser(userId));
    }

    @Test
    @DisplayName("getUser - Happy Case")
    void getUser_HappyCase() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        UserResponse response = userService.getUser(userId);
        assertNotNull(response);
        assertEquals("testuser", response.username());
    }

    @Test
    @DisplayName("updateUser - Not Found")
    void updateUser_NotFound() {
        UpdateUserRequest request = new UpdateUserRequest(null, "new@email.com", "New Name", null, "0123", null);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> userService.updateUser(userId, request));
    }

    @Test
    @DisplayName("updateUser - Happy Case")
    void updateUser_HappyCase() {
        UpdateUserRequest request = new UpdateUserRequest(null, "new@email.com", "New Name", null, "0123", null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserResponse response = userService.updateUser(userId, request);

        assertNotNull(response);
        assertEquals("new@email.com", user.getEmail());
        assertEquals("New Name", user.getFullName());
    }

    @Test
    @DisplayName("resetPassword - Happy Case")
    void resetPassword_HappyCase() {
        AdminResetPasswordRequest request = new AdminResetPasswordRequest("newPass");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPass")).thenReturn("encoded");

        userService.resetPassword(userId, request);

        assertEquals("encoded", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("deleteUser - Happy Case")
    void deleteUser_HappyCase() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        userService.deleteUser(userId);

        assertNotNull(user.getDeletedAt());
        verify(userRepository).save(user);
    }
    // ==================== CREATE USER ====================
    @Test
    @DisplayName("createUser - Username Taken")
    void createUser_UsernameTaken() {
        com.dts.identity.dto.request.CreateUserRequest request = new com.dts.identity.dto.request.CreateUserRequest("testuser", "test@new.com", "pass", "Full", null, null, null);
        when(userRepository.existsByUsernameAndDeletedAtIsNull("testuser")).thenReturn(true);

        assertThrows(BusinessException.class, () -> userService.createUser(request));
    }

    @Test
    @DisplayName("createUser - Happy Case")
    void createUser_HappyCase() {
        com.dts.identity.dto.request.CreateUserRequest request = new com.dts.identity.dto.request.CreateUserRequest("newuser", "test@new.com", "pass", "Full", null, null, null);
        when(userRepository.existsByUsernameAndDeletedAtIsNull("newuser")).thenReturn(false);
        when(userRepository.existsByEmailAndDeletedAtIsNull("test@new.com")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserResponse response = userService.createUser(request);
        assertNotNull(response);
    }
}


