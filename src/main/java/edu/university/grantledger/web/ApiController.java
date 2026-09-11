package edu.university.grantledger.web;

import com.fasterxml.jackson.databind.JsonNode;
import edu.university.grantledger.application.GrantService;
import edu.university.grantledger.application.Models;
import edu.university.grantledger.application.PostingService;
import edu.university.grantledger.application.QueryService;
import edu.university.grantledger.config.ActorResolver;
import edu.university.grantledger.domain.GrantStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
public class ApiController {
  private static final Set<String> ENTRY_PARAMS =
      Set.of("page", "size", "sort", "category", "type", "from", "to", "externalReference");
  private final GrantService grants;
  private final PostingService postings;
  private final QueryService queries;
  private final ActorResolver actors;

  public ApiController(
      GrantService grants, PostingService postings, QueryService queries, ActorResolver actors) {
    this.grants = grants;
    this.postings = postings;
    this.queries = queries;
    this.actors = actors;
  }

  @GetMapping("/departments")
  @Operation(operationId = "listDepartments")
  public List<Models.DepartmentView> departments(
      Authentication auth, @RequestParam Map<String, String> params) {
    QueryParameters.allowed(params, Set.of());
    return queries.departments(actors.resolve(auth));
  }

  @PostMapping("/grants")
  @Operation(operationId = "createGrant")
  public ResponseEntity<Models.GrantView> create(
      @Valid @RequestBody Models.GrantCreate request,
      Authentication auth,
      HttpServletRequest http) {
    var grant = grants.create(request, actors.resolve(auth), requestId(http));
    return ResponseEntity.created(URI.create("/api/v1/grants/" + grant.id()))
        .eTag(etag(grant.version()))
        .body(grant);
  }

  @GetMapping("/grants")
  @Operation(operationId = "listGrants")
  public Models.PageView<Models.GrantView> list(
      @RequestParam Map<String, String> params, Authentication auth) {
    QueryParameters.allowed(
        params, Set.of("page", "size", "sort", "departmentId", "status", "search"));
    return queries.grants(
        actors.resolve(auth),
        params.containsKey("departmentId") ? UUID.fromString(params.get("departmentId")) : null,
        params.containsKey("status") ? GrantStatus.valueOf(params.get("status")) : null,
        params.get("search"),
        QueryParameters.page(params, "createdAt", Set.of("createdAt", "awardNumber", "id")));
  }

  @GetMapping("/grants/{id}")
  @Operation(operationId = "getGrant")
  public ResponseEntity<Models.GrantView> get(@PathVariable UUID id, Authentication auth) {
    var grant = queries.grant(id, actors.resolve(auth));
    return versioned(grant);
  }

  @PatchMapping("/grants/{id}")
  @Operation(operationId = "updateGrant")
  public ResponseEntity<Models.GrantView> patch(
      @PathVariable UUID id,
      @RequestBody JsonNode request,
      @RequestHeader(value = "If-Match", required = false) String version,
      Authentication auth,
      HttpServletRequest http) {
    return versioned(grants.patch(id, request, version, actors.resolve(auth), requestId(http)));
  }

  @PostMapping("/grants/{id}/activation")
  @Operation(operationId = "activateGrant")
  public ResponseEntity<Models.GrantView> activate(
      @PathVariable UUID id,
      @RequestHeader(value = "If-Match", required = false) String version,
      Authentication auth,
      HttpServletRequest http) {
    return versioned(grants.activate(id, version, actors.resolve(auth), requestId(http)));
  }

  @PostMapping("/grants/{id}/closure")
  @Operation(operationId = "closeGrant")
  public ResponseEntity<Models.GrantView> close(
      @PathVariable UUID id,
      @Valid @RequestBody Models.ClosureCreate request,
      @RequestHeader(value = "If-Match", required = false) String version,
      Authentication auth,
      HttpServletRequest http) {
    return versioned(grants.close(id, request, version, actors.resolve(auth), requestId(http)));
  }

  @GetMapping("/grants/{id}/budgets")
  @Operation(operationId = "getBudgets")
  public ResponseEntity<Models.BudgetsView> budgets(@PathVariable UUID id, Authentication auth) {
    var result = queries.budgets(id, actors.resolve(auth));
    return ResponseEntity.ok().eTag(etag(result.version())).body(result);
  }

