package edu.university.grantledger.application;

import edu.university.grantledger.domain.Category;
import edu.university.grantledger.domain.EntryType;
import edu.university.grantledger.domain.GrantStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;

public final class Models {
  private Models() {}

  public record GrantCreate(
      @NotBlank @Size(max = 50) String awardNumber,
      @NotNull UUID departmentId,
      @NotBlank @Size(max = 200) String title,
      @NotBlank @Size(max = 200) String sponsorName,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate,
      @NotBlank String awardAmount) {}

  public record Allocation(@NotNull Category category, @NotBlank String amount) {}

  public record BudgetsUpdate(
      @NotNull @Size(min = 5, max = 5) List<@Valid Allocation> allocations,
      @NotBlank @Size(max = 500) String reason) {}

  public record ExpenseCreate(
      @NotNull Category category,
      @NotBlank String amount,
      @NotNull LocalDate effectiveDate,
      @NotBlank @Size(max = 100) String externalReference,
      @NotBlank @Size(max = 500) String description) {}

  public record ReversalCreate(@NotBlank @Size(max = 500) String reason) {}

  public record ClosureCreate(@NotBlank @Size(max = 500) String reconciliationNote) {}

  public record DepartmentView(UUID id, String code, String name, boolean active) {}

  public record GrantView(
      UUID id,
      String awardNumber,
      UUID departmentId,
      String title,
      String sponsorName,
      LocalDate startDate,
      LocalDate endDate,
      String awardAmount,
      String currency,
      GrantStatus status,
      long version,
      Instant createdAt,
      Instant updatedAt) {}

  public record BudgetView(Category category, String allocatedAmount) {}

  public record BudgetsView(
      UUID grantId, long version, String currency, List<BudgetView> allocations) {}

  public record EntryView(
      UUID id,
      UUID grantId,
      Category category,
      EntryType type,
      String amount,
      String currency,
      LocalDate effectiveDate,
      String externalReference,
      String description,
      UUID originalEntryId,
      Instant createdAt) {}

  public record CategoryBalance(
      Category category, String allocatedBudget, String netExpenditure, String remaining) {}

  public record BalanceView(
      UUID grantId,
      long version,
      Instant generatedAt,
      String currency,
      List<CategoryBalance> categories,
      String allocatedBudget,
      String netExpenditure,
      String remaining) {}

  public record AuditView(
      UUID id,
      String action,
      String resourceType,
      UUID resourceId,
      String actorIssuer,
      String actorSubject,
      Instant occurredAt,
      Map<String, Object> beforeValues,
      Map<String, Object> afterValues,
      String reason,
      String requestId) {}

  public record PageView<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <T> PageView<T> of(Page<T> result) {
      return new PageView<>(
          result.getContent(),
          result.getNumber(),
          result.getSize(),
          result.getTotalElements(),
          result.getTotalPages());
    }
  }

  public record PostingResult(EntryView entry, String location, boolean replayed) {}

  public record EntryFilter(
      Category category, EntryType type, LocalDate from, LocalDate to, String externalReference) {}
}
