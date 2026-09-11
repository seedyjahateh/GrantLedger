package edu.university.grantledger.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EntryRepository
    extends JpaRepository<EntryEntity, UUID>, JpaSpecificationExecutor<EntryEntity> {
  Optional<EntryEntity> findByIdAndGrantId(UUID id, UUID grantId);

  boolean existsByGrantIdAndExternalReference(UUID grantId, String externalReference);

  boolean existsByOriginalEntryId(UUID originalEntryId);
}
