package com.dts.identity.dto.response;

import java.util.List;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        List<String> permissions
) {}
