package com.wego.parkingsystem.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static com.wego.parkingsystem.constants.ApplicationConstants.HEADER_RATE_LIMIT;
import static com.wego.parkingsystem.constants.ApplicationConstants.HEADER_RATE_LIMIT_FALLBACK;
import static com.wego.parkingsystem.constants.ApplicationConstants.HEADER_RATE_LIMIT_REMAINING;
import static com.wego.parkingsystem.constants.ApplicationConstants.HEADER_RETRY_AFTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RateLimitFilter — Servlet Filter Tests")
class RateLimitFilterTest {

    private RateLimiter rateLimiter;
    private ClientKeyResolver clientKeyResolver;
    private RateLimitFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        rateLimiter = Mockito.mock(RateLimiter.class);
        clientKeyResolver = Mockito.mock(ClientKeyResolver.class);
        filterChain = Mockito.mock(FilterChain.class);

        when(rateLimiter.getCapacity()).thenReturn(10);
        when(clientKeyResolver.resolveKey(Mockito.any())).thenReturn("192.168.1.100");

        filter = new RateLimitFilter(rateLimiter, clientKeyResolver, new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(filter, "rateLimitEnabled", true);
        ReflectionTestUtils.setField(filter, "keyPrefix", "rate-limit:");
    }

    @Test
    @DisplayName("Allowed request continues filter chain and sets standard headers")
    void allowedRequestSetsHeadersAndContinuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/parking/lots/nearby");
        request.setServletPath("/api/v1/parking/lots/nearby");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.checkRateLimit(anyString()))
                .thenReturn(RateLimitResult.allowed(9, 60L));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getHeader(HEADER_RATE_LIMIT)).isEqualTo("10");
        assertThat(response.getHeader(HEADER_RATE_LIMIT_REMAINING)).isEqualTo("9");
        assertThat(response.getHeader(HEADER_RATE_LIMIT_FALLBACK)).isNull();
    }

    @Test
    @DisplayName("Fallback allowed request sets X-RateLimit-Fallback header and continues chain")
    void fallbackAllowedSetsFallbackHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/parking/lots/nearby");
        request.setServletPath("/api/v1/parking/lots/nearby");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.checkRateLimit(anyString()))
                .thenReturn(RateLimitResult.allowedFallback(9, 60L));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getHeader(HEADER_RATE_LIMIT)).isEqualTo("10");
        assertThat(response.getHeader(HEADER_RATE_LIMIT_REMAINING)).isEqualTo("9");
        assertThat(response.getHeader(HEADER_RATE_LIMIT_FALLBACK)).isEqualTo("true");
    }

    @Test
    @DisplayName("Blocked request returns 429 and does not continue chain")
    void blockedRequestReturns429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/parking/lots/nearby");
        request.setServletPath("/api/v1/parking/lots/nearby");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.checkRateLimit(anyString()))
                .thenReturn(RateLimitResult.blockedFallback(45L));

        filter.doFilter(request, response, filterChain);

        Mockito.verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(HEADER_RETRY_AFTER)).isEqualTo("45");
        assertThat(response.getHeader(HEADER_RATE_LIMIT_FALLBACK)).isEqualTo("true");
        assertThat(response.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }
}
