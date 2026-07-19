package com.dts.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank(message = "Role name is required")
        @Size(min = 3, max = 50, message = "Role name must be 3-50 characters")
        String name
) {}
