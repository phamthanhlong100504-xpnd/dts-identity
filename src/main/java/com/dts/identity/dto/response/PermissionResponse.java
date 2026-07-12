package com.dts.identity.dto.response;

import java.util.UUID;

public record PermissionResponse(
        UUID id,
        String name,
        String displayName,
        String resource
) {}
