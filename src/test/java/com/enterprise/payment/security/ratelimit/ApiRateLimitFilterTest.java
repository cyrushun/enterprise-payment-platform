package com.enterprise.payment.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiRateLimitFilterTest {
  private RateLimitStore store;
  private RateLimitProperties properties;
  private ApiRateLimitFilter filter;
  private FilterChain chain;

  @BeforeEach
  void setUp() {
    store = mock(RateLimitStore.class);
    properties = new RateLimitProperties();
    filter =
        new ApiRateLimitFilter(
            store, properties, new ObjectMapper().registerModule(new JavaTimeModule()));
    chain = mock(FilterChain.class);
  }

  @Test
  void allowsRequestAndWritesRateLimitHeaders() throws Exception {
    when(store.consume(anyString(), any(Duration.class)))
        .thenReturn(new RateLimitDecision(1, 0, 60, 2_000_000_000L));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
    request.setRemoteAddr("192.0.2.10");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("100");
    assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("99");
    verify(chain).doFilter(request, response);
  }

  @Test
  void rejectsRequestWhenLimitExceeded() throws Exception {
    when(store.consume(anyString(), any(Duration.class)))
        .thenReturn(new RateLimitDecision(6, 0, 42, 2_000_000_000L));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    request.setRemoteAddr("192.0.2.10");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getContentType()).contains("application/json");
    assertThat(response.getHeader("Retry-After")).isEqualTo("42");
    assertThat(response.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void failsOpenWhenRedisIsUnavailableByDefault() throws Exception {
    when(store.consume(anyString(), any(Duration.class)))
        .thenThrow(new IllegalStateException("redis down"));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
    request.setRemoteAddr("192.0.2.10");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getHeader("X-RateLimit-Status")).isEqualTo("degraded");
    verify(chain).doFilter(request, response);
  }

  @Test
  void failsClosedWhenConfigured() throws Exception {
    properties.setFailOpen(false);
    when(store.consume(anyString(), any(Duration.class)))
        .thenThrow(new IllegalStateException("redis down"));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
    request.setRemoteAddr("192.0.2.10");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getContentType()).contains("application/json");
    assertThat(response.getContentAsString()).contains("DEPENDENCY_UNAVAILABLE");
    verify(chain, never()).doFilter(any(), any());
  }
}
