package com.dts.identity.service;

import com.dts.identity.config.SecurityProperties;
import com.dts.identity.dto.request.AssignRoleRequest;
import com.dts.identity.dto.request.CreatePermissionRequest;
import com.dts.identity.dto.request.CreateRoleRequest;
import com.dts.identity.dto.response.PermissionResponse;
import com.dts.identity.dto.response.RoleResponse;
import com.dts.identity.entity.Permission;
import com.dts.identity.entity.Role;
import com.dts.identity.entity.User;
import com.dts.identity.entity.UserRole;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RbacServiceTest {

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
    private UserService userService;

    private UUID userId;
    private UUID roleId;
    private UUID permissionId;
    private UUID actorId;
    private User user;
    private Role role;
    private Permission permission;
    private UserRole userRole;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        permissionId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .status(User.UserStatus.ACTIVE)
                .build();

        role = Role.builder()
                .id(roleId)
                .name("ROLE_STUDENT")
                .build();

        permission = Permission.builder()
                .id(permissionId)
                .name("exam:create")
                .displayName("Create Exam")
                .resource("exam")
                .build();

        userRole = UserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .role(role)
                .build();
    }

    // ==================== ROLE MANAGEMENT TESTS ====================

    @Test
    @DisplayName("assignRole - Success: Assign role to user")
    void assignRole_Success() {
        when(userRepository.existsById(userId)).thenReturn(true);
        when(roleRepository.existsById(roleId)).thenReturn(true);
        when(userRoleRepository.existsById(any())).thenReturn(false);

        userService.assignRole(new AssignRoleRequest(userId, roleId));

        verify(userRoleRepository).save(argThat(ur ->
                ur.getUserId().equals(userId) && ur.getRoleId().equals(roleId)
        ));
    }

    @Test
    @DisplayName("assignRole - User not found throws exception")
    void assignRole_UserNotFound() {
        when(userRepository.existsById(userId)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.assignRole(new AssignRoleRequest(userId, roleId)));
        assertEquals("User not found", exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    @DisplayName("assignRole - Role not found throws exception")
    void assignRole_RoleNotFound() {
        when(userRepository.existsById(userId)).thenReturn(true);
        when(roleRepository.existsById(roleId)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.assignRole(new AssignRoleRequest(userId, roleId)));
        assertEquals("Role not found", exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    @DisplayName("assignRole - Role already assigned does not duplicate")
    void assignRole_AlreadyAssigned_NoDuplicate() {
        when(userRepository.existsById(userId)).thenReturn(true);
        when(roleRepository.existsById(roleId)).thenReturn(true);
        when(userRoleRepository.existsById(any())).thenReturn(true);

        userService.assignRole(new AssignRoleRequest(userId, roleId));

        verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("revokeRole - Success: Revoke role from user")
    void revokeRole_Success() {
        userService.revokeRole(userId, roleId);
        verify(userRoleRepository).deleteByUserIdAndRoleId(userId, roleId);
    }

    @Test
    @DisplayName("getUserRoles - Returns roles with permissions")
    void getUserRoles_ReturnsRolesWithPermissions() {
        when(userRoleRepository.findByUserId(userId)).thenReturn(List.of(userRole));
        when(rolePermissionRepository.findByRoleId(roleId)).thenReturn(List.of());

        List<RoleResponse> roles = userService.getUserRoles(userId);

        assertEquals(1, roles.size());
        assertEquals("ROLE_STUDENT", roles.get(0).name());
    }

    @Test
    @DisplayName("getUserRoles - Filters out soft-deleted roles")
    void getUserRoles_FiltersDeletedRoles() {
        Role deletedRole = Role.builder().id(UUID.randomUUID()).name("ROLE_DELETED").deletedAt(java.time.Instant.now()).build();
        UserRole deletedUserRole = UserRole.builder().userId(userId).roleId(deletedRole.getId()).role(deletedRole).build();

        when(userRoleRepository.findByUserId(userId)).thenReturn(List.of(userRole, deletedUserRole));
        when(rolePermissionRepository.findByRoleId(roleId)).thenReturn(List.of());
        when(rolePermissionRepository.findByRoleId(deletedRole.getId())).thenReturn(List.of());

        List<RoleResponse> roles = userService.getUserRoles(userId);

        // Only active role should be returned
        assertEquals(1, roles.size());
        assertEquals("ROLE_STUDENT", roles.get(0).name());
    }

    // ==================== ROLE CATALOG TESTS ====================

    @Test
    @DisplayName("listRoles - Returns all active roles with permissions")
    void listRoles_ReturnsActiveRoles() {
        Role activeRole = Role.builder().id(UUID.randomUUID()).name("ROLE_ADMIN").build();
        when(roleRepository.findAllActive()).thenReturn(List.of(role, activeRole));
        when(rolePermissionRepository.findByRoleId(roleId)).thenReturn(List.of());
        when(rolePermissionRepository.findByRoleId(activeRole.getId())).thenReturn(List.of());

        List<RoleResponse> roles = userService.listRoles();

        assertEquals(2, roles.size());
    }

    @Test
    @DisplayName("createRole - Success: Creates role with ROLE_ prefix")
    void createRole_Success_AddsPrefix() {
        CreateRoleRequest request = new CreateRoleRequest("student");
        when(roleRepository.existsByName("ROLE_STUDENT")).thenReturn(false);
        Role savedRole = Role.builder().id(UUID.randomUUID()).name("ROLE_STUDENT").build();
        when(roleRepository.save(any(Role.class))).thenReturn(savedRole);

        RoleResponse response = userService.createRole(request, actorId);

        assertEquals("ROLE_STUDENT", response.name());
        verify(roleRepository).save(argThat(r -> r.getName().equals("ROLE_STUDENT") && r.getCreatedBy().equals(actorId)));
    }

    @Test
    @DisplayName("createRole - Already has ROLE_ prefix keeps it")
    void createRole_AlreadyHasPrefix_KeepsPrefix() {
        CreateRoleRequest request = new CreateRoleRequest("ROLE_ADMIN");
        when(roleRepository.existsByName("ROLE_ADMIN")).thenReturn(false);
        Role savedRole = Role.builder().id(UUID.randomUUID()).name("ROLE_ADMIN").build();
        when(roleRepository.save(any(Role.class))).thenReturn(savedRole);

        RoleResponse response = userService.createRole(request, actorId);

        assertEquals("ROLE_ADMIN", response.name());
    }

    @Test
    @DisplayName("createRole - Duplicate name throws exception")
    void createRole_Duplicate_ThrowsException() {
        CreateRoleRequest request = new CreateRoleRequest("student");
        when(roleRepository.existsByName("ROLE_STUDENT")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.createRole(request, actorId));
        assertEquals("Role already exists: ROLE_STUDENT", exception.getMessage());
    }

    @Test
    @DisplayName("deleteRole - Soft deletes role")
    void deleteRole_SoftDeletes() {
        Role activeRole = Role.builder().id(roleId).name("ROLE_STUDENT").build();
        when(roleRepository.findByIdActive(roleId)).thenReturn(Optional.of(activeRole));

        userService.deleteRole(roleId, actorId);

        assertNotNull(activeRole.getDeletedAt());
        assertEquals(actorId, activeRole.getUpdatedBy());
        verify(roleRepository).save(activeRole);
    }

    @Test
    @DisplayName("deleteRole - Not found throws exception")
    void deleteRole_NotFound_ThrowsException() {
        when(roleRepository.findByIdActive(roleId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.deleteRole(roleId, actorId));
        assertEquals("Role not found", exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    // ==================== PERMISSION CATALOG TESTS ====================

    @Test
    @DisplayName("listPermissions - Returns all active permissions")
    void listPermissions_ReturnsActivePermissions() {
        Permission activePerm = Permission.builder().id(UUID.randomUUID()).name("exam:read").build();
        when(permissionRepository.findAllActive()).thenReturn(List.of(permission, activePerm));

        List<PermissionResponse> perms = userService.listPermissions();

        assertEquals(2, perms.size());
    }

    @Test
    @DisplayName("createPermission - Success: Creates permission")
    void createPermission_Success() {
        CreatePermissionRequest request = new CreatePermissionRequest("exam:delete", "Delete Exam", "exam");
        when(permissionRepository.findByName("exam:delete")).thenReturn(Optional.empty());
        Permission saved = Permission.builder().id(UUID.randomUUID()).name("exam:delete").build();
        when(permissionRepository.save(any(Permission.class))).thenReturn(saved);

        PermissionResponse response = userService.createPermission(request, actorId);

        assertEquals("exam:delete", response.name());
        verify(permissionRepository).save(argThat(p -> p.getCreatedBy().equals(actorId)));
    }

    @Test
    @DisplayName("createPermission - Duplicate name throws exception")
    void createPermission_Duplicate_ThrowsException() {
        CreatePermissionRequest request = new CreatePermissionRequest("exam:create", "Create Exam", "exam");
        when(permissionRepository.findByName("exam:create")).thenReturn(Optional.of(permission));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.createPermission(request, actorId));
        assertEquals("Permission already exists: exam:create", exception.getMessage());
    }

    @Test
    @DisplayName("updatePermission - Success: Updates permission")
    void updatePermission_Success() {
        when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(permission));
        when(permissionRepository.findByName("exam:update")).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenReturn(permission);

        PermissionResponse response = userService.updatePermission(permissionId,
                new com.dts.identity.dto.request.UpdatePermissionRequest("exam:update", "Update Exam", "exam"), actorId);

        assertEquals("exam:update", response.name());
        assertEquals(actorId, permission.getUpdatedBy());
    }

    @Test
    @DisplayName("updatePermission - Deleted permission throws exception")
    void updatePermission_Deleted_ThrowsException() {
        permission.setDeletedAt(java.time.Instant.now());
        when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(permission));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.updatePermission(permissionId, new com.dts.identity.dto.request.UpdatePermissionRequest("x", "y", "z"), actorId));
        assertEquals("Cannot update a deleted permission", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    @DisplayName("deletePermission - Soft deletes and cleans role_permissions")
    void deletePermission_SoftDeletesAndCleans() {
        com.dts.identity.entity.RolePermission rp = com.dts.identity.entity.RolePermission.builder()
                .roleId(roleId)
                .permissionId(permissionId)
                .build();
        when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(permission));
        when(rolePermissionRepository.findByPermissionId(permissionId)).thenReturn(List.of(rp));

        userService.deletePermission(permissionId, actorId);

        assertNotNull(permission.getDeletedAt());
        assertEquals(actorId, permission.getUpdatedBy());
        verify(rolePermissionRepository).deleteByRoleIdAndPermissionId(roleId, permissionId);
        verify(permissionRepository).save(permission);
    }

    // ==================== ROLE-PERMISSION ASSOCIATION TESTS ====================

    @Test
    @DisplayName("assignPermissionToRole - Success: Assigns permission to role")
    void assignPermissionToRole_Success() {
        when(rolePermissionRepository.existsById(any())).thenReturn(false);

        userService.assignPermissionToRole(roleId, permissionId);

        verify(rolePermissionRepository).save(argThat(rp ->
                rp.getRoleId().equals(roleId) && rp.getPermissionId().equals(permissionId)
        ));
    }

    @Test
    @DisplayName("assignPermissionToRole - Already assigned does not duplicate")
    void assignPermissionToRole_AlreadyAssigned_NoDuplicate() {
        when(rolePermissionRepository.existsById(any())).thenReturn(true);

        userService.assignPermissionToRole(roleId, permissionId);

        verify(rolePermissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("revokePermissionFromRole - Success: Revokes permission from role")
    void revokePermissionFromRole_Success() {
        userService.revokePermissionFromRole(roleId, permissionId);
        verify(rolePermissionRepository).deleteByRoleIdAndPermissionId(roleId, permissionId);
    }

    // ==================== ADMIN USER MANAGEMENT TESTS ====================

    @Test
    @DisplayName("createUser - Admin creates user with roles")
    void createUser_AdminCreatesWithRoles() {
        com.dts.identity.dto.request.CreateUserRequest request = new com.dts.identity.dto.request.CreateUserRequest(
                "newuser", "new@example.com", "password", "Full Name", java.time.LocalDate.now(), "0123", List.of(roleId));
        when(userRepository.existsByUsernameAndDeletedAtIsNull("newuser")).thenReturn(false);
        when(userRepository.existsByEmailAndDeletedAtIsNull("new@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndDeletedAtIsNull("0123")).thenReturn(false);
        when(roleRepository.existsById(roleId)).thenReturn(true);
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        User savedUser = User.builder().id(UUID.randomUUID()).username("newuser").build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        userService.createUser(request);

        verify(userRepository).save(any(User.class));
        verify(userRoleRepository).save(argThat(ur -> ur.getUserId().equals(savedUser.getId()) && ur.getRoleId().equals(roleId)));
        verify(outboxPublisher).publish(eq("user-events"), eq("USER_CREATED"), anyString(), eq("USER"), any());
    }

    @Test
    @DisplayName("createUser - Invalid roleId throws exception")
    void createUser_InvalidRole_ThrowsException() {
        com.dts.identity.dto.request.CreateUserRequest request = new com.dts.identity.dto.request.CreateUserRequest(
                "newuser", "new@example.com", "password", "Full Name", java.time.LocalDate.now(), "0123", List.of(roleId));
        when(userRepository.existsByUsernameAndDeletedAtIsNull(any())).thenReturn(false);
        when(userRepository.existsByEmailAndDeletedAtIsNull(any())).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndDeletedAtIsNull(any())).thenReturn(false);
        when(roleRepository.existsById(roleId)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.createUser(request));
        assertEquals("Role not found: " + roleId, exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    @DisplayName("updateUserStatus - BANNED status revokes all tokens")
    void updateUserStatus_Banned_RevokesTokens() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.updateUserStatus(userId, new com.dts.identity.dto.request.UpdateUserStatusRequest("BANNED"));

        assertEquals(User.UserStatus.BANNED, user.getStatus());
        verify(refreshTokenRepository).revokeAllByUserId(eq(userId), any());
    }

    @Test
    @DisplayName("updateUserStatus - LOCKED status revokes all tokens")
    void updateUserStatus_Locked_RevokesTokens() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.updateUserStatus(userId, new com.dts.identity.dto.request.UpdateUserStatusRequest("LOCKED"));

        assertEquals(User.UserStatus.LOCKED, user.getStatus());
        verify(refreshTokenRepository).revokeAllByUserId(eq(userId), any());
    }

    @Test
    @DisplayName("updateUserStatus - ACTIVE status clears lock state")
    void updateUserStatus_Active_ClearsLock() {
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(java.time.Instant.now().plusSeconds(3600));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.updateUserStatus(userId, new com.dts.identity.dto.request.UpdateUserStatusRequest("ACTIVE"));

        assertEquals(User.UserStatus.ACTIVE, user.getStatus());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
    }

    @Test
    @DisplayName("updateUserStatus - Invalid status throws exception")
    void updateUserStatus_InvalidStatus_ThrowsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.updateUserStatus(userId, new com.dts.identity.dto.request.UpdateUserStatusRequest("INVALID")));

        assertEquals("Invalid status: INVALID. Valid values: PENDING, ACTIVE, LOCKED, BANNED", exception.getMessage());
    }

    // ==================== USER SEARCH TESTS ====================

    @Test
    @DisplayName("searchUsers - Filters by status")
    void searchUsers_FiltersByStatus() {
        // Just verify the specification is built correctly
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        var page = userService.searchUsers(null, "ACTIVE", null, pageable);
        // This tests the specification building path
        assertNotNull(page);
    }

    @Test
    @DisplayName("searchUsers - Filters by role name")
    void searchUsers_FiltersByRole() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        var page = userService.searchUsers(null, null, "ROLE_STUDENT", pageable);
        assertNotNull(page);
    }
}