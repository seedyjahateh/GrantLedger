package edu.university.grantledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class MigrationIT extends DatabaseTestSupport {
  @Autowired DataSource dataSource;

  @Test
  void upgradesThePreviousSchemaWithoutLosingRecords() {
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    var previous =
        Flyway.configure()
            .dataSource(dataSource)
            .schemas(schema)
            .defaultSchema(schema)
            .locations("classpath:db/migration")
            .target("1")
            .load();
    previous.migrate();
    var jdbc = JdbcClient.create(dataSource);
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO "
                + schema
                + ".department(id,code,name,active) VALUES (:id,'UPGRADE','Migration fixture',true)")
        .param("id", id)
        .update();
    var current =
        Flyway.configure()
            .dataSource(dataSource)
            .schemas(schema)
            .defaultSchema(schema)
            .locations("classpath:db/migration")
            .load();
    current.migrate();
    current.validate();
    assertThat(
            jdbc.sql("SELECT name FROM " + schema + ".department WHERE id=:id")
                .param("id", id)
                .query(String.class)
                .single())
        .isEqualTo("Migration fixture");
    assertThat(
            jdbc.sql(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=:schema AND table_name='spring_session'")
                .param("schema", schema)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }
}
