package com.wego.parkingsystem.ratelimit;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Token Bucket rate limiter backed by Redis atomic Lua script execution with
 * an automated Resilience4j Circuit Breaker and In-Memory Token Bucket Fallback.
 *
 * <p>Algorithm:
 * Each client IP has a Redis hash key {@code rate-limit:{ip}} storing:
 * <ul>
 *   <li>{@code tokens}       — current token count (float)</li>
 *   <li>{@code last_updated} — Unix epoch timestamp (seconds)</li>
 * </ul>
 *
 * <p>On each request, an atomic Lua script:
 * 1. Reads the hash fields.
 * 2. Calculates elapsed time and refills proportional tokens.
 * 3. Deducts 1 token if available; blocks if empty.
 * 4. Returns [allowed(0|1), remaining(int), ttl(seconds)].
 *
 * <p>Resilience & Fallback:
 * If Redis is unreachable, timing out, or the Circuit Breaker is OPEN:
 * 1. Fast-fails immediately without blocking Tomcat worker threads.
 * 2. Evaluates rate limiting via {@link InMemoryTokenBucketRateLimiter}.
 * 3. Enforces local rate limits to protect the API against surges.
 * 4. Self-heals when Redis recovers and transitions back to CLOSED state.
 */
@Component("tokenBucketRateLimiter")
@Primary
@Slf4j
public class TokenBucketRateLimiter implements RateLimiter {

    // ─── Lua Script — Atomic Token Bucket ────────────────────────────────────
    private static final String LUA_SCRIPT = """
            local key             = KEYS[1]
            local capacity        = tonumber(ARGV[1])
            local refill_tokens   = tonumber(ARGV[2])
            local refill_duration = tonumber(ARGV[3])
            local cost            = tonumber(ARGV[4])
            local now             = tonumber(ARGV[5])

            local data        = redis.call("HMGET", key, "tokens", "last_updated")
            local tokens      = tonumber(data[1])
            local last_updated = tonumber(data[2])

            if not tokens then
                tokens       = capacity
                last_updated = now
            else
                local elapsed     = now - last_updated
                if elapsed > 0 then
                    local delta = (elapsed / refill_duration) * refill_tokens
                    tokens      = math.min(capacity, tokens + delta)
                    last_updated = now
                end
            end

            local allowed   = 0
            local remaining = tokens
            local ttl       = refill_duration

            if tokens >= cost then
                allowed   = 1
                remaining = tokens - cost
                redis.call("HMSET", key, "tokens", remaining, "last_updated", last_updated)
                redis.call("EXPIRE", key, refill_duration * 2)
            else
                local missing = cost - tokens
                ttl = math.ceil((missing / refill_tokens) * refill_duration)
            end

            return { allowed, math.floor(remaining), ttl }
            """;

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<List<Long>> script;
    private final InMemoryTokenBucketRateLimiter fallbackRateLimiter;
    @Getter
    private final CircuitBreaker circuitBreaker;

    // ─── Configuration ───────────────────────────────────────────────────────
    @Getter
    @Value("${rate-limit.capacity:10}")
    private int capacity;

    @Value("${rate-limit.refill-tokens:10}")
    private int refillTokens;

    @Value("${rate-limit.refill-duration-seconds:60}")
    private int refillDurationSeconds;

    @Value("${rate-limit.cost-per-request:1}")
    private int costPerRequest;

    // ─── Metrics ─────────────────────────────────────────────────────────────
    private final Counter allowedCounter;
    private final Counter blockedCounter;
    private final Counter redisErrorCounter;
    private final Counter fallbackCounter;
    private final Timer   processingTimer;

