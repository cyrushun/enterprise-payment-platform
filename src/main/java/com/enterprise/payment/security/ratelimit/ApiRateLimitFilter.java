package com.enterprise.payment.security.ratelimit;

import com.enterprise.payment.shared.error.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiRateLimitFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(ApiRateLimitFilter.class);
  private final RateLimitStore store;
  private final RateLimitProperties properties;
  private final ObjectMapper objectMapper;
  private final io.micrometer.core.instrument.MeterRegistry metrics;

  public ApiRateLimitFilter(
      RateLimitStore store,
      RateLimitProperties properties,
      ObjectMapper objectMapper,
      io.micrometer.core.instrument.MeterRegistry metrics) {
    this.store = store;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.metrics = metrics;
  }

  public ApiRateLimitFilter(
      RateLimitStore store, RateLimitProperties properties, ObjectMapper objectMapper) {
    this(
        store,
        properties,
        objectMapper,
        new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = path(request);
    return !properties.isEnabled()
        || !path.startsWith("/api/v1/")
        || "OPTIONS".equalsIgnoreCase(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = path(request);
    RateLimitProperties.Rule rule;
    String bucket;
    String identity;

    if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/v1/auth/login".equals(path)) {
      rule = properties.getLogin();
      bucket = "login-ip";
      identity = clientIp(request);
    } else if ("POST".equalsIgnoreCase(request.getMethod())
        && "/api/v1/auth/register".equals(path)) {
      rule = properties.getRegistration();
      bucket = "register-ip";
      identity = clientIp(request);
    } else if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/v1/transfers".equals(path)) {
      rule = properties.getTransfer();
      bucket = "transfer-customer";
      identity = authenticatedIdentity(request);
    } else {
      rule = properties.getGeneral();
      bucket = "api-principal";
      identity = authenticatedIdentityOrIp(request);
    }

    String key = "payment:ratelimit:v1:" + bucket + ":" + sha256(identity);
    long storeStarted = System.nanoTime();
    try {
      RateLimitDecision decision = store.consume(key, rule.getWindow());
      metrics
          .timer("payment.rate_limit.store.duration", "outcome", "success", "bucket", bucket)
          .record(System.nanoTime() - storeStarted, java.util.concurrent.TimeUnit.NANOSECONDS);
      long remaining = Math.max(0L, rule.getLimit() - decision.count());
      response.setHeader("X-RateLimit-Limit", Integer.toString(rule.getLimit()));
      response.setHeader("X-RateLimit-Remaining", Long.toString(remaining));
      response.setHeader("X-RateLimit-Reset", Long.toString(decision.resetEpochSeconds()));
      if (!decision.allowed(rule.getLimit())) {
        metrics.counter("payment.rate_limit.rejected", "bucket", bucket).increment();
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
            response.getOutputStream(),
            new ApiError(
                "RATE_LIMIT_EXCEEDED",
                "Too many requests. Please retry after the indicated interval.",
                Instant.now(),
                java.util.List.of()));
        return;
      }
    } catch (RuntimeException ex) {
      metrics
          .timer("payment.rate_limit.store.duration", "outcome", "failure", "bucket", bucket)
          .record(System.nanoTime() - storeStarted, java.util.concurrent.TimeUnit.NANOSECONDS);
      log.warn(
          "Rate-limit store unavailable; applying configured fail-{} policy",
          properties.isFailOpen() ? "open" : "closed");
      if (!properties.isFailOpen()) {
        response.setStatus(503);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
            response.getOutputStream(),
            new ApiError(
                "DEPENDENCY_UNAVAILABLE",
                "Request protection is temporarily unavailable.",
                Instant.now(),
                java.util.List.of()));
        return;
      }
      response.setHeader("X-RateLimit-Status", "degraded");
      metrics
          .counter(
              "payment.rate_limit.degraded",
              "policy",
              properties.isFailOpen() ? "fail_open" : "fail_closed")
          .increment();
    }
    chain.doFilter(request, response);
  }

  private String authenticatedIdentity(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated())
      return "anonymous:" + clientIp(request);
    if (authentication instanceof JwtAuthenticationToken jwt) {
      String customerId = jwt.getToken().getClaimAsString("customer_id");
      if (customerId != null && !customerId.isBlank()) return "customer:" + customerId;
    }
    return "principal:" + authentication.getName();
  }

  private String authenticatedIdentityOrIp(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || "anonymousUser".equals(authentication.getPrincipal())) {
      return "ip:" + clientIp(request);
    }
    return authenticatedIdentity(request);
  }

  private String clientIp(HttpServletRequest request) {
    if (properties.isTrustForwardedHeaders()) {
      String forwarded = request.getHeader("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
  }

  private String path(HttpServletRequest request) {
    String context = request.getContextPath();
    String uri = request.getRequestURI();
    return context == null || context.isEmpty() ? uri : uri.substring(context.length());
  }

  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }
}
