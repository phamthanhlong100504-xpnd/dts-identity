package com.dts.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Username or email is required")
        @Schema(example = "test@example1.com")
        String identifier,

        @NotBlank(message = "Verification code is required")
        @Schema(example = "123456")
        String code,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "New password must be 8-100 characters")
        @Schema(example = "NewPassword@123")
        String newPassword
) {}
