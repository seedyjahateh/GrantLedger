package edu.university.grantledger.web;

import edu.university.grantledger.domain.DomainException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ApiController.class)
public class ApiErrors {
  private final MeterRegistry metrics;

  public ApiErrors(MeterRegistry metrics) {
    this.metrics = metrics;
  }

  @ExceptionHandler(DomainException.class)
  ResponseEntity<ProblemDetail> domain(DomainException error, HttpServletRequest request) {
    metrics.counter("ledger.rejections", "code", error.code()).increment();
    return problem(error.status(), error.code(), error.getMessage(), error.details(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(
      MethodArgumentNotValidException error, HttpServletRequest request) {
    var fields =
        error.getBindingResult().getFieldErrors().stream()
            .map(
                field ->
                    Map.of(
                        "field",
                        field.getField(),
                        "message",
                        String.valueOf(field.getDefaultMessage())))
            .toList();
    return problem(
        400,
        "VALIDATION_FAILED",
        "Correct the invalid fields.",
        Map.of("fieldErrors", fields),
        request);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingRequestHeaderException.class,
    IllegalArgumentException.class,
    DateTimeParseException.class
  })
  ResponseEntity<ProblemDetail> invalid(Exception error, HttpServletRequest request) {
    return problem(
        400,
        "INVALID_REQUEST",
        "The request format, parameter, or required header is invalid.",
        Map.of(),
        request);
  }

  @ExceptionHandler(CannotAcquireLockException.class)
  ResponseEntity<ProblemDetail> contention(Exception error, HttpServletRequest request) {
    metrics.counter("ledger.lock.timeouts").increment();
    return problem(
        409,
        "CONCURRENT_OPERATION",
        "Another operation holds the grant lock. Retry with the same idempotency key.",
        Map.of(),
        request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict(Exception error, HttpServletRequest request) {
    return problem(
        409,
        "DATA_CONFLICT",
        "The operation conflicts with an existing resource or data constraint.",
        Map.of(),
        request);
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ProblemDetail> database(Exception error, HttpServletRequest request) {
    return problem(
        503,
        "SERVICE_UNAVAILABLE",
        "The database operation could not complete. Retry later.",
        Map.of(),
        request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception error, HttpServletRequest request) {
    org.slf4j.LoggerFactory.getLogger(ApiErrors.class)
        .error(
            "Unhandled request failure class={} traceId={}",
            error.getClass().getSimpleName(),
            ApiController.requestId(request));
    return problem(
        500,
        "INTERNAL_ERROR",
        "The operation could not complete. Contact support with the trace ID.",
        Map.of(),
        request);
  }

  private ResponseEntity<ProblemDetail> problem(
      int status,
      String code,
      String detail,
      Map<String, Object> properties,
      HttpServletRequest request) {
    var body = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    body.setType(
        URI.create(
            "urn:grantledger:problem:"
                + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
    body.setTitle(code);
    body.setInstance(URI.create(request.getRequestURI()));
    body.setProperty("code", code);
    body.setProperty("traceId", ApiController.requestId(request));
    properties.forEach(body::setProperty);
    return ResponseEntity.status(status).body(body);
  }
}
