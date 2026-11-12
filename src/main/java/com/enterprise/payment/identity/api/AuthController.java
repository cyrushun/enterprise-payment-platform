package com.enterprise.payment.identity.api;

import com.enterprise.payment.identity.application.IdentityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final IdentityService identity;

  public AuthController(IdentityService identity) {
    this.identity = identity;
  }

  public record RegisterRequest(
      @NotBlank @Size(max = 100) String firstName,
      @NotBlank @Size(max = 100) String lastName,
      @Email @NotBlank String email,
      @NotBlank @Size(min = 12, max = 72) String password,
      String phoneNumber) {}

  public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

  public record RefreshRequest(@NotBlank String refreshToken) {}

  public record TokenResponse(
      String accessToken, String refreshToken, long expiresIn, String tokenType) {
    public TokenResponse(String a, String r, long e) {
      this(a, r, e, "Bearer");
    }
  }

  public record MeResponse(UUID userId, UUID customerId, String email) {}

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public TokenResponse register(@Valid @RequestBody RegisterRequest r) {
    return identity.register(r);
  }

  @PostMapping("/login")
  public TokenResponse login(@Valid @RequestBody LoginRequest r) {
    return identity.login(r);
  }

  @PostMapping("/refresh")
  public TokenResponse refresh(@Valid @RequestBody RefreshRequest r) {
    return identity.refresh(r.refreshToken());
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@Valid @RequestBody RefreshRequest r) {
    identity.logout(r.refreshToken());
  }

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
    return identity.me(jwt);
  }
}
