package com.wego.parkingsystem.ratelimit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryTokenBucketRateLimiter — Fallback Unit Tests")
class InMemoryTokenBucketRateLimiterTest {

    private InMemoryTokenBucketRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new InMemoryTokenBucketRateLimiter(new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("First request consumes 1 token and returns allowed fallback result")
    void firstRequestShouldConsumeOneToken() {
        RateLimitResult result = limiter.checkRateLimit("client:10.0.0.1");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemaining()).isEqualTo(9);
        assertThat(result.getResetSeconds()).isEqualTo(60L);
        assertThat(result.isFallback()).isTrue();
        assertThat(result.isFailOpen()).isFalse();
    }

    @Test
    @DisplayName("10 requests consume all tokens and 11th request is blocked")
    void tenRequestsAllowedEleventhBlocked() {
        String key = "client:10.0.0.2";

        for (int i = 0; i < 10; i++) {
            RateLimitResult result = limiter.checkRateLimit(key);
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemaining()).isEqualTo(9 - i);
            assertThat(result.isFallback()).isTrue();
        }

        // 11th request must be blocked
        RateLimitResult blockedResult = limiter.checkRateLimit(key);
        assertThat(blockedResult.isAllowed()).isFalse();
        assertThat(blockedResult.getRemaining()).isEqualTo(0);
        assertThat(blockedResult.getResetSeconds()).isGreaterThan(0);
        assertThat(blockedResult.isFallback()).isTrue();
    }

    @Test
    @DisplayName("Independent clients have separate token bucket state")
    void independentClientsHaveSeparateState() {
        String clientA = "client:10.0.0.3";
        String clientB = "client:10.0.0.4";

        for (int i = 0; i < 10; i++) {
            limiter.checkRateLimit(clientA);
        }

        // Client A should be blocked
        assertThat(limiter.checkRateLimit(clientA).isAllowed()).isFalse();

        // Client B should still have full quota
        RateLimitResult resultB = limiter.checkRateLimit(clientB);
        assertThat(resultB.isAllowed()).isTrue();
        assertThat(resultB.getRemaining()).isEqualTo(9);
    }

    @Test
    @DisplayName("Stale buckets cleanup removes expired entries")
    void staleBucketsCleanup() {
        limiter.checkRateLimit("client:stale-1");
        limiter.checkRateLimit("client:stale-2");
        assertThat(limiter.getBucketCount()).isEqualTo(2);

        // Clean with ttl = 0 (everything older than 0s since now)
        int removed = limiter.cleanStaleBuckets(-1);
        assertThat(removed).isEqualTo(2);
        assertThat(limiter.getBucketCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("getCapacity returns configured capacity")
    void getCapacityReturnsConfiguredCapacity() {
        assertThat(limiter.getCapacity()).isEqualTo(10);
    }
}
