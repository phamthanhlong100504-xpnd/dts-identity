package com.dts.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePermissionRequest(
        @NotBlank(message = "Permission name is required")
        @Size(max = 100, message = "Permission name max 100 characters")
        String name,

        @NotBlank(message = "Display name is required")
        @Size(max = 100, message = "Display name max 100 characters")
        String displayName,

        @NotBlank(message = "Resource is required")
        @Size(max = 50, message = "Resource max 50 characters")
        String resource
) {}
