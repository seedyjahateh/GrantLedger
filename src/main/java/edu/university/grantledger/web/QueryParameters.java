package edu.university.grantledger.web;

import edu.university.grantledger.application.Models;
import edu.university.grantledger.domain.Category;
import edu.university.grantledger.domain.DomainException;
import edu.university.grantledger.domain.EntryType;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class QueryParameters {
  private QueryParameters() {}

  static void allowed(Map<String, String> parameters, Set<String> allowed) {
    if (!allowed.containsAll(parameters.keySet()))
      throw new DomainException(
          400, "INVALID_QUERY", "An unsupported query parameter was supplied.");
    if (parameters.values().stream().anyMatch(value -> value.length() > 200))
      throw new DomainException(
          400, "INVALID_QUERY", "Query parameter values cannot exceed 200 characters.");
  }

  static Pageable page(Map<String, String> parameters, String defaultSort, Set<String> fields) {
    int page = Integer.parseInt(parameters.getOrDefault("page", "0"));
    int size = Integer.parseInt(parameters.getOrDefault("size", "25"));
    if (page < 0 || size < 1 || size > 100)
      throw new DomainException(
          400, "INVALID_PAGE", "Page must be nonnegative; size must be between 1 and 100.");
    String[] sort = parameters.getOrDefault("sort", defaultSort + ",desc").split(",", -1);
    if (sort.length != 2
        || !fields.contains(sort[0])
        || !(sort[1].equals("asc") || sort[1].equals("desc")))
      throw new DomainException(
          400, "INVALID_SORT", "Use a documented sort field followed by asc or desc.");
    Sort ordering = Sort.by(Sort.Direction.fromString(sort[1]), sort[0]);
    if (!sort[0].equals("id"))
      ordering = ordering.and(Sort.by(Sort.Direction.fromString(sort[1]), "id"));
    return PageRequest.of(page, size, ordering);
  }

  static Models.EntryFilter filter(Map<String, String> parameters) {
    return new Models.EntryFilter(
        parameters.containsKey("category") ? Category.valueOf(parameters.get("category")) : null,
        parameters.containsKey("type") ? EntryType.valueOf(parameters.get("type")) : null,
        parameters.containsKey("from") && !parameters.get("from").isBlank()
            ? LocalDate.parse(parameters.get("from"))
            : null,
        parameters.containsKey("to") && !parameters.get("to").isBlank()
            ? LocalDate.parse(parameters.get("to"))
            : null,
        parameters.get("externalReference"));
  }
}
