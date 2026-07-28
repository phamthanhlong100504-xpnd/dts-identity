package com.dts.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePermissionRequest(
        @NotBlank(message = "Permission name is required")
        @Size(max = 100, message = "Permission name max 100 characters")
        @Schema(example = "courses:read")
        String name,

        @NotBlank(message = "Display name is required")
        @Size(max = 100, message = "Display name max 100 characters")
        @Schema(example = "Read Courses")
        String displayName,

        @NotBlank(message = "Resource is required")
        @Size(max = 50, message = "Resource max 50 characters")
        @Schema(example = "courses")
        String resource
) {}
