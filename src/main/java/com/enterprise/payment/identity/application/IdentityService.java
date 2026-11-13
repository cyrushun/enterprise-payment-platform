package com.enterprise.payment.identity.application;

import com.enterprise.payment.audit.application.AuditService;
import com.enterprise.payment.customer.infrastructure.CustomerEntity;
import com.enterprise.payment.customer.infrastructure.CustomerRepository;
import com.enterprise.payment.identity.api.AuthController;
import com.enterprise.payment.identity.infrastructure.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentityService {
  private final UserRepository users;
  private final RoleRepository roles;
  private final RefreshTokenRepository refreshTokens;
  private final CustomerRepository customers;
  private final UserRoleRepository userRoles;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;
  private final PasswordEncoder encoder;
  private final JwtEncoder jwtEncoder;
  private final long accessTtl, refreshTtl;
  private final String issuer, audience;

  public IdentityService(
      UserRepository users,
      RoleRepository roles,
      RefreshTokenRepository refreshTokens,
      CustomerRepository customers,
      UserRoleRepository userRoles,
      PasswordEncoder encoder,
      JwtEncoder jwtEncoder,
      AuditService auditService,
      ObjectMapper objectMapper,
      @Value("${app.security.jwt.access-token-ttl-seconds:900}") long accessTtl,
      @Value("${app.security.jwt.refresh-token-ttl-seconds:604800}") long refreshTtl,
      @Value("${app.security.jwt.issuer}") String issuer,
      @Value("${app.security.jwt.audience}") String audience) {
    this.users = users;
    this.roles = roles;
    this.refreshTokens = refreshTokens;
    this.customers = customers;
    this.userRoles = userRoles;
    this.encoder = encoder;
    this.jwtEncoder = jwtEncoder;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.accessTtl = accessTtl;
    this.refreshTtl = refreshTtl;
    this.issuer = issuer;
    this.audience = audience;
  }

  @Transactional
  public AuthController.TokenResponse register(AuthController.RegisterRequest r) {
    String email = normalize(r.email());
    if (users.findByEmailIgnoreCase(email).isPresent())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
    CustomerEntity c =
        new CustomerEntity(r.firstName().trim(), r.lastName().trim(), email, r.phoneNumber());
    customers.save(c);
    UserEntity u = users.save(new UserEntity(email, encoder.encode(r.password()), c.getId()));
    // The only public registration role is CUSTOMER; roles are seeded by V4.
    RoleEntity customerRole =
        roles
            .findByName("CUSTOMER")
            .orElseThrow(() -> new IllegalStateException("CUSTOMER role missing"));
    userRoles.save(new UserRoleEntity(u.getId(), customerRole.getId()));
    auditService.record(
        u.getId(),
        "AUTH_REGISTER",
        "USER",
        u.getId(),
        null,
        objectMapper.valueToTree(Map.of("email", email)));
    return issue(u, List.of("CUSTOMER"), UUID.randomUUID());
  }

  @Transactional
  public AuthController.TokenResponse login(AuthController.LoginRequest r) {
    UserEntity u =
        users
            .findByEmailIgnoreCase(normalize(r.email()))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    if (!"ACTIVE".equals(u.getStatus()) || !encoder.matches(r.password(), u.getPasswordHash()))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    List<String> assigned = userRoles.findRoleNames(u.getId());
    if (assigned.isEmpty())
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User has no assigned role");
    auditService.record(
        u.getId(),
        "AUTH_LOGIN",
        "USER",
        u.getId(),
        null,
        objectMapper.valueToTree(Map.of("email", u.getEmail())));
    return issue(u, assigned, UUID.randomUUID());
  }

  private AuthController.TokenResponse issue(UserEntity user, List<String> userRoles, UUID family) {
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .audience(List.of(audience))
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(accessTtl))
            .claim("roles", userRoles)
            .claim(
                "customer_id",
                user.getCustomerId() == null ? null : user.getCustomerId().toString())
            .build();
    String access =
        jwtEncoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(
                            org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                        .build(),
                    claims))
            .getTokenValue();
    String refresh = UUID.randomUUID() + "." + UUID.randomUUID() + "." + UUID.randomUUID();
    refreshTokens.save(
        new RefreshTokenEntity(user.getId(), hash(refresh), family, now.plusSeconds(refreshTtl)));
    return new AuthController.TokenResponse(access, refresh, accessTtl);
  }

  @Transactional
  public AuthController.TokenResponse refresh(String raw) {
    var old =
        refreshTokens
            .findByTokenHash(hash(raw))
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
    if (old.getRevokedAt() != null) {
      refreshTokens.findByFamilyId(old.getFamilyId()).forEach(t -> t.revoke(null));
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Refresh token reuse detected; token family revoked");
    }
    if (old.getExpiresAt().isBefore(Instant.now()))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
    UserEntity u =
        users
            .findById(old.getUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    old.revoke(null);
    auditService.record(u.getId(), "AUTH_REFRESH", "USER", u.getId(), null, null);
    return issue(u, userRoles.findRoleNames(u.getId()), old.getFamilyId());
  }

  @Transactional
  public void logout(String raw) {
    refreshTokens
        .findByTokenHash(hash(raw))
        .ifPresent(
            t -> {
              t.revoke(null);
              auditService.record(t.getUserId(), "AUTH_LOGOUT", "USER", t.getUserId(), null, null);
            });
  }

  public AuthController.MeResponse me(Jwt jwt) {
    UUID id = UUID.fromString(jwt.getSubject());
    UserEntity u =
        users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    return new AuthController.MeResponse(
        id,
        u.getCustomerId(),
        u.getEmail(),
        jwt.getClaimAsStringList("roles") == null ? List.of() : jwt.getClaimAsStringList("roles"));
  }

  private static String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
