package edu.university.grantledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Pattern;

public final class LedgerPolicy {
  private static final Pattern MONEY = Pattern.compile("^(0|[1-9][0-9]{0,8})\\.[0-9]{2}$");

  private LedgerPolicy() {}

  public static BigDecimal money(String value, boolean positive) {
    if (value == null || !MONEY.matcher(value).matches()) {
      throw new DomainException(
          400,
          "INVALID_MONEY",
          "Use a decimal string with exactly two fractional digits, up to 999999999.99.");
    }
    BigDecimal amount = new BigDecimal(value);
    if (positive && amount.signum() == 0) {
      throw new DomainException(400, "INVALID_MONEY", "Amount must be positive.");
    }
    return amount;
  }

  public static void active(GrantStatus status) {
    if (status != GrantStatus.ACTIVE) {
      throw new DomainException(409, "INVALID_GRANT_STATE", "The grant must be active.");
    }
  }

  public static void dates(LocalDate start, LocalDate end) {
    if (start == null || end == null || start.isAfter(end)) {
      throw new DomainException(400, "INVALID_DATES", "Start date must be on or before end date.");
    }
  }

  public static void expenseDate(LocalDate date, LocalDate start, LocalDate end, LocalDate today) {
    if (date == null || date.isBefore(start) || date.isAfter(end) || date.isAfter(today)) {
      throw new DomainException(
          400,
          "INVALID_EFFECTIVE_DATE",
          "Expense date must be within the award dates and not in the future.");
    }
  }

  public static void available(BigDecimal remaining, BigDecimal amount) {
    if (amount.compareTo(remaining) > 0) {
      throw new DomainException(
          409,
          "INSUFFICIENT_BUDGET",
          "The expense exceeds the remaining category budget.",
          Map.of(
              "availableAmount",
              remaining.toPlainString(),
              "requestedAmount",
              amount.toPlainString()));
    }
  }

  public static void budgets(
      GrantStatus status,
      BigDecimal award,
      Map<Category, BigDecimal> amounts,
      Map<Category, BigDecimal> spent) {
    if (status == GrantStatus.CLOSED)
      throw new DomainException(409, "INVALID_GRANT_STATE", "Closed grants are read-only.");
    if (amounts.size() != Category.values().length)
      throw new DomainException(
          400, "INVALID_BUDGETS", "Supply each of the five categories exactly once.");
    BigDecimal total = BigDecimal.ZERO;
    for (Category category : Category.values()) {
      BigDecimal amount = amounts.get(category);
      if (amount == null || amount.signum() < 0)
        throw new DomainException(400, "INVALID_BUDGETS", "All allocations must be nonnegative.");
      if (amount.compareTo(spent.getOrDefault(category, BigDecimal.ZERO)) < 0)
        throw new DomainException(
            409,
            "BUDGET_BELOW_EXPENDITURE",
            "An allocation cannot fall below its net expenditure.");
      total = total.add(amount);
    }
    if (total.compareTo(award) > 0
        || (status == GrantStatus.ACTIVE && total.compareTo(award) != 0)) {
      throw new DomainException(
          409,
          "INVALID_BUDGET_TOTAL",
          "Allocations must not exceed the award and must equal it for active grants.");
    }
  }
}
