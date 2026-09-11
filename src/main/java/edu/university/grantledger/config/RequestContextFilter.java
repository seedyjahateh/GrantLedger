package edu.university.grantledger.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component("grantLedgerRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(RequestContextFilter.class);
  private final ObjectMapper json;

  public RequestContextFilter(ObjectMapper json) {
    this.json = json;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString();
    long started = System.nanoTime();
    request.setAttribute("requestId", requestId);
    response.setHeader("X-Request-ID", requestId);
    MDC.put("traceId", requestId);
    try {
      byte[] body = null;
      if (request.getRequestURI().startsWith("/api/")
          && request.getContentType() != null
          && request.getContentType().toLowerCase(java.util.Locale.ROOT).contains("json"))
        body = request.getInputStream().readNBytes(65537);
      if (request.getContentLengthLong() > 65536 || body != null && body.length > 65536) {
        response.setStatus(413);
        response.setContentType("application/problem+json");
        json.writeValue(
            response.getOutputStream(),
            Map.of(
                "type",
                "urn:grantledger:problem:request-too-large",
                "status",
                413,
                "title",
                "Request too large",
                "detail",
                "Request bodies may not exceed 64 KiB.",
                "code",
                "REQUEST_TOO_LARGE",
                "traceId",
                requestId,
                "instance",
                request.getRequestURI()));
        return;
      }
      chain.doFilter(body == null ? request : new BodyRequest(request, body), response);
    } finally {
      Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      LOG.info(
          "request method={} route={} status={} durationMs={}",
          request.getMethod(),
          route == null ? "unmapped" : route,
          response.getStatus(),
          (System.nanoTime() - started) / 1000000);
      MDC.remove("traceId");
    }
  }

  private static final class BodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    BodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
      var input = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return input.read();
        }

        @Override
        public boolean isFinished() {
          return input.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException("Synchronous request body");
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
  }
}
