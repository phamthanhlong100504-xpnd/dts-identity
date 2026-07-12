package com.dts.identity.service;

import com.dts.identity.dto.request.AssignRoleRequest;
import com.dts.identity.dto.request.UpdateUserRequest;
import com.dts.identity.dto.response.PermissionResponse;
import com.dts.identity.dto.response.RoleResponse;
import com.dts.identity.dto.response.UserResponse;
import com.dts.identity.entity.*;
import com.dts.identity.exception.BusinessException;
import com.dts.identity.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
                .map(ur -> {
                    Role role = ur.getRole();
                    List<String> permissions = rolePermissionRepository.findByRoleId(role.getId()).stream()
                            .map(rp -> rp.getPermission().getName())
                            .collect(Collectors.toList());
                    return new RoleResponse(role.getId(), role.getName().name(), permissions);
                })
                .collect(Collectors.toList());
    }

    // ==================== ROLE & PERMISSION CATALOG ====================

    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
                .map(role -> {
                    List<String> permissions = rolePermissionRepository.findByRoleId(role.getId()).stream()
                            .map(rp -> rp.getPermission().getName())
                            .collect(Collectors.toList());
                    return new RoleResponse(role.getId(), role.getName().name(), permissions);
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
}
