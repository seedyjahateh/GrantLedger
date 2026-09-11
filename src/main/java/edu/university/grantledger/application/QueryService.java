package edu.university.grantledger.application;

import edu.university.grantledger.domain.Actor;
import edu.university.grantledger.domain.Category;
import edu.university.grantledger.domain.DomainException;
import edu.university.grantledger.domain.GrantStatus;
import edu.university.grantledger.persistence.AuditStore;
import edu.university.grantledger.persistence.BudgetEntity;
import edu.university.grantledger.persistence.BudgetRepository;
import edu.university.grantledger.persistence.DepartmentRepository;
import edu.university.grantledger.persistence.EntryEntity;
import edu.university.grantledger.persistence.EntryRepository;
import edu.university.grantledger.persistence.GrantEntity;
import edu.university.grantledger.persistence.GrantRepository;
import edu.university.grantledger.persistence.LedgerQueries;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class QueryService {
  private final GrantAccess access;
  private final GrantRepository grants;
  private final DepartmentRepository departments;
  private final BudgetRepository budgets;
  private final EntryRepository entries;
  private final LedgerQueries queries;
  private final AuditStore audit;
  private final Clock clock;

  public QueryService(
      GrantAccess access,
      GrantRepository grants,
      DepartmentRepository departments,
      BudgetRepository budgets,
      EntryRepository entries,
      LedgerQueries queries,
      AuditStore audit,
      Clock clock) {
    this.access = access;
    this.grants = grants;
    this.departments = departments;
    this.budgets = budgets;
    this.entries = entries;
    this.queries = queries;
    this.audit = audit;
    this.clock = clock;
  }

  public List<Models.DepartmentView> departments(Actor actor) {
    actor.requireReader();
    return departments.findByIdInOrderByCode(actor.departments()).stream()
        .map(
            department ->
                new Models.DepartmentView(
                    department.getId(),
                    department.getCode(),
                    department.getName(),
                    department.isActive()))
        .toList();
  }

  public Models.GrantView grant(UUID id, Actor actor) {
    return Mapping.grant(access.read(id, actor));
  }

  public Models.PageView<Models.GrantView> grants(
      Actor actor, UUID departmentId, GrantStatus status, String search, Pageable page) {
    actor.requireReader();
    if (departmentId != null && !actor.departments().contains(departmentId))
      throw GrantAccess.notFound();
    Specification<GrantEntity> specification =
        (root, query, builder) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(root.get("departmentId").in(actor.departments()));
          if (departmentId != null)
            predicates.add(builder.equal(root.get("departmentId"), departmentId));
          if (status != null) predicates.add(builder.equal(root.get("status"), status));
          if (search != null && !search.isBlank()) {
            String escaped =
                search
                    .strip()
                    .toLowerCase(java.util.Locale.ROOT)
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
            predicates.add(
                builder.or(
                    builder.like(builder.lower(root.get("awardNumber")), "%" + escaped + "%", '!'),
                    builder.like(builder.lower(root.get("title")), "%" + escaped + "%", '!')));
          }
          return builder.and(predicates.toArray(Predicate[]::new));
        };
    return Models.PageView.of(grants.findAll(specification, page).map(Mapping::grant));
  }

  public Models.BudgetsView budgets(UUID id, Actor actor) {
    return GrantService.budgetView(
        access.read(id, actor), budgets.findByGrantIdOrderByCategory(id));
  }

  public Models.BalanceView balance(UUID id, Actor actor) {
    var grant = access.read(id, actor);
    var expenditure = queries.expenditure(id);
    List<Models.CategoryBalance> categories = new ArrayList<>();
    BigDecimal allocated = new BigDecimal("0.00");
    BigDecimal spent = new BigDecimal("0.00");
    for (var budget : budgets.findByGrantIdOrderByCategory(id)) {
      var categorySpent = expenditure.get(budget.getCategory());
      categories.add(
          new Models.CategoryBalance(
              budget.getCategory(),
              budget.getAllocatedAmount().toPlainString(),
              categorySpent.toPlainString(),
              budget.getAllocatedAmount().subtract(categorySpent).toPlainString()));
      allocated = allocated.add(budget.getAllocatedAmount());
      spent = spent.add(categorySpent);
    }
    return new Models.BalanceView(
        id,
        grant.getVersion(),
        clock.instant(),
        "USD",
        categories,
        allocated.toPlainString(),
        spent.toPlainString(),
        allocated.subtract(spent).toPlainString());
  }

  public Models.EntryView entry(UUID id, UUID entryId, Actor actor) {
    access.read(id, actor);
    var entry = entries.findByIdAndGrantId(entryId, id).orElseThrow(GrantAccess::notFound);
    return Mapping.entry(entry, categoryMap(id).get(entry.getBudgetAllocationId()));
  }

  public Models.PageView<Models.EntryView> entries(
      UUID id, Models.EntryFilter filter, Pageable page, Actor actor) {
    access.read(id, actor);
    var categories = categoryMap(id);
    return Models.PageView.of(
        entries
            .findAll(entrySpec(id, filter), page)
            .map(entry -> Mapping.entry(entry, categories.get(entry.getBudgetAllocationId()))));
  }

  public Models.PageView<Models.AuditView> audit(UUID id, Pageable page, Actor actor) {
    actor.requireAuditor();
    access.read(id, actor);
    long count = audit.count(id);
    return new Models.PageView<>(
        audit.list(id, page.getPageSize(), page.getOffset()),
        page.getPageNumber(),
        page.getPageSize(),
        count,
        (int) ((count + page.getPageSize() - 1) / page.getPageSize()));
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 10)
  public String export(UUID id, Models.EntryFilter filter, Actor actor, String requestId) {
    var grant = access.read(id, actor);
    var categories = categoryMap(id);
    var rows =
        entries
            .findAll(
                entrySpec(id, filter), PageRequest.of(0, 10001, Sort.by("effectiveDate", "id")))
            .getContent();
    if (rows.size() > 10000)
      throw new DomainException(
          422, "REPORT_TOO_LARGE", "Narrow the date range to at most 10000 entries.");
    StringBuilder csv =
        new StringBuilder(
            "entryId,awardNumber,category,type,amount,currency,effectiveDate,externalReference,originalEntryId,description,createdAt\r\n");
    for (var row : rows) {
      csv.append(
              String.join(
                  ",",
                  csv(row.getId()),
                  csv(grant.getAwardNumber()),
                  csv(categories.get(row.getBudgetAllocationId())),
                  csv(row.getType()),
                  csv(row.getAmount().toPlainString()),
                  "USD",
                  csv(row.getEffectiveDate()),
                  csv(row.getExternalReference()),
                  csv(row.getOriginalEntryId()),
                  csv(row.getDescription()),
                  csv(row.getCreatedAt())))
          .append("\r\n");
    }
    audit.append(
        id,
        "EXPORT_INITIATED",
        "GRANT",
        id,
        actor,
        Map.of(),
        Map.of("rowCount", rows.size()),
        null,
        requestId);
    return csv.toString();
  }

  static String csv(Object input) {
    String value = input == null ? "" : input.toString();
    String trimmed = value.stripLeading();
    if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0
        || value.startsWith("\t")
        || value.startsWith("\r")
        || value.startsWith("\n")) value = "'" + value;
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  private Map<UUID, Category> categoryMap(UUID id) {
    return budgets.findByGrantIdOrderByCategory(id).stream()
        .collect(Collectors.toMap(BudgetEntity::getId, BudgetEntity::getCategory));
  }

  private Specification<EntryEntity> entrySpec(UUID id, Models.EntryFilter filter) {
    if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to()))
      throw new DomainException(400, "INVALID_DATES", "The report start must not follow its end.");
    UUID budgetId =
        filter.category() == null
            ? null
            : budgets.findByGrantIdOrderByCategory(id).stream()
                .filter(budget -> budget.getCategory() == filter.category())
                .map(BudgetEntity::getId)
                .findFirst()
                .orElseThrow(GrantAccess::notFound);
    return (root, query, builder) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(builder.equal(root.get("grantId"), id));
      if (budgetId != null) predicates.add(builder.equal(root.get("budgetAllocationId"), budgetId));
      if (filter.type() != null) predicates.add(builder.equal(root.get("type"), filter.type()));
      if (filter.from() != null)
        predicates.add(builder.greaterThanOrEqualTo(root.get("effectiveDate"), filter.from()));
      if (filter.to() != null)
        predicates.add(builder.lessThanOrEqualTo(root.get("effectiveDate"), filter.to()));
      if (filter.externalReference() != null)
        predicates.add(
            builder.equal(root.get("externalReference"), filter.externalReference().strip()));
      return builder.and(predicates.toArray(Predicate[]::new));
    };
  }
}
