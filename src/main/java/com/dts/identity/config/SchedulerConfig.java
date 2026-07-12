package com.dts.identity.config;

import com.dts.identity.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduled tasks for housekeeping: clean up expired tokens.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SchedulerConfig {

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Clean up expired + revoked refresh tokens every hour.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanExpiredTokens() {
        Instant now = Instant.now();
        int deleted = refreshTokenRepository.deleteExpiredRevoked(now);
        if (deleted > 0) {
            log.info("Cleaned {} expired revoked refresh tokens", deleted);
        }
    }
}
