package com.dts.identity.service;

import com.dts.identity.dto.request.*;
import com.dts.identity.dto.response.PermissionResponse;
import com.dts.identity.dto.response.RoleResponse;
import com.dts.identity.dto.response.UserResponse;
import com.dts.identity.entity.*;
import com.dts.identity.exception.BusinessException;
import com.dts.identity.repository.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    // ==================== USER CRUD ====================

    public Page<UserResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(UserResponse::from);
    }

    @Cacheable(value = "users", key = "#id")
    public UserResponse getUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));
        return UserResponse.from(user);
    }

    @Transactional
    @CacheEvict(value = "users", key = "#id")
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));

        if (request.username() != null) {
            if (!request.username().equals(user.getUsername())
                    && userRepository.existsByUsernameAndDeletedAtIsNull(request.username())) {
                throw new BusinessException("Username already taken");
            }
            user.setUsername(request.username());
        }
        if (request.email() != null) {
            if (!request.email().equals(user.getEmail())
                    && userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
                throw new BusinessException("Email already registered");
            }
            user.setEmail(request.email());
        }
        if (request.fullName() != null) user.setFullName(request.fullName());
        if (request.birthOfDate() != null) user.setBirthOfDate(request.birthOfDate());
        if (request.phoneNumber() != null) user.setPhoneNumber(request.phoneNumber());
        if (request.status() != null) {
            try {
                user.setStatus(User.UserStatus.valueOf(request.status().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid status: " + request.status());
            }
        }

        User saved = userRepository.save(user);
        log.info("User updated: id={}", id);
        return UserResponse.from(saved);
    }

    @Transactional
    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));

        // Soft delete
        user.setDeletedAt(Instant.now());
        refreshTokenRepository.revokeAllByUserId(id, Instant.now());
        userRepository.save(user);
        log.info("User soft-deleted: id={}", id);
    }

    // ==================== ROLE MANAGEMENT ====================

    @Transactional
    public void assignRole(AssignRoleRequest request) {
        if (!userRepository.existsById(request.userId())) {
            throw new BusinessException("User not found", HttpStatus.NOT_FOUND);
        }
        if (!roleRepository.existsById(request.roleId())) {
            throw new BusinessException("Role not found", HttpStatus.NOT_FOUND);
        }

        UserRole.UserRoleId id = new UserRole.UserRoleId(request.userId(), request.roleId());
        if (!userRoleRepository.existsById(id)) {
            userRoleRepository.save(UserRole.builder()
                    .userId(request.userId())
                    .roleId(request.roleId())
                    .build());
            log.info("Role assigned: userId={}, roleId={}", request.userId(), request.roleId());
        }
    }

    @Transactional
    public void revokeRole(UUID userId, UUID roleId) {
        userRoleRepository.deleteByUserIdAndRoleId(userId, roleId);
        log.info("Role revoked: userId={}, roleId={}", userId, roleId);
    }

    public List<RoleResponse> getUserRoles(UUID userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .filter(ur -> ur.getRole() != null)  // Bỏ qua role đã bị soft-delete
                .map(ur -> {
                    Role role = ur.getRole();
                    List<String> permissions = rolePermissionRepository.findByRoleId(role.getId()).stream()
                            .filter(rp -> rp.getPermission() != null)
                            .map(rp -> rp.getPermission().getName())
                            .collect(Collectors.toList());
                    return new RoleResponse(role.getId(), role.getName(), permissions);
                })
                .collect(Collectors.toList());
    }

    // ==================== ROLE & PERMISSION CATALOG ====================

    public List<RoleResponse> listRoles() {
        return roleRepository.findAllActive().stream()
                .map(role -> {
                    List<String> permissions = rolePermissionRepository.findByRoleId(role.getId()).stream()
                            .filter(rp -> rp.getPermission() != null)
                            .map(rp -> rp.getPermission().getName())
                            .collect(Collectors.toList());
                    return new RoleResponse(role.getId(), role.getName(), permissions);
                })
                .collect(Collectors.toList());
    }

    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAllActive().stream()
                .map(p -> new PermissionResponse(p.getId(), p.getName(), p.getDisplayName(), p.getResource()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void assignPermissionToRole(UUID roleId, UUID permissionId) {
        RolePermission.RolePermissionId id = new RolePermission.RolePermissionId(roleId, permissionId);
        if (!rolePermissionRepository.existsById(id)) {
            rolePermissionRepository.save(RolePermission.builder()
                    .roleId(roleId)
                    .permissionId(permissionId)
                    .build());
            log.info("Permission assigned to role: roleId={}, permissionId={}", roleId, permissionId);
        }
    }

    @Transactional
    public void revokePermissionFromRole(UUID roleId, UUID permissionId) {
        rolePermissionRepository.deleteByRoleIdAndPermissionId(roleId, permissionId);
        log.info("Permission revoked from role: roleId={}, permissionId={}", roleId, permissionId);
    }

    // ==================== ADMIN: CREATE USER ====================

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsernameAndDeletedAtIsNull(request.username())) {
            throw new BusinessException("Username already taken");
        }
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
            throw new BusinessException("Email already registered");
        }
        if (userRepository.existsByPhoneNumberAndDeletedAtIsNull(request.phoneNumber())) {
            throw new BusinessException("Phone number already registered");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .birthOfDate(request.birthOfDate())
                .phoneNumber(request.phoneNumber())
                .status(User.UserStatus.ACTIVE)  // Admin-created users are active immediately
                .emailVerifiedAt(Instant.now())
                .build();
        user = userRepository.save(user);

        // Assign roles if provided
        if (request.roleIds() != null && !request.roleIds().isEmpty()) {
            for (UUID roleId : request.roleIds()) {
                if (!roleRepository.existsById(roleId)) {
                    throw new BusinessException("Role not found: " + roleId, HttpStatus.NOT_FOUND);
                }
                UserRole userRole = UserRole.builder()
                        .userId(user.getId())
                        .roleId(roleId)
                        .build();
                userRoleRepository.save(userRole);
            }
        }

        log.info("Admin created user: id={}, username={}", user.getId(), user.getUsername());
        return UserResponse.from(user);
    }

    // ==================== ADMIN: UPDATE USER STATUS ====================

    @Transactional
    @CacheEvict(value = "users", key = "#id")
    public UserResponse updateUserStatus(UUID id, UpdateUserStatusRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));

        User.UserStatus newStatus;
        try {
            newStatus = User.UserStatus.valueOf(request.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid status: " + request.status()
                    + ". Valid values: PENDING, ACTIVE, LOCKED, BANNED");
        }

        user.setStatus(newStatus);

        // If unlocking, clear lock state
        if (newStatus == User.UserStatus.ACTIVE) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        // If banning or locking, revoke all sessions
        if (newStatus == User.UserStatus.BANNED || newStatus == User.UserStatus.LOCKED) {
            refreshTokenRepository.revokeAllByUserId(id, Instant.now());
        }

        User saved = userRepository.save(user);
        log.info("User status updated: id={}, status={}", id, newStatus);
        return UserResponse.from(saved);
    }

    // ==================== ADMIN: SEARCH USERS ====================

    public Page<UserResponse> searchUsers(String search, String status, String role, Pageable pageable) {
        Specification<User> spec = buildSearchSpecification(search, status, role);
        return userRepository.findAll(spec, pageable).map(UserResponse::from);
    }

    private Specification<User> buildSearchSpecification(String search, String status, String role) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Soft-delete filter (always active)
            predicates.add(cb.isNull(root.get("deletedAt")));

            // Keyword search across username, email, fullName, phoneNumber
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                Predicate searchPredicate = cb.or(
                        cb.like(cb.lower(root.get("username")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("fullName")), pattern),
                        cb.like(cb.lower(root.get("phoneNumber")), pattern)
                );
                predicates.add(searchPredicate);
            }

            // Filter by status
            if (status != null && !status.isBlank()) {
                try {
                    User.UserStatus userStatus = User.UserStatus.valueOf(status.toUpperCase());
                    predicates.add(cb.equal(root.get("status"), userStatus));
                } catch (IllegalArgumentException e) {
                    throw new BusinessException("Invalid status filter: " + status
                            + ". Valid values: PENDING, ACTIVE, LOCKED, BANNED");
                }
            }

            // Filter by role name
            if (role != null && !role.isBlank()) {
                Join<User, UserRole> userRoles = root.join("userRoles");
                Join<UserRole, Role> roleJoin = userRoles.join("role");
                predicates.add(cb.equal(roleJoin.get("name"), role));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ==================== ADMIN: CRUD ROLE ====================

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request, UUID actorId) {
        String roleName = request.name().toUpperCase().replaceAll("[^A-Z0-9_]", "_");
        if (!roleName.startsWith("ROLE_")) {
            roleName = "ROLE_" + roleName;
        }

        if (roleRepository.existsByName(roleName)) {
            throw new BusinessException("Role already exists: " + roleName);
        }

        Role role = Role.builder()
                .name(roleName)
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();
        role = roleRepository.save(role);

        log.info("Role created: id={}, name={}, createdBy={}", role.getId(), roleName, actorId);
        return new RoleResponse(role.getId(), role.getName(), List.of());
    }

    @Transactional
    public void deleteRole(UUID roleId, UUID actorId) {
        Role role = roleRepository.findByIdActive(roleId)
                .orElseThrow(() -> new BusinessException("Role not found", HttpStatus.NOT_FOUND));

        // Soft-delete: chỉ đánh dấu, không xóa vật lý
        // Vẫn giữ user_roles và role_permissions để bảo toàn lịch sử
        role.setDeletedAt(Instant.now());
        role.setUpdatedBy(actorId);
        roleRepository.save(role);

        log.info("Role soft-deleted: id={}, name={}, deletedBy={}", roleId, role.getName(), actorId);
    }

    // ==================== ADMIN: CRUD PERMISSION ====================

    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request, UUID actorId) {
        if (permissionRepository.findByName(request.name()).isPresent()) {
            throw new BusinessException("Permission already exists: " + request.name());
        }

        Permission permission = Permission.builder()
                .name(request.name())
                .displayName(request.displayName())
                .resource(request.resource())
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();
        permission = permissionRepository.save(permission);

        log.info("Permission created: id={}, name={}, createdBy={}", permission.getId(), request.name(), actorId);
        return new PermissionResponse(permission.getId(), permission.getName(),
                permission.getDisplayName(), permission.getResource());
    }

    @Transactional
    public PermissionResponse updatePermission(UUID permissionId, UpdatePermissionRequest request, UUID actorId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new BusinessException("Permission not found", HttpStatus.NOT_FOUND));

        if (permission.getDeletedAt() != null) {
            throw new BusinessException("Cannot update a deleted permission", HttpStatus.BAD_REQUEST);
        }

        if (request.name() != null) {
            var existing = permissionRepository.findByName(request.name());
            if (existing.isPresent() && !existing.get().getId().equals(permissionId)) {
                throw new BusinessException("Permission name already exists: " + request.name());
            }
            permission.setName(request.name());
        }
        if (request.displayName() != null) permission.setDisplayName(request.displayName());
        if (request.resource() != null) permission.setResource(request.resource());

        permission.setUpdatedBy(actorId);
        Permission saved = permissionRepository.save(permission);
        log.info("Permission updated: id={}, updatedBy={}", permissionId, actorId);
        return new PermissionResponse(saved.getId(), saved.getName(),
                saved.getDisplayName(), saved.getResource());
    }

    @Transactional
    public void deletePermission(UUID permissionId, UUID actorId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new BusinessException("Permission not found", HttpStatus.NOT_FOUND));

        if (permission.getDeletedAt() != null) {
            throw new BusinessException("Permission already deleted", HttpStatus.BAD_REQUEST);
        }

        // Soft-delete
        permission.setDeletedAt(Instant.now());
        permission.setUpdatedBy(actorId);
        // Cleanup role_permissions associations (giữ audit trail)
        rolePermissionRepository.findByPermissionId(permissionId).forEach(rp ->
                rolePermissionRepository.deleteByRoleIdAndPermissionId(rp.getRoleId(), permissionId));
        permissionRepository.save(permission);

        log.info("Permission soft-deleted: id={}, name={}, deletedBy={}", permissionId, permission.getName(), actorId);
    }
}
