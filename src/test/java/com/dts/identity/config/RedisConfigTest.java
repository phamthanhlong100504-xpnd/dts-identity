package com.dts.identity.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

    private RedisConfig redisConfig;

    @Mock
    private Cache cache;

    @BeforeEach
    void setUp() {
        redisConfig = new RedisConfig();
    }

    @Test
    void errorHandler_DoesNotRethrowExceptions() {
        CacheErrorHandler handler = redisConfig.errorHandler();
        RuntimeException failure = new RuntimeException("Redis connection refused");

        when(cache.getName()).thenReturn("users");

        assertDoesNotThrow(() -> handler.handleCacheGetError(failure, cache, "key1"));
        assertDoesNotThrow(() -> handler.handleCachePutError(failure, cache, "key1", "val1"));
        assertDoesNotThrow(() -> handler.handleCacheEvictError(failure, cache, "key1"));
        assertDoesNotThrow(() -> handler.handleCacheClearError(failure, cache));
    }
}
