package edu.university.grantledger.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.grantledger.application.Models;
import edu.university.grantledger.domain.Actor;
import edu.university.grantledger.domain.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyStore {
  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final Clock clock;

  public IdempotencyStore(JdbcClient jdbc, ObjectMapper json, Clock clock) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
  }

  public String hash(Object normalizedRequest) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(json.writeValueAsBytes(normalizedRequest)));
    } catch (JsonProcessingException | NoSuchAlgorithmException error) {
      throw new IllegalStateException("Cannot fingerprint request", error);
    }
  }

  public Optional<Models.PostingResult> replay(
      Actor actor, UUID grantId, String operation, UUID key, String hash) {
    return jdbc.sql(
            "SELECT request_hash,response_body,response_location FROM idempotency_record WHERE actor_issuer=:issuer AND actor_subject=:subject AND grant_id=:grant AND operation=:operation AND key=:key")
        .param("issuer", actor.issuer())
        .param("subject", actor.subject())
        .param("grant", grantId)
        .param("operation", operation)
        .param("key", key)
        .query(
            (row, index) -> {
              if (!MessageDigest.isEqual(
                  hash.getBytes(StandardCharsets.US_ASCII),
                  row.getString("request_hash").getBytes(StandardCharsets.US_ASCII))) {
                throw new DomainException(
                    409,
                    "IDEMPOTENCY_KEY_REUSED",
                    "The idempotency key was already used for a different request.");
              }
              try {
                return new Models.PostingResult(
                    json.readValue(row.getString("response_body"), Models.EntryView.class),
                    row.getString("response_location"),
                    true);
              } catch (JsonProcessingException error) {
                throw new IllegalStateException("Cannot replay posting", error);
              }
            })
        .optional();
  }

  public void save(
      Actor actor,
      UUID grantId,
      String operation,
      UUID key,
      String hash,
      Models.PostingResult result) {
    try {
      jdbc.sql(
              """
          INSERT INTO idempotency_record(id,actor_issuer,actor_subject,grant_id,operation,key,request_hash,response_status,response_body,response_location,created_at)
          VALUES (:id,:issuer,:subject,:grant,:operation,:key,:hash,201,:body,:location,:now)
          """)
          .param("id", UUID.randomUUID())
          .param("issuer", actor.issuer())
          .param("subject", actor.subject())
          .param("grant", grantId)
          .param("operation", operation)
          .param("key", key)
          .param("hash", hash)
          .param("body", json.writeValueAsString(result.entry()))
          .param("location", result.location())
          .param("now", clock.instant().atOffset(ZoneOffset.UTC))
          .update();
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Cannot store posting result", error);
    }
  }
}
