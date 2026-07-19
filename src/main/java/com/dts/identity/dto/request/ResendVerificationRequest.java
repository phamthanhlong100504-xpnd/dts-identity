package com.dts.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(
        @NotBlank(message = "Username or email is required")
        String identifier,

        @NotBlank(message = "Verification type is required")
        String type  // REGISTER, RESET_PASSWORD, CHANGE_EMAIL
) {}
