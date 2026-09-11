package edu.university.grantledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.grantledger.application.GrantService;
import edu.university.grantledger.application.Models;
import edu.university.grantledger.application.PostingService;
import edu.university.grantledger.application.QueryService;
import edu.university.grantledger.domain.Actor;
import edu.university.grantledger.domain.Category;
import edu.university.grantledger.domain.DomainException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;

class LedgerIT extends DatabaseTestSupport {
  @Autowired ObjectMapper json;
  @Autowired JdbcClient jdbc;
  @Autowired GrantService grants;
  @Autowired PostingService postings;
  @Autowired QueryService queries;
  @Autowired PlatformTransactionManager transactionManager;
  private final Actor admin =
      new Actor(
          "https://issuer.test", "administrator", Set.of("RESEARCH_ADMIN"), Set.of(DEPARTMENT));

  @BeforeEach
  void departments() {
    jdbc.sql(
            "INSERT INTO department(id,code,name,active) VALUES (:id,'RESEARCH','Research administration',true) ON CONFLICT(id) DO NOTHING")
        .param("id", DEPARTMENT)
        .update();
    jdbc.sql(
            "INSERT INTO department(id,code,name,active) VALUES (:id,'OTHER','Other department',true) ON CONFLICT(id) DO NOTHING")
        .param("id", OTHER_DEPARTMENT)
        .update();
  }

  private RequestPostProcessor user(String role, UUID department) {
    return jwt()
        .jwt(
            jwt ->
                jwt.issuer("https://issuer.test")
                    .subject("administrator")
                    .claim("roles", List.of(role))
                    .claim("department_ids", List.of(department.toString())));
  }

  private Models.GrantView draft() {
    return grants.create(
        new Models.GrantCreate(
            "AWARD-" + UUID.randomUUID(),
            DEPARTMENT,
            "Synthetic research",
            "Test foundation",
            LocalDate.now().minusYears(1),
            LocalDate.now().plusYears(1),
            "100.00"),
        admin,
        "test");
  }

  private Models.BudgetsUpdate allocation(String travel, String other) {
    return new Models.BudgetsUpdate(
        Arrays.stream(Category.values())
            .map(
                category ->
                    new Models.Allocation(
                        category,
                        category == Category.TRAVEL
                            ? travel
                            : category == Category.OTHER ? other : "0.00"))
            .toList(),
        "Test allocation");
  }

  private String etag(long version) {
    return "\"" + version + "\"";
  }

  private Models.GrantView active() {
    var grant = draft();
    var budget =
        grants.allocate(
            grant.id(), allocation("100.00", "0.00"), etag(grant.version()), admin, "test");
    return grants.activate(grant.id(), etag(budget.version()), admin, "test");
  }

  private Models.ExpenseCreate expense(String amount, String reference) {
    return new Models.ExpenseCreate(
        Category.TRAVEL, amount, LocalDate.now().minusDays(1), reference, "Synthetic supplies");
  }

