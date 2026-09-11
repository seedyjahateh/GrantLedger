package edu.university.grantledger.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.university.grantledger.domain.Actor;
import edu.university.grantledger.domain.DomainException;
import edu.university.grantledger.persistence.GrantRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class AuditFailureTest {
  @Test
  void missingGrantIsNotFoundWithoutWrite() {
    var repository = mock(GrantRepository.class);
    when(repository.findById(any())).thenReturn(Optional.empty());
    var access =
        new GrantAccess(
            repository, mock(JdbcClient.class), mock(jakarta.persistence.EntityManager.class));
    assertThatThrownBy(
            () ->
                access.read(
                    UUID.randomUUID(), new Actor("issuer", "subject", Set.of("VIEWER"), Set.of())))
        .isInstanceOfSatisfying(
            DomainException.class,
            error -> org.assertj.core.api.Assertions.assertThat(error.status()).isEqualTo(404));
  }

  @Test
  void textIsValidatedAndCsvCannotExecuteFormula() {
    org.assertj.core.api.Assertions.assertThat(GrantService.text(" hi ", 2)).isEqualTo("hi");
    assertThatThrownBy(() -> GrantService.text(null, 5)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> GrantService.text(" ", 5)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> GrantService.text("long", 2)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> GrantService.text("a\0b", 5)).isInstanceOf(DomainException.class);
    for (String value :
        new String[] {"=1+1", "  +cmd", "-formula", "@test", "\ttext", "\rtext", "\ntext"})
      org.assertj.core.api.Assertions.assertThat(QueryService.csv(value)).startsWith("\"'");
    org.assertj.core.api.Assertions.assertThat(QueryService.csv(null)).isEqualTo("\"\"");
    org.assertj.core.api.Assertions.assertThat(QueryService.csv("hello \"world\""))
        .isEqualTo("\"hello \"\"world\"\"\"");
  }
}
