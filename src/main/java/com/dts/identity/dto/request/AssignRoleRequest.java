package com.dts.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignRoleRequest(
        @NotNull(message = "User ID is required")
        @Schema(example = "3450b2db-9313-4138-98ec-86c202b64511")
        UUID userId,

        @NotNull(message = "Role ID is required")
        @Schema(example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        UUID roleId
) {}
