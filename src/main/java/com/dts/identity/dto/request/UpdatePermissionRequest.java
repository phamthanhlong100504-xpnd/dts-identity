package com.dts.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UpdatePermissionRequest(
        @Size(max = 100, message = "Permission name max 100 characters")
        @Schema(example = "questions:read")
        String name,

        @Size(max = 100, message = "Display name max 100 characters")
        @Schema(example = "Xem câu hỏi")
        String displayName,

        @Size(max = 50, message = "Resource max 50 characters")
        @Schema(example = "questions")
        String resource
) {}
