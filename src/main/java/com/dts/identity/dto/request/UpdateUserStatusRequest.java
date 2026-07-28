package com.dts.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateUserStatusRequest(
        @NotBlank(message = "Status is required")
        @Schema(example = "ACTIVE", allowableValues = {"ACTIVE", "LOCKED", "BANNED", "PENDING"})
        String status  // ACTIVE, LOCKED, BANNED
) {}
