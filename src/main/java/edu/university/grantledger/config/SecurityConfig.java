package edu.university.grantledger.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
      clientRegistrations(
          @Value("${spring.security.oauth2.client.registration.institution.client-id}")
              String clientId,
          @Value("${spring.security.oauth2.client.registration.institution.client-secret}")
              String secret,
          @Value("${spring.security.oauth2.client.provider.institution.issuer-uri}") String issuer,
          @Value("${spring.security.oauth2.client.provider.institution.authorization-uri}")
              String authorize,
          @Value("${spring.security.oauth2.client.provider.institution.token-uri}") String token,
          @Value("${spring.security.oauth2.client.provider.institution.jwk-set-uri}") String keys) {
    var registration =
        org.springframework.security.oauth2.client.registration.ClientRegistration
            .withRegistrationId("institution")
            .clientId(clientId)
            .clientSecret(secret)
            .authorizationGrantType(
                org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid")
            .issuerUri(issuer)
            .authorizationUri(authorize)
            .tokenUri(token)
            .jwkSetUri(keys)
            .userNameAttributeName("sub")
            .clientName("Institutional SSO")
            .build();
    return new org.springframework.security.oauth2.client.registration
        .InMemoryClientRegistrationRepository(registration);
  }

  @Bean
  Clock clock(@Value("${grantledger.timezone}") String timezone) {
    return Clock.system(ZoneId.of(timezone));
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String keys,
      @Value("${grantledger.audience}") String audience) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(keys)
            .jwsAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
            .build();
    OAuth2TokenValidator<Jwt> audienceAndLifetime =
        jwt -> {
          boolean valid =
              jwt.getAudience().contains(audience)
                  && jwt.getIssuedAt() != null
                  && jwt.getExpiresAt() != null
                  && Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())
                          .compareTo(Duration.ofMinutes(10))
                      <= 0
                  && jwt.getIssuedAt().isBefore(jwt.getExpiresAt())
                  && jwt.getIssuedAt().isBefore(java.time.Instant.now().plusSeconds(60));
          return valid
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new OAuth2Error("invalid_token", "Invalid token audience or lifetime", null));
        };
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), audienceAndLifetime));
    return decoder;
  }

  @Bean
  @Order(1)
  SecurityFilterChain management(HttpSecurity http) throws Exception {
    return http.securityMatcher(EndpointRequest.toAnyEndpoint())
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain api(HttpSecurity http, ObjectMapper json) throws Exception {
    var errors = new SecurityProblems(json);
    return http.securityMatcher("/api/**")
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .jwt(Customizer.withDefaults())
                    .authenticationEntryPoint(errors)
                    .accessDeniedHandler(errors))
        .exceptionHandling(
            handler -> handler.authenticationEntryPoint(errors).accessDeniedHandler(errors))
        .headers(
            headers ->
                headers.contentSecurityPolicy(
                    csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
        .build();
  }

  @Bean
  @Order(3)
  SecurityFilterChain browser(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/assets/**", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2Login(Customizer.withDefaults())
        .addFilterBefore(new SessionAgeFilter(), AnonymousAuthenticationFilter.class)
        .headers(
            headers ->
                headers.contentSecurityPolicy(
                    csp ->
                        csp.policyDirectives(
                            "default-src 'self'; style-src 'self'; script-src 'self'; frame-ancestors 'none'; form-action 'self'")))
        .logout(logout -> logout.logoutSuccessUrl("/"))
        .build();
  }
}
