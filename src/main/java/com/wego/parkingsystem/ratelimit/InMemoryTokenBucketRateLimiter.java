package com.wego.parkingsystem.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe In-Memory Token Bucket rate limiter.
 * <p>
 * Serves as the primary resilient fallback when the distributed Redis rate limiter is down,
 * timing out, or tripped open by a circuit breaker. This ensures:
 * <ul>
 *   <li>The API does NOT fail or hang waiting for Redis timeouts.</li>
 *   <li>The API does NOT fail open without rate limits, preventing overload/DoS during outages.</li>
 *   <li>Local rate limits are strictly enforced per node until Redis recovers.</li>
 * </ul>
 */
@Component("inMemoryTokenBucketRateLimiter")
@Slf4j
public class InMemoryTokenBucketRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, BucketState> buckets = new ConcurrentHashMap<>();

    @Getter
    @Value("${rate-limit.capacity:10}")
    private int capacity = 10;

    @Getter
    @Value("${rate-limit.refill-tokens:10}")
    private int refillTokens = 10;

    @Getter
    @Value("${rate-limit.refill-duration-seconds:60}")
    private int refillDurationSeconds = 60;

    @Getter
    @Value("${rate-limit.cost-per-request:1}")
    private int costPerRequest = 1;

    @Value("${rate-limit.fallback.max-buckets:20000}")
    private int maxBuckets = 20000;

    private final Counter fallbackAllowedCounter;
    private final Counter fallbackBlockedCounter;

    @Autowired
    public InMemoryTokenBucketRateLimiter(MeterRegistry meterRegistry) {
        MeterRegistry registry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
        this.fallbackAllowedCounter = Counter.builder("rate_limit_fallback_allowed_total")
                .description("Total requests allowed by in-memory fallback rate limiter")
                .register(registry);
        this.fallbackBlockedCounter = Counter.builder("rate_limit_fallback_blocked_total")
                .description("Total requests blocked by in-memory fallback rate limiter")
                .register(registry);
    }

    /**
     * Testing constructor with explicit parameters.
     */
    public InMemoryTokenBucketRateLimiter(int capacity, int refillTokens, int refillDurationSeconds, int costPerRequest) {
        this(new SimpleMeterRegistry());
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillDurationSeconds = refillDurationSeconds;
        this.costPerRequest = costPerRequest;
    }

    @Override
    public RateLimitResult checkRateLimit(String clientKey) {
        long now = Instant.now().getEpochSecond();

        // Guard against memory exhaustion if unique keys grow excessively
        if (buckets.size() > maxBuckets) {
            cleanStaleBuckets(refillDurationSeconds * 2L);
        }

        BucketState state = buckets.computeIfAbsent(clientKey, k -> new BucketState(capacity, now));
        RateLimitResult result = state.tryConsume(capacity, refillTokens, refillDurationSeconds, costPerRequest, now);

        if (result.isAllowed()) {
            fallbackAllowedCounter.increment();
        } else {
            fallbackBlockedCounter.increment();
        }

        return result;
    }

    /**
     * Periodically evicts stale client buckets to prevent memory leaks.
     */
    @Scheduled(fixedDelayString = "${rate-limit.fallback.cleanup-interval-ms:60000}")
    public void scheduledCleanup() {
        cleanStaleBuckets(refillDurationSeconds * 2L);
    }

    /**
     * Cleans up buckets that have been inactive longer than the given TTL in seconds.
     *
     * @param ttlSeconds age in seconds after which a bucket is considered expired
     * @return number of cleaned buckets
     */
    public int cleanStaleBuckets(long ttlSeconds) {
        long now = Instant.now().getEpochSecond();
        int initialSize = buckets.size();
        buckets.entrySet().removeIf(entry -> entry.getValue().isStale(now, ttlSeconds));
        int removed = initialSize - buckets.size();
        if (removed > 0) {
            log.debug("Cleaned up {} stale in-memory rate limit buckets.", removed);
        }
        return removed;
    }

    /**
     * Returns the current number of tracked client buckets.
     */
    public int getBucketCount() {
        return buckets.size();
    }

    /**
     * Internal state for a single token bucket.
     */
    static class BucketState {
        private double tokens;
        private long lastUpdated;

        BucketState(double initialTokens, long lastUpdated) {
            this.tokens = initialTokens;
            this.lastUpdated = lastUpdated;
        }

        synchronized RateLimitResult tryConsume(int capacity, int refillTokens, int refillDurationSeconds, int cost, long now) {
            long elapsed = now - lastUpdated;
            if (elapsed > 0) {
                double delta = ((double) elapsed / refillDurationSeconds) * refillTokens;
                tokens = Math.min((double) capacity, tokens + delta);
                lastUpdated = now;
            }

            if (tokens >= cost) {
                tokens -= cost;
                int remaining = (int) Math.floor(tokens);
                long resetSeconds = refillDurationSeconds;
                return RateLimitResult.allowedFallback(remaining, resetSeconds);
            } else {
                double missing = cost - tokens;
                long retryAfter = (long) Math.ceil((missing / refillTokens) * refillDurationSeconds);
                if (retryAfter <= 0) {
                    retryAfter = 1;
                }
                return RateLimitResult.blockedFallback(retryAfter);
            }
        }

        synchronized boolean isStale(long now, long ttlSeconds) {
            return (now - lastUpdated) > ttlSeconds;
        }
    }
}
