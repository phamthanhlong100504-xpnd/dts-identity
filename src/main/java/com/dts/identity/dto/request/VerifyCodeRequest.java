package com.dts.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VerifyCodeRequest(
        @NotBlank(message = "Username or email is required")
        String identifier,

        @NotBlank(message = "Verification code is required")
        String code,

        @NotNull(message = "Verification type is required")
        String type  // REGISTER, RESET_PASSWORD, CHANGE_EMAIL
) {}
