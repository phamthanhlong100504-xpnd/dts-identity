package com.dts.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateMyProfileRequest(
        @Size(min = 3, max = 20, message = "Username must be 3-20 characters")
        @Pattern(regexp = ".*\\S.*", message = "Username must not be blank")
        @Schema(example = "testuser2")
        String username,

        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email max 254 characters")
        @Pattern(regexp = ".*\\S.*", message = "Email must not be blank")
        @Schema(example = "test@example.com")
        String email,

        @Size(max = 100, message = "Full name max 100 characters")
        @Pattern(regexp = ".*\\S.*", message = "Full name must not be blank")
        @Schema(example = "Test User Updated")
        String fullName,

        @Schema(type = "string", format = "date", example = "2000-01-01")
        LocalDate birthOfDate,

        @Size(max = 20, message = "Phone number max 20 characters")
        @Pattern(regexp = ".*\\S.*", message = "Phone number must not be blank")
        @Schema(example = "0987654321")
        String phoneNumber
) {}