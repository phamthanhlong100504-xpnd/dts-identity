package com.dts.identity.dto.request;

import jakarta.validation.constraints.Size;

public record UpdatePermissionRequest(
        @Size(max = 100, message = "Permission name max 100 characters")
        String name,

        @Size(max = 100, message = "Display name max 100 characters")
        String displayName,

        @Size(max = 50, message = "Resource max 50 characters")
        String resource
) {}
