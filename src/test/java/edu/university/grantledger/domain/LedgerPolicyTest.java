package edu.university.grantledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class LedgerPolicyTest {
  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {"", "1", "1.0", "1.001", "01.00", "-1.00", "1e2", "1000000000.00", " 1.00", "NaN"})
  void rejectsInvalidMoney(String value) {
    assertThatThrownBy(() -> LedgerPolicy.money(value, false)).isInstanceOf(DomainException.class);
  }

  @Test
  void exactMoney() {
    assertThat(LedgerPolicy.money("125.40", true)).isEqualByComparingTo("125.40");
    assertThat(LedgerPolicy.money("0.00", false)).isEqualByComparingTo("0");
    assertThat(LedgerPolicy.money("999999999.99", true)).isEqualByComparingTo("999999999.99");
    assertThatThrownBy(() -> LedgerPolicy.money("0.00", true)).isInstanceOf(DomainException.class);
  }

  @Test
  void datesAndState() {
    var today = LocalDate.of(2026, 9, 11);
    LedgerPolicy.active(GrantStatus.ACTIVE);
    LedgerPolicy.dates(today, today);
    LedgerPolicy.expenseDate(today, today.minusDays(1), today.plusDays(1), today);
    assertThatThrownBy(() -> LedgerPolicy.active(GrantStatus.DRAFT))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> LedgerPolicy.dates(null, today)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> LedgerPolicy.dates(today, null)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> LedgerPolicy.dates(today, today.minusDays(1)))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> LedgerPolicy.expenseDate(null, today, today, today))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> LedgerPolicy.expenseDate(today.minusDays(1), today, today, today))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> LedgerPolicy.expenseDate(today.plusDays(1), today, today, today))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () -> LedgerPolicy.expenseDate(today.plusDays(1), today, today.plusDays(2), today))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void enforcesAllocationAndAvailability() {
    var amounts = new EnumMap<Category, BigDecimal>(Category.class);
    for (var category : Category.values()) amounts.put(category, BigDecimal.ZERO);
    amounts.put(Category.TRAVEL, new BigDecimal("100.00"));
    LedgerPolicy.budgets(GrantStatus.ACTIVE, new BigDecimal("100.00"), amounts, Map.of());
    LedgerPolicy.budgets(GrantStatus.DRAFT, new BigDecimal("200.00"), amounts, Map.of());
    LedgerPolicy.available(new BigDecimal("100.00"), new BigDecimal("100.00"));
    assertThatThrownBy(
            () -> LedgerPolicy.available(new BigDecimal("100.00"), new BigDecimal("100.01")))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.status()).isEqualTo(409);
              assertThat(e.code()).isEqualTo("INSUFFICIENT_BUDGET");
              assertThat(e.details()).containsEntry("availableAmount", "100.00");
            });
    assertThatThrownBy(
            () ->
                LedgerPolicy.budgets(GrantStatus.CLOSED, new BigDecimal("100"), amounts, Map.of()))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () ->
                LedgerPolicy.budgets(GrantStatus.ACTIVE, new BigDecimal("200"), amounts, Map.of()))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () -> LedgerPolicy.budgets(GrantStatus.DRAFT, new BigDecimal("50"), amounts, Map.of()))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () ->
                LedgerPolicy.budgets(
                    GrantStatus.ACTIVE,
                    new BigDecimal("100"),
                    amounts,
                    Map.of(Category.TRAVEL, new BigDecimal("101"))))
        .isInstanceOf(DomainException.class);
    amounts.put(Category.OTHER, new BigDecimal("-1"));
    assertThatThrownBy(
            () -> LedgerPolicy.budgets(GrantStatus.DRAFT, new BigDecimal("100"), amounts, Map.of()))
        .isInstanceOf(DomainException.class);
    amounts.put(Category.OTHER, null);
    assertThatThrownBy(
            () -> LedgerPolicy.budgets(GrantStatus.DRAFT, new BigDecimal("100"), amounts, Map.of()))
        .isInstanceOf(DomainException.class);
    amounts.remove(Category.OTHER);
    assertThatThrownBy(
            () -> LedgerPolicy.budgets(GrantStatus.DRAFT, new BigDecimal("100"), amounts, Map.of()))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void rolesAreExplicitAndDefensivelyCopied() {
    var admin = new Actor("issuer", "admin", Set.of("RESEARCH_ADMIN"), Set.of(UUID.randomUUID()));
    admin.requireAdmin();
    admin.requireAuditor();
    admin.requireReader();
    var auditor = new Actor("issuer", "audit", Set.of("AUDITOR"), Set.of());
    auditor.requireReader();
    auditor.requireAuditor();
    assertThatThrownBy(auditor::requireAdmin).isInstanceOf(DomainException.class);
    var viewer = new Actor("issuer", "view", Set.of("VIEWER"), Set.of());
    viewer.requireReader();
    assertThatThrownBy(viewer::requireAuditor).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> new Actor("issuer", "none", Set.of(), Set.of()).requireReader())
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> admin.roles().add("OTHER"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
