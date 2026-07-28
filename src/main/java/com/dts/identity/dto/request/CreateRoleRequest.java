package com.dts.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank(message = "Role name is required")
        @Size(min = 3, max = 50, message = "Role name must be 3-50 characters")
        @Schema(example = "ROLE_TEACHER")
        String name
) {}
