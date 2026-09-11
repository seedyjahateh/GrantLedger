package edu.university.grantledger.application;

import com.fasterxml.jackson.databind.JsonNode;
import edu.university.grantledger.domain.Actor;
import edu.university.grantledger.domain.Category;
import edu.university.grantledger.domain.DomainException;
import edu.university.grantledger.domain.GrantStatus;
import edu.university.grantledger.domain.LedgerPolicy;
import edu.university.grantledger.persistence.AuditStore;
import edu.university.grantledger.persistence.BudgetEntity;
import edu.university.grantledger.persistence.BudgetRepository;
import edu.university.grantledger.persistence.DepartmentRepository;
import edu.university.grantledger.persistence.GrantEntity;
import edu.university.grantledger.persistence.GrantRepository;
import edu.university.grantledger.persistence.LedgerQueries;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GrantService {
  private final GrantRepository grants;
  private final BudgetRepository budgets;
  private final DepartmentRepository departments;
  private final GrantAccess access;
  private final LedgerQueries queries;
  private final AuditStore audit;
  private final Clock clock;

  public GrantService(
      GrantRepository grants,
      BudgetRepository budgets,
      DepartmentRepository departments,
      GrantAccess access,
      LedgerQueries queries,
      AuditStore audit,
      Clock clock) {
    this.grants = grants;
    this.budgets = budgets;
    this.departments = departments;
    this.access = access;
    this.queries = queries;
    this.audit = audit;
    this.clock = clock;
  }

  public Models.GrantView create(Models.GrantCreate request, Actor actor, String requestId) {
    actor.requireAdmin();
    if (!actor.departments().contains(request.departmentId())) throw GrantAccess.notFound();
    var department =
        departments.findById(request.departmentId()).orElseThrow(GrantAccess::notFound);
    if (!department.isActive())
      throw new DomainException(
          409, "INACTIVE_DEPARTMENT", "New grants require an active department.");
    LedgerPolicy.dates(request.startDate(), request.endDate());
    var grant =
        new GrantEntity(
            text(request.awardNumber(), 50).toUpperCase(Locale.ROOT),
            department.getId(),
            text(request.title(), 200),
            text(request.sponsorName(), 200),
            request.startDate(),
            request.endDate(),
            LedgerPolicy.money(request.awardAmount(), true),
            actor,
            clock.instant());
    grants.saveAndFlush(grant);
    budgets.saveAll(
        Arrays.stream(Category.values())
            .map(category -> new BudgetEntity(grant.getId(), category, actor, clock.instant()))
            .toList());
    audit.append(
        grant.getId(),
        "GRANT_CREATED",
        "GRANT",
        grant.getId(),
        actor,
        Map.of(),
        Mapping.grant(grant),
        null,
        requestId);
    return Mapping.grant(grant);
  }

  public Models.GrantView patch(
      UUID id, JsonNode patch, String etag, Actor actor, String requestId) {
    var grant = access.lock(id, actor);
    access.match(grant, etag);
    if (grant.getStatus() == GrantStatus.CLOSED)
      throw new DomainException(409, "INVALID_GRANT_STATE", "Closed grants are read-only.");
    Set<String> allowed =
        grant.getStatus() == GrantStatus.DRAFT
            ? Set.of("awardNumber", "title", "sponsorName", "startDate", "endDate", "awardAmount")
            : Set.of("title", "sponsorName");
    if (!patch.isObject() || patch.isEmpty())
      throw new DomainException(400, "INVALID_PATCH", "Supply at least one editable field.");
    patch
        .fields()
        .forEachRemaining(
            field -> {
              if (!allowed.contains(field.getKey()) || !field.getValue().isTextual())
                throw new DomainException(
                    400,
                    "INVALID_PATCH",
                    "Unknown, immutable, or non-string field: " + field.getKey());
            });
    var before = Mapping.grant(grant);
    LocalDate start =
        patch.has("startDate")
            ? LocalDate.parse(patch.get("startDate").textValue())
            : grant.getStartDate();
    LocalDate end =
        patch.has("endDate")
            ? LocalDate.parse(patch.get("endDate").textValue())
            : grant.getEndDate();
    LedgerPolicy.dates(start, end);
    BigDecimal award =
        patch.has("awardAmount")
            ? LedgerPolicy.money(patch.get("awardAmount").textValue(), true)
            : grant.getAwardAmount();
    var amounts = allocationAmounts(budgets.findByGrantIdOrderByCategory(id));
    LedgerPolicy.budgets(grant.getStatus(), award, amounts, queries.expenditure(id));
    grant.metadata(
        patch.has("awardNumber")
            ? text(patch.get("awardNumber").textValue(), 50).toUpperCase(Locale.ROOT)
            : grant.getAwardNumber(),
        patch.has("title") ? text(patch.get("title").textValue(), 200) : grant.getTitle(),
        patch.has("sponsorName")
            ? text(patch.get("sponsorName").textValue(), 200)
            : grant.getSponsorName(),
        start,
        end,
        award);
    grant.touch(actor, clock.instant());
    grants.flush();
    audit.append(
        id, "GRANT_UPDATED", "GRANT", id, actor, before, Mapping.grant(grant), null, requestId);
    return Mapping.grant(grant);
  }

  public Models.BudgetsView allocate(
      UUID id, Models.BudgetsUpdate request, String etag, Actor actor, String requestId) {
    var grant = access.lock(id, actor);
    access.match(grant, etag);
    var amounts = new EnumMap<Category, BigDecimal>(Category.class);
    for (var allocation : request.allocations()) {
      if (amounts.put(allocation.category(), LedgerPolicy.money(allocation.amount(), false))
          != null)
        throw new DomainException(
            400, "INVALID_BUDGETS", "Each category must appear exactly once.");
    }
    LedgerPolicy.budgets(
        grant.getStatus(), grant.getAwardAmount(), amounts, queries.expenditure(id));
    var entities = budgets.findByGrantIdOrderByCategory(id);
    var before = allocationAmounts(entities);
    entities.forEach(
        budget -> budget.allocate(amounts.get(budget.getCategory()), actor, clock.instant()));
    grant.touch(actor, clock.instant());
    grants.flush();
    audit.append(
        id,
        "BUDGET_UPDATED",
        "GRANT",
        id,
        actor,
        before,
        amounts,
        text(request.reason(), 500),
        requestId);
    return budgetView(grant, entities);
  }

  public Models.GrantView activate(UUID id, String etag, Actor actor, String requestId) {
    var grant = access.lock(id, actor);
    access.match(grant, etag);
    if (grant.getStatus() != GrantStatus.DRAFT)
      throw new DomainException(409, "INVALID_GRANT_STATE", "Only draft grants can be activated.");
    LedgerPolicy.budgets(
        GrantStatus.ACTIVE,
        grant.getAwardAmount(),
        allocationAmounts(budgets.findByGrantIdOrderByCategory(id)),
        queries.expenditure(id));
    return transition(grant, GrantStatus.ACTIVE, actor, null, requestId);
  }

  public Models.GrantView close(
      UUID id, Models.ClosureCreate request, String etag, Actor actor, String requestId) {
    var grant = access.lock(id, actor);
    access.match(grant, etag);
    LedgerPolicy.active(grant.getStatus());
    return transition(
        grant, GrantStatus.CLOSED, actor, text(request.reconciliationNote(), 500), requestId);
  }

  private Models.GrantView transition(
      GrantEntity grant, GrantStatus status, Actor actor, String reason, String requestId) {
    var before = Map.of("status", grant.getStatus());
    grant.transition(status);
    grant.touch(actor, clock.instant());
    grants.flush();
    audit.append(
        grant.getId(),
        "GRANT_" + status,
        "GRANT",
        grant.getId(),
        actor,
        before,
        Map.of("status", status),
        reason,
        requestId);
    return Mapping.grant(grant);
  }

  static Map<Category, BigDecimal> allocationAmounts(List<BudgetEntity> entities) {
    var result = new EnumMap<Category, BigDecimal>(Category.class);
    entities.forEach(budget -> result.put(budget.getCategory(), budget.getAllocatedAmount()));
    return result;
  }

  static Models.BudgetsView budgetView(GrantEntity grant, List<BudgetEntity> entities) {
    return new Models.BudgetsView(
        grant.getId(),
        grant.getVersion(),
        "USD",
        entities.stream()
            .map(
                budget ->
                    new Models.BudgetView(
                        budget.getCategory(), budget.getAllocatedAmount().toPlainString()))
            .toList());
  }

  static String text(String value, int max) {
    if (value == null
        || value.isBlank()
        || value.strip().length() > max
        || value.indexOf('\0') >= 0)
      throw new DomainException(
          400, "INVALID_TEXT", "Text must be nonblank and at most " + max + " characters.");
    return value.strip();
  }
}
