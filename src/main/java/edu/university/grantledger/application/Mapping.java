package edu.university.grantledger.application;

import edu.university.grantledger.domain.Category;
import edu.university.grantledger.persistence.EntryEntity;
import edu.university.grantledger.persistence.GrantEntity;

final class Mapping {
  private Mapping() {}

  static Models.GrantView grant(GrantEntity grant) {
    return new Models.GrantView(
        grant.getId(),
        grant.getAwardNumber(),
        grant.getDepartmentId(),
        grant.getTitle(),
        grant.getSponsorName(),
        grant.getStartDate(),
        grant.getEndDate(),
        grant.getAwardAmount().toPlainString(),
        grant.getCurrency(),
        grant.getStatus(),
        grant.getVersion(),
        grant.getCreatedAt(),
        grant.getUpdatedAt());
  }

  static Models.EntryView entry(EntryEntity entry, Category category) {
    return new Models.EntryView(
        entry.getId(),
        entry.getGrantId(),
        category,
        entry.getType(),
        entry.getAmount().toPlainString(),
        "USD",
        entry.getEffectiveDate(),
        entry.getExternalReference(),
        entry.getDescription(),
        entry.getOriginalEntryId(),
        entry.getCreatedAt());
  }
}
