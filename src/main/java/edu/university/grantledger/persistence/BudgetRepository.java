package edu.university.grantledger.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<BudgetEntity, UUID> {
  List<BudgetEntity> findByGrantIdOrderByCategory(UUID grantId);
}
