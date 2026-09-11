package edu.university.grantledger.application;

import edu.university.grantledger.domain.Actor;
import edu.university.grantledger.domain.DomainException;
import edu.university.grantledger.domain.EntryType;
import edu.university.grantledger.domain.LedgerPolicy;
import edu.university.grantledger.persistence.AuditStore;
import edu.university.grantledger.persistence.BudgetEntity;
import edu.university.grantledger.persistence.BudgetRepository;
import edu.university.grantledger.persistence.EntryEntity;
import edu.university.grantledger.persistence.EntryRepository;
import edu.university.grantledger.persistence.GrantEntity;
import edu.university.grantledger.persistence.GrantRepository;
import edu.university.grantledger.persistence.IdempotencyStore;
import edu.university.grantledger.persistence.LedgerQueries;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PostingService {
  private final GrantAccess access;
  private final GrantRepository grants;
  private final BudgetRepository budgets;
  private final EntryRepository entries;
  private final LedgerQueries queries;
  private final AuditStore audit;
  private final IdempotencyStore idempotency;
  private final Clock clock;
  private final MeterRegistry metrics;

  public PostingService(
      GrantAccess access,
      GrantRepository grants,
      BudgetRepository budgets,
      EntryRepository entries,
      LedgerQueries queries,
      AuditStore audit,
      IdempotencyStore idempotency,
      Clock clock,
      MeterRegistry metrics) {
    this.access = access;
    this.grants = grants;
    this.budgets = budgets;
    this.entries = entries;
    this.queries = queries;
    this.audit = audit;
    this.idempotency = idempotency;
    this.clock = clock;
    this.metrics = metrics;
  }

  public Models.PostingResult expense(
      UUID grantId, Models.ExpenseCreate request, UUID key, Actor actor, String requestId) {
    var grant = access.lock(grantId, actor);
    var amount = LedgerPolicy.money(request.amount(), true);
    var normalized =
        new Models.ExpenseCreate(
            request.category(),
            amount.toPlainString(),
            request.effectiveDate(),
            GrantService.text(request.externalReference(), 100),
            GrantService.text(request.description(), 500));
    String hash = idempotency.hash(normalized);
    var previous = idempotency.replay(actor, grantId, "EXPENSE", key, hash);
    if (previous.isPresent()) return previous.get();
    LedgerPolicy.active(grant.getStatus());
    LedgerPolicy.expenseDate(
        request.effectiveDate(), grant.getStartDate(), grant.getEndDate(), LocalDate.now(clock));
    var budget =
        budgets.findByGrantIdOrderByCategory(grantId).stream()
            .filter(row -> row.getCategory() == request.category())
            .findFirst()
            .orElseThrow(GrantAccess::notFound);
    if (entries.existsByGrantIdAndExternalReference(grantId, normalized.externalReference()))
      throw new DomainException(
          409, "DUPLICATE_REFERENCE", "This expense reference already exists in the grant.");
    LedgerPolicy.available(
        budget
            .getAllocatedAmount()
            .subtract(queries.expenditure(grantId).get(budget.getCategory())),
        amount);
    var entry =
        new EntryEntity(
            grantId,
            budget.getId(),
            EntryType.EXPENSE,
            amount,
            request.effectiveDate(),
            normalized.externalReference(),
            normalized.description(),
            null,
            actor,
            clock.instant());
    return commit(grant, budget, entry, actor, key, "EXPENSE", hash, null, requestId);
  }

  public Models.PostingResult reverse(
      UUID grantId,
      UUID entryId,
      Models.ReversalCreate request,
      UUID key,
      Actor actor,
      String requestId) {
    var grant = access.lock(grantId, actor);
    String reason = GrantService.text(request.reason(), 500);
    String operation = "REVERSAL:" + entryId;
    String hash = idempotency.hash(Map.of("originalEntryId", entryId, "reason", reason));
    var previous = idempotency.replay(actor, grantId, operation, key, hash);
    if (previous.isPresent()) return previous.get();
    LedgerPolicy.active(grant.getStatus());
    var original = entries.findByIdAndGrantId(entryId, grantId).orElseThrow(GrantAccess::notFound);
    if (original.getType() != EntryType.EXPENSE)
      throw new DomainException(409, "INVALID_REVERSAL", "Only expense entries can be reversed.");
    if (entries.existsByOriginalEntryId(entryId))
      throw new DomainException(409, "ALREADY_REVERSED", "This expense has already been reversed.");
    var budget =
        budgets.findByGrantIdOrderByCategory(grantId).stream()
            .filter(row -> row.getId().equals(original.getBudgetAllocationId()))
            .findFirst()
            .orElseThrow(GrantAccess::notFound);
    var entry =
        new EntryEntity(
            grantId,
            budget.getId(),
            EntryType.REVERSAL,
            original.getAmount(),
            LocalDate.now(clock),
            null,
            reason,
            entryId,
            actor,
            clock.instant());
    return commit(grant, budget, entry, actor, key, operation, hash, reason, requestId);
  }

  private Models.PostingResult commit(
      GrantEntity grant,
      BudgetEntity budget,
      EntryEntity entry,
      Actor actor,
      UUID key,
      String operation,
      String hash,
      String reason,
      String requestId) {
    entries.saveAndFlush(entry);
    grant.touch(actor, clock.instant());
    grants.flush();
    var result =
        new Models.PostingResult(
            Mapping.entry(entry, budget.getCategory()),
            "/api/v1/grants/" + grant.getId() + "/entries/" + entry.getId(),
            false);
    audit.append(
        grant.getId(),
        entry.getType() + "_POSTED",
        "ENTRY",
        entry.getId(),
        actor,
        Map.of(),
        Map.of(
            "amount",
            entry.getAmount().toPlainString(),
            "category",
            budget.getCategory(),
            "type",
            entry.getType()),
        reason,
        requestId);
    idempotency.save(actor, grant.getId(), operation, key, hash, result);
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              @Override
              public void afterCommit() {
                metrics.counter("ledger.postings", "type", entry.getType().name()).increment();
              }
            });
    return result;
  }
}