  @Test
  void completeApiLifecycle() throws Exception {
    String create =
        json.writeValueAsString(
            new Models.GrantCreate(
                "API-" + UUID.randomUUID(),
                DEPARTMENT,
                "API research",
                "Synthetic sponsor",
                LocalDate.now().minusYears(1),
                LocalDate.now().plusYears(1),
                "100.00"));
    var result =
        mvc.perform(
                post("/api/v1/grants")
                    .with(user("RESEARCH_ADMIN", DEPARTMENT))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(create))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode grant = json.readTree(result.getResponse().getContentAsString());
    String path = "/api/v1/grants/" + grant.get("id").asText();
    String version = result.getResponse().getHeader("ETag");
    mvc.perform(get("/api/v1/departments").with(user("VIEWER", DEPARTMENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("RESEARCH"));
    mvc.perform(
            get("/api/v1/grants").param("search", "API research").with(user("VIEWER", DEPARTMENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").isNumber());
    mvc.perform(get(path).with(user("VIEWER", DEPARTMENT))).andExpect(status().isOk());
    version =
        mvc.perform(
                patch(path)
                    .with(user("RESEARCH_ADMIN", DEPARTMENT))
                    .header("If-Match", version)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Updated research\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated research"))
            .andReturn()
            .getResponse()
            .getHeader("ETag");
    version =
        mvc.perform(
                put(path + "/budgets")
                    .with(user("RESEARCH_ADMIN", DEPARTMENT))
                    .header("If-Match", version)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(allocation("100.00", "0.00"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("ETag");
    mvc.perform(get(path + "/budgets").with(user("AUDITOR", DEPARTMENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allocations.length()").value(5));
    mvc.perform(
            post(path + "/activation")
                .with(user("RESEARCH_ADMIN", DEPARTMENT))
                .header("If-Match", version))
        .andExpect(status().isOk());
    var posted =
        mvc.perform(
                post(path + "/entries")
                    .with(user("RESEARCH_ADMIN", DEPARTMENT))
                    .header("Idempotency-Key", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(expense("25.40", "API-EXPENSE"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amount").value("25.40"))
            .andReturn();
    String entryPath = posted.getResponse().getHeader("Location");
    mvc.perform(get(entryPath).with(user("VIEWER", DEPARTMENT))).andExpect(status().isOk());
    mvc.perform(
            get(path + "/entries")
                .param("category", "TRAVEL")
                .param("type", "EXPENSE")
                .param("from", LocalDate.now().minusDays(5).toString())
                .param("to", LocalDate.now().toString())
                .param("externalReference", "API-EXPENSE")
                .with(user("VIEWER", DEPARTMENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(path + "/balance").with(user("VIEWER", DEPARTMENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.remaining").value("74.60"));
    mvc.perform(
            post(entryPath + "/reversals")
                .with(user("RESEARCH_ADMIN", DEPARTMENT))
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Correction\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("REVERSAL"));
    var export =
        mvc.perform(get(path + "/reports/activity.csv").with(user("AUDITOR", DEPARTMENT)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(export.getResponse().getContentAsString()).contains("API-EXPENSE", "REVERSAL");
    mvc.perform(get(path + "/audit-events").with(user("AUDITOR", DEPARTMENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].action").value("EXPORT_INITIATED"));
    version =
        mvc.perform(get(path).with(user("VIEWER", DEPARTMENT)))
            .andReturn()
            .getResponse()
            .getHeader("ETag");
    mvc.perform(
            post(path + "/closure")
                .with(user("RESEARCH_ADMIN", DEPARTMENT))
                .header("If-Match", version)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reconciliationNote\":\"Checked with synthetic records\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"));
  }

  @Test
  void scopeAndRolesCoverAllReadPathsAndMutations() throws Exception {
    var grant = active();
    var entry =
        postings.expense(grant.id(), expense("10.00", "scope"), UUID.randomUUID(), admin, "test");
    String path = "/api/v1/grants/" + grant.id();
    for (String suffix :
        List.of(
            "",
            "/budgets",
            "/balance",
            "/entries",
            "/entries/" + entry.entry().id(),
            "/reports/activity.csv",
            "/audit-events")) {
      mvc.perform(get(path + suffix).with(user("RESEARCH_ADMIN", OTHER_DEPARTMENT)))
          .andExpect(status().isNotFound());
      mvc.perform(get(path + suffix)).andExpect(status().isUnauthorized());
    }
    mvc.perform(get(path + "/audit-events").with(user("VIEWER", DEPARTMENT)))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/grants")
                .param("departmentId", OTHER_DEPARTMENT.toString())
                .with(user("VIEWER", DEPARTMENT)))
        .andExpect(status().isNotFound());
    for (String suffix :
        List.of(
            "/activation",
            "/closure",
            "/entries",
            "/entries/" + entry.entry().id() + "/reversals")) {
      Object request =
          suffix.equals("/entries")
              ? expense("1.00", "x")
              : suffix.endsWith("reversals")
                  ? new Models.ReversalCreate("x")
                  : new Models.ClosureCreate("x");
      mvc.perform(
              post(path + suffix)
                  .with(user("VIEWER", DEPARTMENT))
                  .header("If-Match", etag(grant.version()))
                  .header("Idempotency-Key", UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(request)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            put(path + "/budgets")
                .with(user("VIEWER", DEPARTMENT))
                .header("If-Match", etag(grant.version()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(allocation("100.00", "0.00"))))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch(path)
                .with(user("VIEWER", DEPARTMENT))
                .header("If-Match", etag(grant.version()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get(path).with(jwt())).andExpect(status().isForbidden());
    mvc.perform(get(path).with(oidcLogin())).andExpect(status().isUnauthorized());
  }

  @Test
  void validationAndConflictResponses() throws Exception {
    var grant = active();
    String path = "/api/v1/grants/" + grant.id();
    mvc.perform(
            patch(path)
                .with(user("RESEARCH_ADMIN", DEPARTMENT))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\"}"))
        .andExpect(status().is(428));
    mvc.perform(
            patch(path)
                .with(user("RESEARCH_ADMIN", DEPARTMENT))
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\"}"))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(
            patch(path)
                .with(user("RESEARCH_ADMIN", DEPARTMENT))
                .header("If-Match", etag(grant.version()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"departmentId\":\"x\"}"))
        .andExpect(status().isBadRequest());
    for (String body : List.of("{}", "{", "{\"amount\":\"10.00\",\"unexpected\":true}"))
      mvc.perform(
              post(path + "/entries")
                  .with(user("RESEARCH_ADMIN", DEPARTMENT))
                  .header("Idempotency-Key", UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    for (Map<String, String> invalid :
        List.of(
            Map.of("size", "101"),
            Map.of("page", "-1"),
            Map.of("sort", "secret,asc"),
            Map.of("sort", "id,wrong"),
            Map.of("nope", "x"),
            Map.of("page", "bad"))) {
      var request = get(path + "/entries").with(user("VIEWER", DEPARTMENT));
      invalid.forEach(request::param);
      mvc.perform(request).andExpect(status().isBadRequest());
    }
    mvc.perform(
            get(path + "/entries")
                .param("from", "2026-09-11")
                .param("to", "2026-01-01")
                .with(user("VIEWER", DEPARTMENT)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(path + "/entries")
                .with(user("RESEARCH_ADMIN", DEPARTMENT))
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(expense("100.01", "over"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_BUDGET"));
    mvc.perform(
            post(path + "/entries")
                .with(user("RESEARCH_ADMIN", DEPARTMENT))
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(" ".repeat(65537)))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void idempotentReplayAndReversal() {
    var grant = active();
    UUID key = UUID.randomUUID();
    var request = expense("30.01", "retry");
    var first = postings.expense(grant.id(), request, key, admin, "test");
    var replay = postings.expense(grant.id(), request, key, admin, "retry");
    assertThat(replay.replayed()).isTrue();
    assertThat(replay.entry()).isEqualTo(first.entry());
    assertThatThrownBy(
            () -> postings.expense(grant.id(), expense("30.02", "retry"), key, admin, "test"))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () -> postings.expense(grant.id(), request, UUID.randomUUID(), admin, "test"))
        .isInstanceOf(DomainException.class);
    UUID reversalKey = UUID.randomUUID();
    var reversal =
        postings.reverse(
            grant.id(),
            first.entry().id(),
            new Models.ReversalCreate("Correction"),
            reversalKey,
            admin,
            "test");
    assertThat(
            postings
                .reverse(
                    grant.id(),
                    first.entry().id(),
                    new Models.ReversalCreate("Correction"),
                    reversalKey,
                    admin,
                    "test")
                .replayed())
        .isTrue();
    assertThatThrownBy(
            () ->
                postings.reverse(
                    grant.id(),
                    first.entry().id(),
                    new Models.ReversalCreate("Again"),
                    UUID.randomUUID(),
                    admin,
                    "test"))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () ->
                postings.reverse(
                    grant.id(),
                    reversal.entry().id(),
                    new Models.ReversalCreate("Again"),
                    UUID.randomUUID(),
                    admin,
                    "test"))
        .isInstanceOf(DomainException.class);
    assertThat(queries.balance(grant.id(), admin).remaining()).isEqualTo("100.00");
    var current = queries.grant(grant.id(), admin);
    grants.close(
        grant.id(), new Models.ClosureCreate("Reconciled"), etag(current.version()), admin, "test");
    assertThat(postings.expense(grant.id(), request, key, admin, "after closure").entry())
        .isEqualTo(first.entry());
    assertThatThrownBy(
            () ->
                postings.expense(
                    grant.id(), expense("1.00", "closed"), UUID.randomUUID(), admin, "test"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void concurrentExpensesCannotOverspend() throws Exception {
    var grant = active();
    var results =
        race(
            List.of(
                () -> postOutcome(grant.id(), "80.00", "race-a", UUID.randomUUID()),
                () -> postOutcome(grant.id(), "80.00", "race-b", UUID.randomUUID())));
    assertThat(results).containsExactlyInAnyOrder("created", "INSUFFICIENT_BUDGET");
    assertThat(queries.balance(grant.id(), admin).remaining()).isEqualTo("20.00");
  }

  @Test
  void twentyConcurrentRetriesHaveOneFinancialEffect() throws Exception {
    var grant = active();
    UUID key = UUID.randomUUID();
    List<Callable<String>> tasks = new ArrayList<>();
    for (int index = 0; index < 20; index++)
      tasks.add(() -> postOutcome(grant.id(), "1.00", "same", key));
    assertThat(race(tasks)).allMatch("created"::equals);
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM ledger_entry WHERE grant_id=:id")
                .param("id", grant.id())
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "SELECT COUNT(*) FROM audit_event WHERE grant_id=:id AND action='EXPENSE_POSTED'")
                .param("id", grant.id())
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void competingReversalsHaveOneWinner() throws Exception {
    var grant = active();
    var entry =
        postings.expense(grant.id(), expense("1.00", "original"), UUID.randomUUID(), admin, "test");
    Callable<String> task =
        () -> {
          try {
            postings.reverse(
                grant.id(),
                entry.entry().id(),
                new Models.ReversalCreate("race"),
                UUID.randomUUID(),
                admin,
                "test");
            return "created";
          } catch (DomainException error) {
            return error.code();
          }
        };
    assertThat(race(List.of(task, task))).containsExactlyInAnyOrder("created", "ALREADY_REVERSED");
  }

  @Test
  void budgetAndClosureRacesPreserveInvariants() throws Exception {
    var grant = active();
    var results =
        race(
            List.of(
                () -> postOutcome(grant.id(), "80.00", "race-budget", UUID.randomUUID()),
                () -> {
                  try {
                    grants.allocate(
                        grant.id(),
                        allocation("20.00", "80.00"),
                        etag(grant.version()),
                        admin,
                        "test");
                    return "allocated";
                  } catch (DomainException error) {
                    return error.code();
                  }
                }));
    assertThat(results).hasSize(2);
    assertThat(queries.balance(grant.id(), admin).categories())
        .allSatisfy(
            category -> assertThat(new java.math.BigDecimal(category.remaining())).isNotNegative());
    var current = queries.grant(grant.id(), admin);
    var closure =
        race(
            List.of(
                () -> postOutcome(grant.id(), "1.00", "race-close", UUID.randomUUID()),
                () -> {
                  try {
                    grants.close(
                        grant.id(),
                        new Models.ClosureCreate("race"),
                        etag(current.version()),
                        admin,
                        "test");
                    return "closed";
                  } catch (DomainException error) {
                    return error.code();
                  }
                }));
    assertThat(closure).containsAnyOf("closed", "PRECONDITION_FAILED");
  }

  @Test
  void failingAuditRollsBackPostingAndKey() {
    var grant = active();
    UUID key = UUID.randomUUID();
    jdbc.sql(
            "ALTER TABLE audit_event ADD CONSTRAINT test_reject_audit CHECK (grant_id <> '"
                + grant.id()
                + "'::uuid OR action <> 'EXPENSE_POSTED') NOT VALID")
        .update();
    try {
      assertThatThrownBy(
              () ->
                  postings.expense(
                      grant.id(), expense("1.00", "audit-failure"), key, admin, "test"))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThat(queries.balance(grant.id(), admin).remaining()).isEqualTo("100.00");
      assertThat(
              jdbc.sql("SELECT COUNT(*) FROM idempotency_record WHERE key=:key")
                  .param("key", key)
                  .query(Long.class)
                  .single())
          .isZero();
    } finally {
      jdbc.sql("ALTER TABLE audit_event DROP CONSTRAINT test_reject_audit").update();
    }
    assertThat(
            postings
                .expense(grant.id(), expense("1.00", "audit-failure"), key, admin, "test")
                .replayed())
        .isFalse();
  }

  @Test
  void browserPagesAndCsrf() throws Exception {
    var grant = active();
    var login =
        oidcLogin()
            .idToken(
                token ->
                    token
                        .issuer("https://issuer.test")
                        .subject("browser")
                        .claim("roles", List.of("RESEARCH_ADMIN"))
                        .claim("department_ids", List.of(DEPARTMENT.toString())));
    for (String path :
        List.of(
            "/",
            "/grants",
            "/grants/new",
            "/grants/" + grant.id(),
            "/grants/" + grant.id() + "/audit",
            "/grants/" + grant.id() + "/close")) {
      var response = mvc.perform(get(path).with(login)).andReturn().getResponse();
      assertThat(response.getStatus()).isIn(200, 302);
    }
    mvc.perform(post("/grants/" + grant.id() + "/activation").with(login).param("version", "0"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/grants/" + grant.id() + "/metadata")
                .with(login)
                .with(csrf())
                .param("version", String.valueOf(grant.version()))
                .param("title", "Browser title")
                .param("sponsorName", "Browser sponsor"))
        .andExpect(status().is3xxRedirection());
    mvc.perform(
            get("/grants/" + grant.id())
                .with(
                    oidcLogin()
                        .idToken(
                            token ->
                                token
                                    .issuer("https://issuer.test")
                                    .subject("viewer")
                                    .claim("roles", List.of("VIEWER"))
                                    .claim("department_ids", List.of(DEPARTMENT.toString())))))
        .andExpect(status().isOk());
  }

  private String postOutcome(UUID grant, String amount, String reference, UUID key) {
    try {
      postings.expense(grant, expense(amount, reference), key, admin, "race");
      return "created";
    } catch (DomainException error) {
      return error.code();
    }
  }

  private <T> List<T> race(List<Callable<T>> tasks) throws Exception {
    try (var executor = Executors.newFixedThreadPool(tasks.size())) {
      CountDownLatch start = new CountDownLatch(1);
      var futures =
          tasks.stream()
              .map(
                  task ->
                      executor.submit(
                          () -> {
                            start.await();
                            return task.call();
                          }))
              .toList();
      start.countDown();
      List<T> results = new ArrayList<>();
      for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
      return results;
    }
  }
}
