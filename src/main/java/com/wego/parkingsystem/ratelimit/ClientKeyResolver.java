package com.wego.parkingsystem.ratelimit;

/**
 * Strategy interface for resolving the rate limit key from an HTTP request.
 * Implementations: {@link ClientIpKeyResolver}, ApiKeyResolver, JwtSubjectKeyResolver.
 */
public interface ClientKeyResolver {

    /**
     * Resolves the client identifier key for rate limiting.
     *
     * @param request the current HTTP servlet request
     * @return rate limit key string (e.g. "192.168.10.25")
     */
    String resolveKey(jakarta.servlet.http.HttpServletRequest request);
}
