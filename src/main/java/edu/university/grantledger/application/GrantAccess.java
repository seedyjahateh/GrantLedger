package edu.university.grantledger.application;

import edu.university.grantledger.domain.Actor;
import edu.university.grantledger.domain.DomainException;
import edu.university.grantledger.persistence.GrantEntity;
import edu.university.grantledger.persistence.GrantRepository;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class GrantAccess {
  private final GrantRepository grants;
  private final JdbcClient jdbc;
  private final jakarta.persistence.EntityManager entityManager;

  public GrantAccess(
      GrantRepository grants, JdbcClient jdbc, jakarta.persistence.EntityManager entityManager) {
    this.grants = grants;
    this.jdbc = jdbc;
    this.entityManager = entityManager;
  }

  public GrantEntity read(UUID id, Actor actor) {
    actor.requireReader();
    GrantEntity grant = grants.findById(id).orElseThrow(GrantAccess::notFound);
    if (!actor.departments().contains(grant.getDepartmentId())) throw notFound();
    return grant;
  }

  public GrantEntity lock(UUID id, Actor actor) {
    actor.requireAdmin();
    // Check visibility before locking an identifier supplied by the caller.
    read(id, actor);
    jdbc.sql("SET LOCAL lock_timeout = '2s'").update();
    var locked = grants.lockById(id).orElseThrow(GrantAccess::notFound);
    // The visibility check may have populated the persistence context before a competing commit.
    entityManager.refresh(locked);
    return locked;
  }

  public void match(GrantEntity grant, String etag) {
    if (etag == null || etag.isBlank())
      throw new DomainException(428, "PRECONDITION_REQUIRED", "If-Match is required.");
    if (!etag.equals("\"" + grant.getVersion() + "\""))
      throw new DomainException(
          412, "PRECONDITION_FAILED", "The grant changed. Reload it and retry.");
  }

  public static DomainException notFound() {
    return new DomainException(404, "NOT_FOUND", "Resource not found.");
  }
}
