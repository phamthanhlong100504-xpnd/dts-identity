package com.dts.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Old password is required")
        @Schema(example = "Test@1234")
        String oldPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "New password must be 8-100 characters")
        @Schema(example = "NewPassword@123")
        String newPassword
) {}
