package com.dts.identity.dto.response;

import java.util.List;
import java.util.UUID;

public record TokenValidationResponse(
        boolean valid,
        UUID userId,
        String username,
        List<String> roles,
        List<String> permissions
) {
    public static TokenValidationResponse invalid() {
        return new TokenValidationResponse(false, null, null, null, null);
    }

    public static TokenValidationResponse valid(UUID userId, String username,
                                                 List<String> roles, List<String> permissions) {
        return new TokenValidationResponse(true, userId, username, roles, permissions);
    }
}
