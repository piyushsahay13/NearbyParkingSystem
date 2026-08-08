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

    // ─── Redis Failure Tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("Should FAIL OPEN when Redis throws exception")
    void shouldFailOpenOnRedisException() {
        doThrow(new RuntimeException("Redis connection refused"))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any());

        RateLimitResult result = rateLimiter.checkRateLimit("rate-limit:192.168.1.1");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.isFailOpen()).isTrue();
    }

    @Test
    @DisplayName("Should FAIL OPEN when Redis returns null result")
    void shouldFailOpenOnNullRedisResult() {
        doReturn(null)
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any());

        RateLimitResult result = rateLimiter.checkRateLimit("rate-limit:192.168.1.1");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.isFailOpen()).isTrue();
    }

    @Test
    @DisplayName("Should FAIL OPEN when Redis returns incomplete result list")
    void shouldFailOpenOnIncompleteRedisResult() {
        doReturn(List.of(1L)) // Missing remaining and ttl fields
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any());

        RateLimitResult result = rateLimiter.checkRateLimit("rate-limit:192.168.1.1");

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
    }

    @Test
    @DisplayName("RateLimitResult.blocked() should carry retry-after seconds")
    void blockedResultShouldCarryRetryAfter() {
        RateLimitResult result = RateLimitResult.blocked(30L);
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRemaining()).isEqualTo(0);
        assertThat(result.getResetSeconds()).isEqualTo(30L);
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
