package edu.university.grantledger.persistence;

import edu.university.grantledger.domain.Actor;
import edu.university.grantledger.domain.Category;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "budget_allocation")
public class BudgetEntity {
  @Id private UUID id;
  private UUID grantId;

  @Enumerated(EnumType.STRING)
  private Category category;

  private BigDecimal allocatedAmount;
  private Instant createdAt;
  private Instant updatedAt;
  private String createdByIssuer;
  private String createdBySubject;
  private String updatedByIssuer;
  private String updatedBySubject;

  protected BudgetEntity() {}

  public BudgetEntity(UUID grantId, Category category, Actor actor, Instant now) {
    id = UUID.randomUUID();
    this.grantId = grantId;
    this.category = category;
    allocatedAmount = new BigDecimal("0.00");
    createdAt = now;
    updatedAt = now;
    createdByIssuer = actor.issuer();
    createdBySubject = actor.subject();
    updatedByIssuer = actor.issuer();
    updatedBySubject = actor.subject();
  }

  public void allocate(BigDecimal amount, Actor actor, Instant now) {
    allocatedAmount = amount;
    updatedAt = now;
    updatedByIssuer = actor.issuer();
    updatedBySubject = actor.subject();
  }

  public UUID getId() {
    return id;
  }

  public UUID getGrantId() {
    return grantId;
  }

  public Category getCategory() {
    return category;
  }

  public BigDecimal getAllocatedAmount() {
    return allocatedAmount;
  }
}
