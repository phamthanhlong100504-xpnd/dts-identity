package com.dts.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserStatusRequest(
        @NotBlank(message = "Status is required")
        String status  // ACTIVE, LOCKED, BANNED
) {}
