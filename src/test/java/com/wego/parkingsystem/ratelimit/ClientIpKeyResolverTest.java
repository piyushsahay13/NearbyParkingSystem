package com.wego.parkingsystem.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ClientIpKeyResolver}.
 * Verifies correct client IP extraction from various HTTP headers.
 */
@DisplayName("ClientIpKeyResolver — IP Extraction Tests")
class ClientIpKeyResolverTest {

    private ClientIpKeyResolver resolver;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        resolver = new ClientIpKeyResolver();
        request  = Mockito.mock(HttpServletRequest.class);
    }

    @Test
    @DisplayName("Should extract first IP from X-Forwarded-For header")
    void shouldExtractFromXForwardedFor() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.10.25, 10.0.0.1, 172.16.0.1");
        when(request.getHeader("X-Real-IP")).thenReturn(null);

        String key = resolver.resolveKey(request);

        assertThat(key).isEqualTo("192.168.10.25");
    }

    @Test
    @DisplayName("Should extract IP from X-Real-IP when X-Forwarded-For is absent")
    void shouldExtractFromXRealIp() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("10.0.5.100");

        String key = resolver.resolveKey(request);

        assertThat(key).isEqualTo("10.0.5.100");
    }

    @Test
    @DisplayName("Should fall back to RemoteAddr when no proxy headers present")
    void shouldFallBackToRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        String key = resolver.resolveKey(request);

        assertThat(key).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("Should trim whitespace from X-Forwarded-For IP")
    void shouldTrimWhitespaceFromForwardedFor() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("  203.0.113.42  ");
        when(request.getHeader("X-Real-IP")).thenReturn(null);

        String key = resolver.resolveKey(request);

        assertThat(key).isEqualTo("203.0.113.42");
    }
}
