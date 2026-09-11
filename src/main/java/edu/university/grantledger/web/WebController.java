package edu.university.grantledger.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.grantledger.application.GrantService;
import edu.university.grantledger.application.Models;
import edu.university.grantledger.application.PostingService;
import edu.university.grantledger.application.QueryService;
import edu.university.grantledger.config.ActorResolver;
import edu.university.grantledger.domain.Category;
import edu.university.grantledger.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WebController {
  private final GrantService grants;
  private final QueryService queries;
  private final PostingService postings;
  private final ActorResolver actors;
  private final ObjectMapper json;
  private final Clock clock;

  public WebController(
      GrantService grants,
      QueryService queries,
      PostingService postings,
      ActorResolver actors,
      ObjectMapper json,
      Clock clock) {
    this.grants = grants;
    this.queries = queries;
    this.postings = postings;
    this.actors = actors;
    this.json = json;
    this.clock = clock;
  }

  @ModelAttribute
  public void common(Model model, Authentication auth) {
    var actor = actors.resolve(auth);
    actor.requireReader();
    model.addAttribute("admin", actor.admin());
    model.addAttribute("auditor", actor.auditor());
    model.addAttribute("categories", Category.values());
    model.addAttribute("today", LocalDate.now(clock));
  }

  @GetMapping("/")
  public String home() {
    return "redirect:/grants";
  }

  @GetMapping("/grants")
  public String list(@RequestParam Map<String, String> params, Authentication auth, Model model) {
    QueryParameters.allowed(params, Set.of("page", "search"));
    model.addAttribute(
        "results",
        queries.grants(
            actors.resolve(auth),
            null,
            null,
            params.get("search"),
            QueryParameters.page(params, "createdAt", Set.of("createdAt"))));
    model.addAttribute("search", params.getOrDefault("search", ""));
    return "grants";
  }

  @GetMapping("/grants/new")
  public String createForm(Authentication auth, Model model) {
    actors.resolve(auth).requireAdmin();
    model.addAttribute("departments", queries.departments(actors.resolve(auth)));
    model.addAttribute(
        "form",
        new Models.GrantCreate(
            "", null, "", "", LocalDate.now(clock), LocalDate.now(clock).plusYears(1), ""));
    return "grant-new";
  }

  @PostMapping("/grants")
  public String create(
      @Valid @ModelAttribute("form") Models.GrantCreate form,
      BindingResult errors,
      Authentication auth,
      Model model,
      HttpServletRequest http) {
    model.addAttribute("departments", queries.departments(actors.resolve(auth)));
    if (errors.hasErrors()) return "grant-new";
    try {
      return redirect(
          grants.create(form, actors.resolve(auth), ApiController.requestId(http)).id());
    } catch (DomainException error) {
      if (error.status() == 403 || error.status() == 404) throw error;
      model.addAttribute("problem", error.getMessage());
      return "grant-new";
    }
  }

  @GetMapping("/grants/{id}")
  public String detail(
      @PathVariable UUID id,
      @RequestParam Map<String, String> params,
      Authentication auth,
      Model model) {
    QueryParameters.allowed(
        params, Set.of("page", "category", "type", "from", "to", "externalReference"));
    details(id, params, auth, model);
    model.addAttribute(
        "expense", new Models.ExpenseCreate(Category.OTHER, "", LocalDate.now(clock), "", ""));
    model.addAttribute("idempotencyKey", UUID.randomUUID());
    return "grant-detail";
  }

  @PostMapping("/grants/{id}/expenses")
  public String expense(
      @PathVariable UUID id,
      @Valid @ModelAttribute("expense") Models.ExpenseCreate form,
      BindingResult errors,
      @RequestParam UUID idempotencyKey,
      Authentication auth,
      Model model,
      HttpServletRequest http) {
    if (!errors.hasErrors()) {
      try {
        postings.expense(
            id, form, idempotencyKey, actors.resolve(auth), ApiController.requestId(http));
        return redirect(id);
      } catch (DomainException error) {
        if (error.status() == 403 || error.status() == 404) throw error;
        model.addAttribute("problem", error.getMessage());
      }
    }
    details(id, Map.of(), auth, model);
    model.addAttribute("idempotencyKey", idempotencyKey);
    return "grant-detail";
  }

  @PostMapping("/grants/{id}/metadata")
  public String metadata(
      @PathVariable UUID id,
      @RequestParam String title,
      @RequestParam String sponsorName,
      @RequestParam long version,
      Authentication auth,
      HttpServletRequest http) {
    grants.patch(
        id,
        json.valueToTree(Map.of("title", title, "sponsorName", sponsorName)),
        ApiController.etag(version),
        actors.resolve(auth),
        ApiController.requestId(http));
    return redirect(id);
  }

  @PostMapping("/grants/{id}/budgets")
  public String budgets(
      @PathVariable UUID id,
      @RequestParam Map<String, String> params,
      Authentication auth,
      HttpServletRequest http) {
    var allocations =
        Arrays.stream(Category.values())
            .map(category -> new Models.Allocation(category, params.get(category.name())))
            .toList();
    grants.allocate(
        id,
        new Models.BudgetsUpdate(allocations, params.get("reason")),
        ApiController.etag(Long.parseLong(params.get("version"))),
        actors.resolve(auth),
        ApiController.requestId(http));
    return redirect(id);
  }

  @PostMapping("/grants/{id}/activation")
  public String activate(
      @PathVariable UUID id,
      @RequestParam long version,
      Authentication auth,
      HttpServletRequest http) {
    grants.activate(
        id, ApiController.etag(version), actors.resolve(auth), ApiController.requestId(http));
    return redirect(id);
  }

  @GetMapping("/grants/{id}/entries/{entryId}/reverse")
  public String reverseForm(
      @PathVariable UUID id, @PathVariable UUID entryId, Authentication auth, Model model) {
    actors.resolve(auth).requireAdmin();
    model.addAttribute("grant", queries.grant(id, actors.resolve(auth)));
    model.addAttribute("entry", queries.entry(id, entryId, actors.resolve(auth)));
    model.addAttribute("idempotencyKey", UUID.randomUUID());
    return "reverse";
  }

  @PostMapping("/grants/{id}/entries/{entryId}/reverse")
  public String reverse(
      @PathVariable UUID id,
      @PathVariable UUID entryId,
      @RequestParam String reason,
      @RequestParam UUID idempotencyKey,
      @RequestParam boolean confirmed,
      Authentication auth,
      HttpServletRequest http) {
    if (!confirmed)
      throw new DomainException(400, "CONFIRMATION_REQUIRED", "Confirm the full reversal.");
    postings.reverse(
        id,
        entryId,
        new Models.ReversalCreate(reason),
        idempotencyKey,
        actors.resolve(auth),
        ApiController.requestId(http));
    return redirect(id);
  }

  @GetMapping("/grants/{id}/close")
  public String closeForm(@PathVariable UUID id, Authentication auth, Model model) {
    actors.resolve(auth).requireAdmin();
    model.addAttribute("grant", queries.grant(id, actors.resolve(auth)));
    return "close";
  }

  @PostMapping("/grants/{id}/close")
  public String close(
      @PathVariable UUID id,
      @RequestParam String reconciliationNote,
      @RequestParam long version,
      @RequestParam boolean confirmed,
      Authentication auth,
      HttpServletRequest http) {
    if (!confirmed)
      throw new DomainException(
          400, "CONFIRMATION_REQUIRED", "Confirm reconciliation and permanent closure.");
    grants.close(
        id,
        new Models.ClosureCreate(reconciliationNote),
        ApiController.etag(version),
        actors.resolve(auth),
        ApiController.requestId(http));
    return redirect(id);
  }

  @GetMapping("/grants/{id}/activity.csv")
  public ResponseEntity<String> export(
      @PathVariable UUID id,
      @RequestParam Map<String, String> params,
      Authentication auth,
      HttpServletRequest http) {
    QueryParameters.allowed(params, Set.of("from", "to", "category", "type", "externalReference"));
    return ResponseEntity.ok()
        .header("Content-Type", "text/csv;charset=UTF-8")
        .header("Content-Disposition", "attachment; filename=grant-" + id + ".csv")
        .body(
            queries.export(
                id,
                QueryParameters.filter(params),
                actors.resolve(auth),
                ApiController.requestId(http)));
  }

  @GetMapping("/grants/{id}/audit")
  public String audit(
      @PathVariable UUID id,
      @RequestParam Map<String, String> params,
      Authentication auth,
      Model model) {
    QueryParameters.allowed(params, Set.of("page"));
    model.addAttribute("grant", queries.grant(id, actors.resolve(auth)));
    model.addAttribute(
        "results",
        queries.audit(
            id,
            QueryParameters.page(params, "occurredAt", Set.of("occurredAt")),
            actors.resolve(auth)));
    return "audit";
  }

  private void details(UUID id, Map<String, String> params, Authentication auth, Model model) {
    var actor = actors.resolve(auth);
    model.addAttribute("grant", queries.grant(id, actor));
    model.addAttribute("balance", queries.balance(id, actor));
    model.addAttribute("budgets", queries.budgets(id, actor));
    model.addAttribute("params", params);
    model.addAttribute(
        "entries",
        queries.entries(
            id,
            QueryParameters.filter(params),
            QueryParameters.page(params, "effectiveDate", Set.of("effectiveDate")),
            actor));
  }

  private String redirect(UUID id) {
    return "redirect:/grants/" + id;
  }
}
