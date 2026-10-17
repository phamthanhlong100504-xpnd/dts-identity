package com.dts.identity.controller;

import com.dts.identity.dto.request.UpdateLicenseRequest;
import com.dts.identity.dto.request.UpdateMyProfileRequest;
import com.dts.identity.dto.response.ApiResponse;
import com.dts.identity.dto.response.RoleResponse;
import com.dts.identity.dto.response.UserLicenseResponse;
import com.dts.identity.dto.response.UserResponse;
import com.dts.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "CRUD users, get own profile")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user's profile")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(
            Authentication authentication
    ) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok(userService.getUser(userId)));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current authenticated user's profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateMyProfileRequest request
    ) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok(
                userService.updateMyProfile(userId, request)
        ));
    }

    @PutMapping("/me/license")
    @Operation(summary = "Select current driving license class")
    public ResponseEntity<ApiResponse<UserLicenseResponse>> updateLicense(
            Authentication authentication,
            @Valid @RequestBody UpdateLicenseRequest request
    ) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok(
                "License class updated",
                userService.updateSelectedLicenseClass(userId, request)
        ));
    }

    @GetMapping("/me/roles")
    @Operation(summary = "Get current user's roles and permissions")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getMyRoles(
            Authentication authentication
    ) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok(
                userService.getUserRoles(userId)
        ));
    }
}