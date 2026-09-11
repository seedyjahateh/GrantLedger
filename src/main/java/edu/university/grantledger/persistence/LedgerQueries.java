package edu.university.grantledger.persistence;

import edu.university.grantledger.domain.Category;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerQueries {
  private final JdbcClient jdbc;

  public LedgerQueries(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Map<Category, BigDecimal> expenditure(UUID grantId) {
    Map<Category, BigDecimal> result = new EnumMap<>(Category.class);
    jdbc.sql(
            """
        SELECT b.category, COALESCE(SUM(CASE WHEN e.type = 'EXPENSE' THEN e.amount ELSE -e.amount END), 0.00) AS spent
        FROM budget_allocation b LEFT JOIN ledger_entry e ON e.budget_allocation_id = b.id AND e.grant_id = b.grant_id
        WHERE b.grant_id = :id GROUP BY b.category
        """)
        .param("id", grantId)
        .query(
            (row, index) ->
                Map.entry(Category.valueOf(row.getString("category")), row.getBigDecimal("spent")))
        .list()
        .forEach(pair -> result.put(pair.getKey(), pair.getValue()));
    return result;
  }
}
