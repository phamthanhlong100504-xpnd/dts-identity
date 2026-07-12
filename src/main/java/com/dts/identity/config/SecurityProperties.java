package com.dts.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
        BcryptProperties bcrypt,
        BruteForceProperties bruteForce,
        VerificationCodeProperties verificationCode
) {
    public record BcryptProperties(int strength) {}
    public record BruteForceProperties(int maxFailedAttempts, int lockDurationMinutes) {}
    public record VerificationCodeProperties(int expirationMinutes, int maxAttempts) {}
}
