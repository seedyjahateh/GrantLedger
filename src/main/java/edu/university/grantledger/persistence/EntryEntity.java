package edu.university.grantledger.persistence;

import edu.university.grantledger.domain.Actor;
import edu.university.grantledger.domain.EntryType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "ledger_entry")
public class EntryEntity {
  @Id private UUID id;
  private UUID grantId;
  private UUID budgetAllocationId;

  @Enumerated(EnumType.STRING)
  private EntryType type;

  private BigDecimal amount;
  private LocalDate effectiveDate;
  private String externalReference;
  private String description;
  private UUID originalEntryId;
  private Instant createdAt;
  private String createdByIssuer;
  private String createdBySubject;

  protected EntryEntity() {}

  public EntryEntity(
      UUID grantId,
      UUID budgetId,
      EntryType type,
      BigDecimal amount,
      LocalDate effectiveDate,
      String externalReference,
      String description,
      UUID originalId,
      Actor actor,
      Instant now) {
    id = UUID.randomUUID();
    this.grantId = grantId;
    budgetAllocationId = budgetId;
    this.type = type;
    this.amount = amount;
    this.effectiveDate = effectiveDate;
    this.externalReference = externalReference;
    this.description = description;
    originalEntryId = originalId;
    createdAt = now;
    createdByIssuer = actor.issuer();
    createdBySubject = actor.subject();
  }

  public UUID getId() {
    return id;
  }

  public UUID getGrantId() {
    return grantId;
  }

  public UUID getBudgetAllocationId() {
    return budgetAllocationId;
  }

  public EntryType getType() {
    return type;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public LocalDate getEffectiveDate() {
    return effectiveDate;
  }

  public String getExternalReference() {
    return externalReference;
  }

  public String getDescription() {
    return description;
  }

  public UUID getOriginalEntryId() {
    return originalEntryId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
