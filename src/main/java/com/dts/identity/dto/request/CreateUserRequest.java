package com.dts.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 20, message = "Username must be 3-20 characters")
        @Schema(example = "newuser")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Schema(example = "newuser@example.com")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
        @Schema(example = "Password@123")
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name max 100 characters")
        @Schema(example = "New User")
        String fullName,

        @NotNull(message = "Birth date is required")
        @Past(message = "Birth date must be in the past")
        @Schema(type = "string", format = "date", example = "2000-01-01")
        LocalDate birthOfDate,

        @NotBlank(message = "Phone number is required")
        @Size(max = 20, message = "Phone number max 20 characters")
        @Schema(example = "0987654321")
        String phoneNumber,

        @Schema(example = "[\"a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11\"]")
        List<UUID> roleIds
) {}
