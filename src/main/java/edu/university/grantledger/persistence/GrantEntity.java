package edu.university.grantledger.persistence;

import edu.university.grantledger.domain.Actor;
import edu.university.grantledger.domain.GrantStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "grant_record")
public class GrantEntity {
  @Id private UUID id;
  private String awardNumber;
  private UUID departmentId;
  private String title;
  private String sponsorName;
  private LocalDate startDate;
  private LocalDate endDate;
  private BigDecimal awardAmount;
  private String currency;

  @Enumerated(EnumType.STRING)
  private GrantStatus status;

  @Version private long version;
  private Instant createdAt;
  private String createdByIssuer;
  private String createdBySubject;
  private Instant updatedAt;
  private String updatedByIssuer;
  private String updatedBySubject;

  protected GrantEntity() {}

  public GrantEntity(
      String awardNumber,
      UUID departmentId,
      String title,
      String sponsorName,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal awardAmount,
      Actor actor,
      Instant now) {
    this.id = UUID.randomUUID();
    this.awardNumber = awardNumber;
    this.departmentId = departmentId;
    this.title = title;
    this.sponsorName = sponsorName;
    this.startDate = startDate;
    this.endDate = endDate;
    this.awardAmount = awardAmount;
    this.currency = "USD";
    this.status = GrantStatus.DRAFT;
    this.createdAt = now;
    this.createdByIssuer = actor.issuer();
    this.createdBySubject = actor.subject();
    touch(actor, now);
  }

  public void touch(Actor actor, Instant now) {
    updatedAt = updatedAt != null && !now.isAfter(updatedAt) ? updatedAt.plusNanos(1000) : now;
    updatedByIssuer = actor.issuer();
    updatedBySubject = actor.subject();
  }

  public void metadata(
      String awardNumber,
      String title,
      String sponsorName,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal awardAmount) {
    this.awardNumber = awardNumber;
    this.title = title;
    this.sponsorName = sponsorName;
    this.startDate = startDate;
    this.endDate = endDate;
    this.awardAmount = awardAmount;
  }

  public void transition(GrantStatus status) {
    this.status = status;
  }

  public UUID getId() {
    return id;
  }

  public String getAwardNumber() {
    return awardNumber;
  }

  public UUID getDepartmentId() {
    return departmentId;
  }

  public String getTitle() {
    return title;
  }

  public String getSponsorName() {
    return sponsorName;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public BigDecimal getAwardAmount() {
    return awardAmount;
  }

  public String getCurrency() {
    return currency;
  }

  public GrantStatus getStatus() {
    return status;
  }

  public long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
