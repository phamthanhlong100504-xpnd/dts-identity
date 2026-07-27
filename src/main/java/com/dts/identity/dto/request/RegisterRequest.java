package com.dts.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 20, message = "Username must be 3-20 characters")
        @Schema(example = "testuser")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Schema(example = "test@example.com")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
        @Schema(example = "Test@1234")
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name max 100 characters")
        @Schema(example = "Test User")
        String fullName,

        @NotNull(message = "Birth date is required")
        @Past(message = "Birth date must be in the past")
        @JsonFormat(pattern = "yyyy-MM-dd")
        @Schema(type = "string", format = "date", example = "2000-01-01")
        LocalDate birthOfDate,

        @NotBlank(message = "Phone number is required")
        @Size(max = 20, message = "Phone number max 20 characters")
        @Schema(example = "0123456789")
        String phoneNumber
) {}