    @Autowired
    @SuppressWarnings("unchecked")
    public TokenBucketRateLimiter(RedisTemplate<String, String> redisTemplate,
                                  MeterRegistry meterRegistry,
                                  InMemoryTokenBucketRateLimiter fallbackRateLimiter,
                                  @Autowired(required = false) CircuitBreakerRegistry circuitBreakerRegistry) {
        this.redisTemplate = redisTemplate;
        this.script = RedisScript.of(LUA_SCRIPT, (Class<List<Long>>) (Class<?>) List.class);
        this.fallbackRateLimiter = fallbackRateLimiter != null
                ? fallbackRateLimiter
                : new InMemoryTokenBucketRateLimiter(meterRegistry);
        this.circuitBreaker = circuitBreakerRegistry != null
                ? circuitBreakerRegistry.circuitBreaker("redisRateLimiter")
                : CircuitBreaker.ofDefaults("redisRateLimiter");

        this.allowedCounter    = Counter.builder("rate_limit_allowed_total")
                .description("Total requests allowed by rate limiter").register(meterRegistry);
        this.blockedCounter    = Counter.builder("rate_limit_blocked_total")
                .description("Total requests blocked by rate limiter").register(meterRegistry);
        this.redisErrorCounter = Counter.builder("rate_limit_redis_errors_total")
                .description("Total Redis errors during rate limit evaluation").register(meterRegistry);
        this.fallbackCounter   = Counter.builder("rate_limit_fallback_total")
                .description("Total requests falling back to in-memory rate limiter").register(meterRegistry);
        this.processingTimer   = Timer.builder("rate_limit_processing_time")
                .description("Rate limit evaluation latency").register(meterRegistry);
    }

    /**
     * Testing constructor with default in-memory fallback and standard CircuitBreaker.
     */
    public TokenBucketRateLimiter(RedisTemplate<String, String> redisTemplate,
                                  MeterRegistry meterRegistry) {
        this(redisTemplate, meterRegistry, new InMemoryTokenBucketRateLimiter(meterRegistry), null);
    }

    /**
     * Testing constructor with explicit fallback rate limiter.
     */
    public TokenBucketRateLimiter(RedisTemplate<String, String> redisTemplate,
                                  MeterRegistry meterRegistry,
                                  InMemoryTokenBucketRateLimiter fallbackRateLimiter) {
        this(redisTemplate, meterRegistry, fallbackRateLimiter, null);
    }

    @Override
    public RateLimitResult checkRateLimit(String clientKey) {
        return processingTimer.record(() -> {
            try {
                return circuitBreaker.executeSupplier(() -> executeRedis(clientKey));
            } catch (CallNotPermittedException e) {
                log.warn("Redis rate limiter circuit breaker is OPEN for key '{}'. Fast-failing to in-memory fallback.",
                        clientKey);
                return executeFallback(clientKey);
            } catch (Exception e) {
                log.error("Redis error during rate limit check for key '{}': {} — falling back to in-memory limiter.",
                        clientKey, e.getMessage());
                redisErrorCounter.increment();
                return executeFallback(clientKey);
            }
        });
    }

    private RateLimitResult executeRedis(String clientKey) {
        long nowSeconds = Instant.now().getEpochSecond();

        List<Long> result = redisTemplate.execute(
                script,
                List.of(clientKey),
                String.valueOf(capacity),
                String.valueOf(refillTokens),
                String.valueOf(refillDurationSeconds),
                String.valueOf(costPerRequest),
                String.valueOf(nowSeconds)
        );

        if (result == null || result.size() < 3) {
            log.warn("Unexpected Lua script result: {}. Falling back to in-memory rate limiter.", result);
            redisErrorCounter.increment();
            return executeFallback(clientKey);
        }

        boolean allowed        = result.get(0) == 1L;
        int remaining          = result.get(1).intValue();
        long resetOrRetry      = result.get(2);

        if (allowed) {
            allowedCounter.increment();
            return RateLimitResult.allowed(remaining, resetOrRetry);
        } else {
            blockedCounter.increment();
            return RateLimitResult.blocked(resetOrRetry);
        }
    }

    private RateLimitResult executeFallback(String clientKey) {
        fallbackCounter.increment();
        try {
            if (fallbackRateLimiter != null) {
                return fallbackRateLimiter.checkRateLimit(clientKey);
            }
        } catch (Exception e) {
            log.error("In-memory fallback rate limiter also failed for key '{}': {}. Failing open.",
                    clientKey, e.getMessage());
        }
        return RateLimitResult.failOpen();
    }

}
