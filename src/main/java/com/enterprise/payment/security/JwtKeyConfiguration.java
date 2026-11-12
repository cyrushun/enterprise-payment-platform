package com.enterprise.payment.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtKeyConfiguration {
  @Bean
  RSAPublicKey rsaPublicKey(@Value("${app.security.jwt.public-key-location}") Resource resource)
      throws Exception {
    try (var in = resource.getInputStream()) {
      var spec = new java.security.spec.X509EncodedKeySpec(pemBytes(in.readAllBytes()));
      return (RSAPublicKey) java.security.KeyFactory.getInstance("RSA").generatePublic(spec);
    }
  }

  @Bean
  RSAPrivateKey rsaPrivateKey(@Value("${app.security.jwt.private-key-location}") Resource resource)
      throws Exception {
    try (var in = resource.getInputStream()) {
      var spec = new java.security.spec.PKCS8EncodedKeySpec(pemBytes(in.readAllBytes()));
      return (RSAPrivateKey) java.security.KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
  }

  @Bean
  JwtDecoder jwtDecoder(
      RSAPublicKey key,
      @Value("${app.security.jwt.issuer}") String issuer,
      @Value("${app.security.jwt.audience}") String audience) {
    var decoder = NimbusJwtDecoder.withPublicKey(key).build();
    OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> validator =
        jwt -> {
          var standard = JwtValidators.createDefaultWithIssuer(issuer).validate(jwt);
          if (standard.hasErrors()) return standard;
          return jwt.getAudience().contains(audience)
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new org.springframework.security.oauth2.core.OAuth2Error(
                      "invalid_token", "Invalid audience", null));
        };
    decoder.setJwtValidator(validator);
    return decoder;
  }

  @Bean
  JwtEncoder jwtEncoder(RSAPublicKey pub, RSAPrivateKey priv) {
    JWK jwk = new RSAKey.Builder(pub).privateKey(priv).build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
  }

  private static byte[] pemBytes(byte[] bytes) {
    String pem =
        new String(bytes, java.nio.charset.StandardCharsets.US_ASCII)
            .replaceAll("-----BEGIN [A-Z ]+-----", "")
            .replaceAll("-----END [A-Z ]+-----", "")
            .replaceAll("\\s", "");
    return java.util.Base64.getDecoder().decode(pem);
  }
}
