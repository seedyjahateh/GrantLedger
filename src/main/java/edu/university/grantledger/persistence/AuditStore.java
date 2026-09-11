package edu.university.grantledger.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.grantledger.application.Models;
import edu.university.grantledger.domain.Actor;
import java.sql.Types;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AuditStore {
  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final Clock clock;

  public AuditStore(JdbcClient jdbc, ObjectMapper json, Clock clock) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
  }

  public void append(
      UUID grantId,
      String action,
      String resourceType,
      UUID resourceId,
      Actor actor,
      Object before,
      Object after,
      String reason,
      String requestId) {
    jdbc.sql(
            """
        INSERT INTO audit_event(id,grant_id,action,resource_type,resource_id,actor_issuer,actor_subject,occurred_at,before_values,after_values,reason,request_id)
        VALUES (:id,:grant,:action,:type,:resource,:issuer,:subject,:now,CAST(:before AS jsonb),CAST(:after AS jsonb),:reason,:request)
        """)
        .param("id", UUID.randomUUID())
        .param("grant", grantId)
        .param("action", action)
        .param("type", resourceType)
        .param("resource", resourceId)
        .param("issuer", actor.issuer())
        .param("subject", actor.subject())
        .param("now", clock.instant().atOffset(java.time.ZoneOffset.UTC))
        .param("before", encode(before))
        .param("after", encode(after))
        .param("reason", reason, Types.VARCHAR)
        .param("request", requestId)
        .update();
  }

  public long count(UUID grantId) {
    return jdbc.sql("SELECT COUNT(*) FROM audit_event WHERE grant_id=:id")
        .param("id", grantId)
        .query(Long.class)
        .single();
  }

  public List<Models.AuditView> list(UUID grantId, int size, long offset) {
    return jdbc.sql(
            "SELECT * FROM audit_event WHERE grant_id=:id ORDER BY occurred_at DESC,id DESC LIMIT :size OFFSET :offset")
        .param("id", grantId)
        .param("size", size)
        .param("offset", offset)
        .query(
            (row, index) ->
                new Models.AuditView(
                    row.getObject("id", UUID.class),
                    row.getString("action"),
                    row.getString("resource_type"),
                    row.getObject("resource_id", UUID.class),
                    row.getString("actor_issuer"),
                    row.getString("actor_subject"),
                    row.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant(),
                    decode(row.getString("before_values")),
                    decode(row.getString("after_values")),
                    row.getString("reason"),
                    row.getString("request_id")))
        .list();
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Cannot encode audit record", error);
    }
  }

  private Map<String, Object> decode(String value) {
    try {
      return json.readValue(value, new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Cannot decode audit record", error);
    }
  }
}
