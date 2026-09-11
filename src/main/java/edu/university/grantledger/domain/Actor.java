package edu.university.grantledger.domain;

import java.util.Set;
import java.util.UUID;

public record Actor(String issuer, String subject, Set<String> roles, Set<UUID> departments) {
  public Actor {
    roles = Set.copyOf(roles);
    departments = Set.copyOf(departments);
  }

  public boolean admin() {
    return roles.contains("RESEARCH_ADMIN");
  }

  public boolean auditor() {
    return admin() || roles.contains("AUDITOR");
  }

  public void requireReader() {
    if (!(admin() || auditor() || roles.contains("VIEWER"))) forbidden();
  }

  public void requireAdmin() {
    if (!admin()) forbidden();
  }

  public void requireAuditor() {
    if (!auditor()) forbidden();
  }

  private static void forbidden() {
    throw new DomainException(403, "FORBIDDEN", "Your role does not permit this operation.");
  }
}
