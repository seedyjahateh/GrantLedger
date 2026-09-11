package edu.university.grantledger.domain;

import java.util.Map;

public class DomainException extends RuntimeException {
  private final int status;
  private final String code;
  private final Map<String, Object> details;

  public DomainException(int status, String code, String message) {
    this(status, code, message, Map.of());
  }

  public DomainException(int status, String code, String message, Map<String, Object> details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = Map.copyOf(details);
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public Map<String, Object> details() {
    return details;
  }
}
