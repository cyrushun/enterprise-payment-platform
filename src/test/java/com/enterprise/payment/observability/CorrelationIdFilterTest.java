package com.enterprise.payment.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @AfterEach
  void cleanMdc() {
    MDC.clear();
  }

  @Test
  void acceptsValidCorrelationIdAndReturnsIt() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    request.addHeader("X-Correlation-ID", "payment-request-123");
    FilterChain chain =
        (req, res) -> assertThat(MDC.get("correlationId")).isEqualTo("payment-request-123");

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("payment-request-123");
    assertThat(MDC.get("correlationId")).isNull();
  }

  @Test
  void generatesCorrelationIdWhenMissingOrInvalid() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    request.addHeader("X-Correlation-ID", "bad value with spaces");

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader("X-Correlation-ID")).matches("[0-9a-f-]{36}");
    assertThat(MDC.get("correlationId")).isNull();
  }
}
