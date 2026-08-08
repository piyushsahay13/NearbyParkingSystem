package com.wego.parkingsystem.ratelimit;

import static com.wego.parkingsystem.constants.ApplicationConstants.HEADER_X_FORWARDED_FOR;
import static com.wego.parkingsystem.constants.ApplicationConstants.HEADER_X_REAL_IP;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Default {@link ClientKeyResolver} implementation.
 * Extracts client IP from {@code X-Forwarded-For} header (proxy/load-balancer scenario)
 * or falls back to {@code request.getRemoteAddr()} (direct connection).
 */
@Component
public class ClientIpKeyResolver implements ClientKeyResolver {

    @Override
    public String resolveKey(HttpServletRequest request) {
        // Check X-Forwarded-For header first (set by load balancers / reverse proxies)
        String xForwardedFor = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For can be a comma-separated list; take the first (original client) IP
            return xForwardedFor.split(",")[0].trim();
        }

        // Check X-Real-IP header (set by nginx)
        String xRealIp = request.getHeader(HEADER_X_REAL_IP);
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        // Direct connection fallback
        return request.getRemoteAddr();
    }
}
