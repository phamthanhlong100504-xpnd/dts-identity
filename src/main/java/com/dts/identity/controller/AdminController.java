package com.dts.identity.controller;

import com.dts.identity.dto.request.AssignRoleRequest;
import com.dts.identity.dto.request.UpdateUserRequest;
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
    @Operation(summary = "List all users (paginated)")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> listUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(userService.listUsers(pageable)));
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
}
