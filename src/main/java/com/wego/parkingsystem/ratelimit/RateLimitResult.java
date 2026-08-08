package com.wego.parkingsystem.ratelimit;

import lombok.Value;

/**
 * Result of a rate limit evaluation.
 * Carries the allowed/blocked decision and metadata for response headers.
 * <p>
 * This is a Lombok {@code @Value} class instead of a Java Record to avoid
 * potential conflicts with other annotation processors during the build process.
 */
@Value
public class RateLimitResult {
    boolean allowed;
    int remaining;
    long resetSeconds;
    boolean failOpen;

    public static RateLimitResult allowed(int remaining, long resetSeconds) {
        return new RateLimitResult(true, remaining, resetSeconds, false);
    }

    public static RateLimitResult blocked(long retryAfterSeconds) {
        return new RateLimitResult(false, 0, retryAfterSeconds, false);
    }

    public static RateLimitResult failOpen() {
        return new RateLimitResult(true, -1, -1, true);
    }
}
