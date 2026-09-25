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
    GrantEntity visible = read(id, actor);
    // The visibility check leaves an unlocked copy in the persistence context. If a competing
    // transaction commits while this one waits for the row lock, Hibernate finds the locked row
    // newer than that copy and throws an optimistic-locking failure instead of returning it.
    // Detaching the copy makes the locking query load the row fresh, under the lock.
    entityManager.detach(visible);
    jdbc.sql("SET LOCAL lock_timeout = '2s'").update();
    return grants.lockById(id).orElseThrow(GrantAccess::notFound);
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
