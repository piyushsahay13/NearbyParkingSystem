package com.wego.parkingsystem.ratelimit;

import static com.wego.parkingsystem.constants.ApplicationConstants.HEADER_RATE_LIMIT;
import static com.wego.parkingsystem.constants.ApplicationConstants.HEADER_RATE_LIMIT_REMAINING;
import static com.wego.parkingsystem.constants.ApplicationConstants.HEADER_RATE_LIMIT_RESET;
import static com.wego.parkingsystem.constants.ApplicationConstants.HEADER_RETRY_AFTER;
import static com.wego.parkingsystem.constants.ApplicationConstants.NEARBY_CARPARKS_PATH;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wego.parkingsystem.dto.ApiError;
import com.wego.parkingsystem.dto.ApiResponse;
import com.wego.parkingsystem.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that enforces Token Bucket rate limiting on
 * {@code GET /api/v1/carparks/nearby} before reaching the controller.
 *
 * <p>On each request:
 * <ol>
 *   <li>Resolves client IP via {@link ClientKeyResolver}.</li>
 *   <li>Calls {@link RateLimiter#checkRateLimit(String)} with prefixed key.</li>
 *   <li>If allowed: injects X-RateLimit-* headers and continues filter chain.</li>
 *   <li>If blocked: writes HTTP 429 JSON response and short-circuits the chain.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final ClientKeyResolver clientKeyResolver;
    private final TokenBucketRateLimiter tokenBucketRateLimiter;
    private final ObjectMapper objectMapper;

    @Value("${rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${rate-limit.key-prefix:rate-limit:}")
    private String keyPrefix;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only apply rate limiting to the target endpoint
        return !NEARBY_CARPARKS_PATH.equals(request.getServletPath())
                || !rateLimitEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = clientKeyResolver.resolveKey(request);
        String redisKey = keyPrefix + clientIp;

        RateLimitResult result = rateLimiter.checkRateLimit(redisKey);

        int capacity = tokenBucketRateLimiter.getCapacity();

        // Always inject X-RateLimit-Limit
        response.setHeader(HEADER_RATE_LIMIT, String.valueOf(capacity));

        if (result.isAllowed()) {
            // Inject allowed headers
            if (result.getRemaining() >= 0) {
                response.setHeader(HEADER_RATE_LIMIT_REMAINING, String.valueOf(result.getRemaining()));
                response.setHeader(HEADER_RATE_LIMIT_RESET, String.valueOf(result.getResetSeconds()));
            }

            if (result.isFailOpen()) {
                log.debug("Rate limit check failed open for client: {} — passing through.", clientIp);
            } else {
                log.debug("Rate limit allowed for client: {} — remaining: {}", clientIp, result.getRemaining());
            }

            filterChain.doFilter(request, response);

        } else {
            // Blocked: inject Retry-After and return 429
            response.setHeader(HEADER_RATE_LIMIT_REMAINING, "0");
            response.setHeader(HEADER_RATE_LIMIT_RESET, String.valueOf(result.getResetSeconds()));
            response.setHeader(HEADER_RETRY_AFTER,  String.valueOf(result.getResetSeconds()));

            log.warn("Rate limit BLOCKED for client: {} — retry after {}s", clientIp, result.getResetSeconds());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            ApiResponse<Void> errorResponse = ApiResponse.error(
                    ApiError.builder()
                            .status(429)
                            .code(ErrorCode.CP_429_001.getCode())
                            .type("RATE_LIMIT_EXCEEDED")
                            .message(ErrorCode.CP_429_001.getDefaultMessage())
                            .build(),
                    traceId
            );

            objectMapper.writeValue(response.getWriter(), errorResponse);
        }
    }
}
