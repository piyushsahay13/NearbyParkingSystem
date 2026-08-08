package com.wego.parkingsystem.ratelimit;

/**
 * Strategy interface for rate limiting implementations.
 * Concrete strategies: {@link TokenBucketRateLimiter}, SlidingWindowRateLimiter, FixedWindowRateLimiter.
 */
public interface RateLimiter {

    /**
     * Evaluates the rate limit for the given client key.
     *
     * @param clientKey the rate limit key (e.g. "rate-limit:192.168.10.25")
     * @return {@link RateLimitResult} describing the decision (allowed/blocked/fail-open)
     */
    RateLimitResult checkRateLimit(String clientKey);
}