  @PutMapping("/grants/{id}/budgets")
  @Operation(operationId = "replaceBudgets")
  public ResponseEntity<Models.BudgetsView> allocate(
      @PathVariable UUID id,
      @Valid @RequestBody Models.BudgetsUpdate request,
      @RequestHeader(value = "If-Match", required = false) String version,
      Authentication auth,
      HttpServletRequest http) {
    var result = grants.allocate(id, request, version, actors.resolve(auth), requestId(http));
    return ResponseEntity.ok().eTag(etag(result.version())).body(result);
  }

  @PostMapping("/grants/{id}/entries")
  @Operation(operationId = "postExpense")
  public ResponseEntity<Models.EntryView> expense(
      @PathVariable UUID id,
      @Valid @RequestBody Models.ExpenseCreate request,
      @RequestHeader("Idempotency-Key") UUID key,
      Authentication auth,
      HttpServletRequest http) {
    return posted(postings.expense(id, request, key, actors.resolve(auth), requestId(http)));
  }

  @GetMapping("/grants/{id}/entries")
  @Operation(operationId = "listEntries")
  public Models.PageView<Models.EntryView> entries(
      @PathVariable UUID id, @RequestParam Map<String, String> params, Authentication auth) {
    QueryParameters.allowed(params, ENTRY_PARAMS);
    return queries.entries(
        id,
        QueryParameters.filter(params),
        QueryParameters.page(params, "effectiveDate", Set.of("effectiveDate", "createdAt", "id")),
        actors.resolve(auth));
  }

  @GetMapping("/grants/{id}/entries/{entryId}")
  @Operation(operationId = "getEntry")
  public Models.EntryView entry(
      @PathVariable UUID id, @PathVariable UUID entryId, Authentication auth) {
    return queries.entry(id, entryId, actors.resolve(auth));
  }

  @PostMapping("/grants/{id}/entries/{entryId}/reversals")
  @Operation(operationId = "reverseExpense")
  public ResponseEntity<Models.EntryView> reverse(
      @PathVariable UUID id,
      @PathVariable UUID entryId,
      @Valid @RequestBody Models.ReversalCreate request,
      @RequestHeader("Idempotency-Key") UUID key,
      Authentication auth,
      HttpServletRequest http) {
    return posted(
        postings.reverse(id, entryId, request, key, actors.resolve(auth), requestId(http)));
  }

  @GetMapping("/grants/{id}/balance")
  @Operation(operationId = "getBalance")
  public ResponseEntity<Models.BalanceView> balance(@PathVariable UUID id, Authentication auth) {
    var result = queries.balance(id, actors.resolve(auth));
    return ResponseEntity.ok().eTag(etag(result.version())).body(result);
  }

  @GetMapping(value = "/grants/{id}/reports/activity.csv", produces = "text/csv")
  @Operation(operationId = "exportActivity")
  public ResponseEntity<String> export(
      @PathVariable UUID id,
      @RequestParam Map<String, String> params,
      Authentication auth,
      HttpServletRequest http) {
    QueryParameters.allowed(params, Set.of("category", "type", "from", "to", "externalReference"));
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=grant-" + id + "-activity.csv")
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .body(
            queries.export(
                id, QueryParameters.filter(params), actors.resolve(auth), requestId(http)));
  }

  @GetMapping("/grants/{id}/audit-events")
  @Operation(operationId = "listAuditEvents")
  public Models.PageView<Models.AuditView> audit(
      @PathVariable UUID id, @RequestParam Map<String, String> params, Authentication auth) {
    QueryParameters.allowed(params, Set.of("page", "size"));
    return queries.audit(
        id, QueryParameters.page(params, "occurredAt", Set.of("occurredAt")), actors.resolve(auth));
  }

  private ResponseEntity<Models.EntryView> posted(Models.PostingResult result) {
    return ResponseEntity.created(URI.create(result.location()))
        .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
        .body(result.entry());
  }

  private ResponseEntity<Models.GrantView> versioned(Models.GrantView grant) {
    return ResponseEntity.ok().eTag(etag(grant.version())).body(grant);
  }

  static String etag(long version) {
    return "\"" + version + "\"";
  }

  static String requestId(HttpServletRequest request) {
    return String.valueOf(request.getAttribute("requestId"));
  }
}
