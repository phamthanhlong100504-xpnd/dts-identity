package com.dts.identity.dto.response;

import java.util.List;
import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserInfo user
) {
    public record UserInfo(
            UUID id,
            String username,
            String email,
            String fullName,
            List<String> roles,
            List<String> permissions
    ) {}
}
