package com.dts.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminResetPasswordRequest(
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
        @Schema(example = "NewPassword@123")
        String password
) {}
