package com.dts.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        TokenProperties accessToken,
        TokenProperties refreshToken,
        String issuer
) {
    public record TokenProperties(
            String secret,
            long expirationMs
    ) {}
}
