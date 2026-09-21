package com.wego.parkingsystem.ratelimit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * Unit tests for {@link TokenBucketRateLimiter}.
 * Tests boundary conditions (9th/10th/11th request), Redis failure Fail-Open,
 * and blocked result metadata.
 */
@DisplayName("TokenBucketRateLimiter — Rate Limit Boundary Tests")
class RateLimiterBoundaryTest {

    private RedisTemplate<String, String> redisTemplate;
    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = Mockito.mock(RedisTemplate.class);
        rateLimiter = new TokenBucketRateLimiter(redisTemplate, new SimpleMeterRegistry());
    }

    // ─── Boundary Tests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("9th request should be ALLOWED with remaining=1")
    void ninthRequestShouldBeAllowed() {
        // Simulate Redis returning: allowed=1, remaining=1, reset=60
        doReturn(List.of(1L, 1L, 60L))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any());

        RateLimitResult result = rateLimiter.checkRateLimit("rate-limit:192.168.1.1");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemaining()).isEqualTo(1);
        assertThat(result.getResetSeconds()).isEqualTo(60L);
        assertThat(result.isFailOpen()).isFalse();
    }

    @Test
    @DisplayName("10th request should be ALLOWED with remaining=0")
    void tenthRequestShouldBeAllowedWithZeroRemaining() {
        // Simulate Redis returning: allowed=1, remaining=0, reset=60
        doReturn(List.of(1L, 0L, 60L))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any());

        RateLimitResult result = rateLimiter.checkRateLimit("rate-limit:192.168.1.1");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("11th request should be BLOCKED with HTTP 429 and Retry-After")
    void eleventhRequestShouldBeBlocked() {
        // Simulate Redis returning: allowed=0, remaining=0, ttl=42
        doReturn(List.of(0L, 0L, 42L))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any());

        RateLimitResult result = rateLimiter.checkRateLimit("rate-limit:192.168.1.1");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRemaining()).isEqualTo(0);
        assertThat(result.getResetSeconds()).isEqualTo(42L);
        assertThat(result.isFailOpen()).isFalse();
    }

    // ─── Resilient Fallback Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("Should FALL BACK to in-memory rate limiter when Redis throws exception")
    void shouldFallbackToInMemoryLimiterOnRedisException() {
        doThrow(new RuntimeException("Redis connection refused"))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any());

        RateLimitResult result = rateLimiter.checkRateLimit("rate-limit:192.168.1.1");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.isFallback()).isTrue();
        assertThat(result.getRemaining()).isEqualTo(9);
        assertThat(result.isFailOpen()).isFalse();
    }

    @Test
    @DisplayName("Should enforce rate limiting via fallback when Redis is completely down (10 allowed, 11th blocked)")
    void shouldEnforceRateLimitsViaFallbackWhenRedisIsDown() {
        doThrow(new RuntimeException("Redis cluster unreachable"))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any());

        String key = "rate-limit:192.168.1.50";

        // First 10 requests must be allowed by in-memory fallback
        for (int i = 0; i < 10; i++) {
            RateLimitResult result = rateLimiter.checkRateLimit(key);
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.isFallback()).isTrue();
            assertThat(result.getRemaining()).isEqualTo(9 - i);
        }

        // 11th request must be BLOCKED by in-memory fallback to protect backend
        RateLimitResult blocked = rateLimiter.checkRateLimit(key);
        assertThat(blocked.isAllowed()).isFalse();
        assertThat(blocked.isFallback()).isTrue();
        assertThat(blocked.getRemaining()).isEqualTo(0);
        assertThat(blocked.getResetSeconds()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should FALL BACK to in-memory rate limiter when Redis returns null result")
    void shouldFallbackOnNullRedisResult() {
        doReturn(null)
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any());

        RateLimitResult result = rateLimiter.checkRateLimit("rate-limit:192.168.1.2");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.isFallback()).isTrue();
        assertThat(result.getRemaining()).isEqualTo(9);
    }

    @Test
    @DisplayName("Should FALL BACK to in-memory rate limiter when Redis returns incomplete result list")
    void shouldFallbackOnIncompleteRedisResult() {
        doReturn(List.of(1L)) // Missing remaining and ttl fields
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any());

        RateLimitResult result = rateLimiter.checkRateLimit("rate-limit:192.168.1.3");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.isFallback()).isTrue();
        assertThat(result.getRemaining()).isEqualTo(9);
    }

    @Test
    @DisplayName("Circuit breaker in OPEN state fast-fails directly to in-memory fallback without touching Redis")
    void shouldFastFailToFallbackWhenCircuitBreakerIsOpen() {
        rateLimiter.getCircuitBreaker().transitionToOpenState();

        RateLimitResult result = rateLimiter.checkRateLimit("rate-limit:192.168.1.4");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.isFallback()).isTrue();
        // Verify Redis was NEVER invoked because CircuitBreaker is open
        Mockito.verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Should FAIL OPEN when both Redis and fallback throw unexpected exceptions")
    void shouldFailOpenWhenBothRedisAndFallbackFail() {
        InMemoryTokenBucketRateLimiter failingFallback = Mockito.mock(InMemoryTokenBucketRateLimiter.class);
        Mockito.when(failingFallback.checkRateLimit(anyString()))
                .thenThrow(new RuntimeException("Fallback memory error"));

        TokenBucketRateLimiter resilientLimiter = new TokenBucketRateLimiter(
                redisTemplate, new SimpleMeterRegistry(), failingFallback);

        doThrow(new RuntimeException("Redis unavailable"))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any());

        RateLimitResult result = resilientLimiter.checkRateLimit("rate-limit:192.168.1.5");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.isFailOpen()).isTrue();
    }

    // ─── RateLimitResult Factory Method Tests ─────────────────────────────────

    @Test
    @DisplayName("RateLimitResult.allowed() should carry correct metadata")
    void allowedResultShouldHaveCorrectMetadata() {
        RateLimitResult result = RateLimitResult.allowed(7, 55L);
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemaining()).isEqualTo(7);
        assertThat(result.getResetSeconds()).isEqualTo(55L);
        assertThat(result.isFailOpen()).isFalse();
        assertThat(result.isFallback()).isFalse();
    }

    @Test
    @DisplayName("RateLimitResult.allowedFallback() should carry fallback flag")
    void allowedFallbackResultShouldHaveFallbackFlag() {
        RateLimitResult result = RateLimitResult.allowedFallback(7, 55L);
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemaining()).isEqualTo(7);
        assertThat(result.getResetSeconds()).isEqualTo(55L);
        assertThat(result.isFailOpen()).isFalse();
        assertThat(result.isFallback()).isTrue();
    }

    @Test
    @DisplayName("RateLimitResult.blocked() should carry retry-after seconds")
    void blockedResultShouldCarryRetryAfter() {
        RateLimitResult result = RateLimitResult.blocked(30L);
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRemaining()).isEqualTo(0);
        assertThat(result.getResetSeconds()).isEqualTo(30L);
        assertThat(result.isFallback()).isFalse();
    }

    @Test
    @DisplayName("RateLimitResult.blockedFallback() should carry retry-after and fallback flag")
    void blockedFallbackResultShouldCarryRetryAfterAndFallbackFlag() {
        RateLimitResult result = RateLimitResult.blockedFallback(30L);
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRemaining()).isEqualTo(0);
        assertThat(result.getResetSeconds()).isEqualTo(30L);
        assertThat(result.isFallback()).isTrue();
    }

    @Test
    @DisplayName("RateLimitResult.failOpen() should be marked as fail-open allowed")
    void failOpenResultShouldBeAllowedAndFlagged() {
        RateLimitResult result = RateLimitResult.failOpen();
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.isFailOpen()).isTrue();
        assertThat(result.getRemaining()).isEqualTo(-1);
    }
}
