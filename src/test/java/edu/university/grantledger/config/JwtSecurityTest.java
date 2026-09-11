package edu.university.grantledger.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtSecurityTest {
  @Test
  void validatesSignedTokensWithoutRelyingOnMockAuthentication() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    var key =
        new RSAKey.Builder((RSAPublicKey) pair.getPublic())
            .privateKey((RSAPrivateKey) pair.getPrivate())
            .keyID("test-key")
            .build();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/keys",
        exchange -> {
          byte[] bytes =
              new JWKSet(key.toPublicJWK())
                  .toString()
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          try (var body = exchange.getResponseBody()) {
            body.write(bytes);
          }
        });
    server.start();
    try {
      var decoder =
          new SecurityConfig()
              .jwtDecoder(
                  "https://issuer.test",
                  "http://127.0.0.1:" + server.getAddress().getPort() + "/keys",
                  "grantledger-api");
      var now = Instant.now();
      assertThat(
              decoder
                  .decode(
                      token(
                          key,
                          "https://issuer.test",
                          "grantledger-api",
                          now.minusSeconds(1),
                          now.plusSeconds(299)))
                  .getSubject())
          .isEqualTo("subject");
      for (String invalid :
          List.of(
              token(key, "https://wrong.test", "grantledger-api", now, now.plusSeconds(300)),
              token(key, "https://issuer.test", "wrong", now, now.plusSeconds(300)),
              token(
                  key,
                  "https://issuer.test",
                  "grantledger-api",
                  now.minusSeconds(900),
                  now.minusSeconds(300)),
              token(key, "https://issuer.test", "grantledger-api", now, now.plusSeconds(3600)),
              token(
                  key,
                  "https://issuer.test",
                  "grantledger-api",
                  now.plusSeconds(120),
                  now.plusSeconds(420)))) {
        assertThatThrownBy(() -> decoder.decode(invalid)).isInstanceOf(JwtException.class);
      }
      var other = generator.generateKeyPair();
      var forged =
          new RSAKey.Builder((RSAPublicKey) other.getPublic())
              .privateKey((RSAPrivateKey) other.getPrivate())
              .keyID("test-key")
              .build();
      assertThatThrownBy(
              () ->
                  decoder.decode(
                      token(
                          forged,
                          "https://issuer.test",
                          "grantledger-api",
                          now,
                          now.plusSeconds(300))))
          .isInstanceOf(JwtException.class);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void mapsOnlyWellFormedClaims() {
    var resolver = new ActorResolver("roles", "department_ids");
    var token =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .issuer("https://issuer.test")
            .subject("subject")
            .claim("roles", List.of("RESEARCH_ADMIN"))
            .claim("department_ids", List.of("10000000-0000-0000-0000-000000000001"))
            .build();
    assertThat(resolver.resolve(new JwtAuthenticationToken(token, List.of())).admin()).isTrue();
    assertThatThrownBy(() -> resolver.resolve(null))
        .isInstanceOf(edu.university.grantledger.domain.DomainException.class);
    for (Object claim : List.of("wrong-shape", List.of(1), List.of("invalid-uuid"))) {
      var malformed =
          org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
              .header("alg", "RS256")
              .issuer("https://issuer.test")
              .subject("subject")
              .claim("roles", List.of("VIEWER"))
              .claim("department_ids", claim)
              .build();
      assertThat(resolver.resolve(new JwtAuthenticationToken(malformed, List.of())).departments())
          .isEmpty();
    }
  }

  private String token(RSAKey key, String issuer, String audience, Instant issued, Instant expires)
      throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject("subject")
            .issueTime(Date.from(issued))
            .expirationTime(Date.from(expires))
            .build();
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(key.getKeyID())
                .build(),
            claims);
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }
}
