package edu.university.grantledger.config;

import edu.university.grantledger.domain.Actor;
import edu.university.grantledger.domain.DomainException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class ActorResolver {
  private final String rolesClaim;
  private final String departmentsClaim;

  public ActorResolver(
      @Value("${grantledger.roles-claim}") String rolesClaim,
      @Value("${grantledger.departments-claim}") String departmentsClaim) {
    this.rolesClaim = rolesClaim;
    this.departmentsClaim = departmentsClaim;
  }

  public Actor resolve(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated())
      throw new DomainException(401, "UNAUTHENTICATED", "Authentication is required.");
    Map<String, Object> claims;
    if (authentication.getPrincipal() instanceof Jwt jwt) claims = jwt.getClaims();
    else if (authentication.getPrincipal() instanceof OidcUser user) claims = user.getClaims();
    else
      throw new DomainException(
          401, "UNAUTHENTICATED", "Supported institutional authentication is required.");
    try {
      String issuer = claims.get("iss").toString();
      String subject = (String) claims.get("sub");
      if (subject == null || subject.isBlank() || subject.length() > 255 || issuer.length() > 512)
        throw new IllegalArgumentException();
      Set<String> roles = strings(claims.get(rolesClaim));
      Set<UUID> departments =
          strings(claims.get(departmentsClaim)).stream()
              .map(UUID::fromString)
              .collect(Collectors.toUnmodifiableSet());
      return new Actor(issuer, subject, roles, departments);
    } catch (IllegalArgumentException | NullPointerException | ClassCastException error) {
      return new Actor("unmapped", "unmapped", Set.of(), Set.of());
    }
  }

  private Set<String> strings(Object value) {
    if (!(value instanceof Collection<?> collection)) return Set.of();
    if (collection.stream().anyMatch(item -> !(item instanceof String))) return Set.of();
    return collection.stream().map(String.class::cast).collect(Collectors.toUnmodifiableSet());
  }
}
