package com.dts.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Username or email is required")
        String identifier,

        @NotBlank(message = "Verification code is required")
        String code,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "New password must be 8-100 characters")
        String newPassword
) {}
