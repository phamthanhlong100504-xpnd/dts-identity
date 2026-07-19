package com.dts.identity.controller;

import com.dts.identity.dto.request.*;
import com.dts.identity.dto.response.ApiResponse;
import com.dts.identity.dto.response.PermissionResponse;
import com.dts.identity.dto.response.RoleResponse;
import com.dts.identity.dto.response.UserResponse;
import com.dts.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "User, role, and permission management (admin only)")
public class AdminController {

    private final UserService userService;

    // ==================== USER MANAGEMENT ====================

    @GetMapping("/users")
    @Operation(summary = "List users with optional search and filter (paginated)")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> listUsers(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role) {
        return ResponseEntity.ok(ApiResponse.ok(userService.searchUsers(search, status, role, pageable)));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUser(id)));
    }

    @PutMapping("/users/{id}")
    @Operation(summary = "Update user")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateUser(id, request)));
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "Soft-delete user")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.ok("User deleted", null));
    }

    // ==================== ROLE ASSIGNMENT ====================

    @PostMapping("/roles/assign")
    @Operation(summary = "Assign a role to a user")
    public ResponseEntity<ApiResponse<Void>> assignRole(@Valid @RequestBody AssignRoleRequest request) {
        userService.assignRole(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Role assigned", null));
    }

    @DeleteMapping("/users/{userId}/roles/{roleId}")
    @Operation(summary = "Revoke a role from a user")
    public ResponseEntity<ApiResponse<Void>> revokeRole(
            @PathVariable UUID userId, @PathVariable UUID roleId) {
        userService.revokeRole(userId, roleId);
        return ResponseEntity.ok(ApiResponse.ok("Role revoked", null));
    }

    // ==================== ROLES & PERMISSIONS CATALOG ====================

    @GetMapping("/roles")
    @Operation(summary = "List all roles with their permissions")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> listRoles() {
        return ResponseEntity.ok(ApiResponse.ok(userService.listRoles()));
    }

    @GetMapping("/permissions")
    @Operation(summary = "List all permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> listPermissions() {
        return ResponseEntity.ok(ApiResponse.ok(userService.listPermissions()));
    }

    @PostMapping("/roles/{roleId}/permissions/{permissionId}")
    @Operation(summary = "Assign a permission to a role")
    public ResponseEntity<ApiResponse<Void>> assignPermissionToRole(
            @PathVariable UUID roleId, @PathVariable UUID permissionId) {
        userService.assignPermissionToRole(roleId, permissionId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Permission assigned to role", null));
    }

    @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
    @Operation(summary = "Revoke a permission from a role")
    public ResponseEntity<ApiResponse<Void>> revokePermissionFromRole(
            @PathVariable UUID roleId, @PathVariable UUID permissionId) {
        userService.revokePermissionFromRole(roleId, permissionId);
        return ResponseEntity.ok(ApiResponse.ok("Permission revoked from role", null));
    }

    // ==================== USER CREATION & STATUS ====================

    @PostMapping("/users")
    @Operation(summary = "Create a new user (admin only, no verification needed)")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User created", response));
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Update user status (ACTIVE, LOCKED, BANNED)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateUserStatus(id, request)));
    }

    // ==================== USER ROLES ====================

    @GetMapping("/users/{id}/roles")
    @Operation(summary = "Get roles for a specific user")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getUserRoles(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserRoles(id)));
    }

    // ==================== ROLE CRUD ====================

    @PostMapping("/roles")
    @Operation(summary = "Create a new role")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleRequest request,
            Authentication authentication) {
        UUID actorId = (UUID) authentication.getPrincipal();
        RoleResponse response = userService.createRole(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Role created", response));
    }

    @DeleteMapping("/roles/{id}")
    @Operation(summary = "Soft-delete a role (preserves history)")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID actorId = (UUID) authentication.getPrincipal();
        userService.deleteRole(id, actorId);
        return ResponseEntity.ok(ApiResponse.ok("Role deleted", null));
    }

    // ==================== PERMISSION CRUD ====================

    @PostMapping("/permissions")
    @Operation(summary = "Create a new permission")
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(
            @Valid @RequestBody CreatePermissionRequest request,
            Authentication authentication) {
        UUID actorId = (UUID) authentication.getPrincipal();
        PermissionResponse response = userService.createPermission(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Permission created", response));
    }

    @PutMapping("/permissions/{id}")
    @Operation(summary = "Update a permission")
    public ResponseEntity<ApiResponse<PermissionResponse>> updatePermission(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePermissionRequest request,
            Authentication authentication) {
        UUID actorId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok(userService.updatePermission(id, request, actorId)));
    }

    @DeleteMapping("/permissions/{id}")
    @Operation(summary = "Soft-delete a permission")
    public ResponseEntity<ApiResponse<Void>> deletePermission(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID actorId = (UUID) authentication.getPrincipal();
        userService.deletePermission(id, actorId);
        return ResponseEntity.ok(ApiResponse.ok("Permission deleted", null));
    }
}
