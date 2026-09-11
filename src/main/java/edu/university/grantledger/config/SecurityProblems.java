package edu.university.grantledger.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

final class SecurityProblems implements AuthenticationEntryPoint, AccessDeniedHandler {
  private final ObjectMapper json;

  SecurityProblems(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException error)
      throws IOException {
    response.setHeader("WWW-Authenticate", "Bearer");
    write(request, response, 401, "UNAUTHENTICATED", "A valid bearer access token is required.");
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException error)
      throws IOException {
    write(request, response, 403, "FORBIDDEN", "Your role does not permit this operation.");
  }

  private void write(
      HttpServletRequest request,
      HttpServletResponse response,
      int status,
      String code,
      String detail)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    json.writeValue(
        response.getOutputStream(),
        Map.of(
            "type",
            "urn:grantledger:problem:" + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
            "title",
            code,
            "status",
            status,
            "detail",
            detail,
            "instance",
            request.getRequestURI(),
            "code",
            code,
            "traceId",
            String.valueOf(request.getAttribute("requestId"))));
  }
}
