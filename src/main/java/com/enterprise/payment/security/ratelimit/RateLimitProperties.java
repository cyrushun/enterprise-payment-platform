package com.enterprise.payment.security.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
  private boolean enabled = true;
  private boolean failOpen = true;
  private boolean trustForwardedHeaders = false;
  private Rule login = new Rule(5, Duration.ofMinutes(1));
  private Rule registration = new Rule(5, Duration.ofHours(1));
  private Rule transfer = new Rule(10, Duration.ofMinutes(1));
  private Rule general = new Rule(100, Duration.ofMinutes(1));

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isFailOpen() {
    return failOpen;
  }

  public void setFailOpen(boolean failOpen) {
    this.failOpen = failOpen;
  }

  public boolean isTrustForwardedHeaders() {
    return trustForwardedHeaders;
  }

  public void setTrustForwardedHeaders(boolean trustForwardedHeaders) {
    this.trustForwardedHeaders = trustForwardedHeaders;
  }

  public Rule getLogin() {
    return login;
  }

  public void setLogin(Rule login) {
    this.login = login;
  }

  public Rule getRegistration() {
    return registration;
  }

  public void setRegistration(Rule registration) {
    this.registration = registration;
  }

  public Rule getTransfer() {
    return transfer;
  }

  public void setTransfer(Rule transfer) {
    this.transfer = transfer;
  }

  public Rule getGeneral() {
    return general;
  }

  public void setGeneral(Rule general) {
    this.general = general;
  }

  public static class Rule {
    private int limit;
    private Duration window;

    public Rule() {}

    public Rule(int limit, Duration window) {
      this.limit = limit;
      this.window = window;
    }

    public int getLimit() {
      return limit;
    }

    public void setLimit(int limit) {
      this.limit = limit;
    }

    public Duration getWindow() {
      return window;
    }

    public void setWindow(Duration window) {
      this.window = window;
    }
  }
}
