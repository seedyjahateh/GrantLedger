package edu.university.grantledger.persistence;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<DepartmentEntity, UUID> {
  List<DepartmentEntity> findByIdInOrderByCode(Set<UUID> ids);
}
