package edu.university.grantledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MvcResult;

class ContractIT extends DatabaseTestSupport {
  @Autowired ObjectMapper json;

  @Test
  void runtimeOperationCatalogMatchesReviewedContract() throws Exception {
    var reviewed = json.readTree(Path.of("openapi/grantledger-v1.yaml").toFile());
    var response =
        mvc.perform(get("/v3/api-docs/v1").with(oidcLogin()))
            .andExpect(status().isOk())
            .andReturn();
    var runtime = json.readTree(response.getResponse().getContentAsString());
    Set<String> expected = catalog(reviewed);
    Set<String> actual = catalog(runtime);
    assertThat(expected).hasSize(16);
    assertThat(actual).isEqualTo(expected);
  }

  private Set<String> catalog(JsonNode spec) {
    Set<String> result = new HashSet<>();
    spec.get("paths")
        .fields()
        .forEachRemaining(
            path ->
                path.getValue()
                    .fields()
                    .forEachRemaining(
                        operation -> {
                          if (Set.of("get", "post", "patch", "put", "delete")
                              .contains(operation.getKey()))
                            result.add(
                                operation.getKey()
                                    + " "
                                    + path.getKey()
                                    + " "
                                    + operation.getValue().path("operationId").asText());
                        }));
    return result;
  }

  @TestConfiguration
  public static class ContractChecks {
    @Bean
    MockMvcBuilderCustomizer contractAssertions(ObjectMapper json) throws Exception {
      JsonNode spec = json.readTree(Path.of("openapi/grantledger-v1.yaml").toFile());
      return builder -> builder.alwaysExpect(result -> checkResponse(spec, result, json));
    }

    private void checkResponse(JsonNode spec, MvcResult result, ObjectMapper json)
        throws Exception {
      String uri = result.getRequest().getRequestURI();
      if (!uri.startsWith("/api/v1/")) return;
      var paths = spec.get("paths").fields();
      while (paths.hasNext()) {
        var path = paths.next();
        String pattern = path.getKey().replace("{id}", "[^/]+").replace("{entryId}", "[^/]+");
        if (!uri.matches(pattern)) continue;
        var operation =
            path.getValue()
                .path(result.getRequest().getMethod().toLowerCase(java.util.Locale.ROOT));
        if (operation.isMissingNode()) continue;
        int status = result.getResponse().getStatus();
        var response = operation.path("responses").path(String.valueOf(status));
        assertThat(response.isMissingNode())
            .as("Documented status for %s: %s", uri, status)
            .isFalse();
        if (result.getResponse().getContentType() != null
            && result.getResponse().getContentType().startsWith("text/csv")) return;
        var schema = response.path("content").elements().next().get("schema");
        ObjectNode root = json.createObjectNode();
        root.setAll((ObjectNode) schema.deepCopy());
        root.set("components", spec.get("components").deepCopy());
        nullable(root);
        var validator = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(root);
        assertThat(validator.validate(json.readTree(result.getResponse().getContentAsString())))
            .as("Contract response for %s", uri)
            .isEmpty();
        return;
      }
    }

    private void nullable(JsonNode node) {
      if (node instanceof ObjectNode object) {
        if (object.path("nullable").asBoolean() && object.path("type").isTextual()) {
          String type = object.get("type").asText();
          object.putArray("type").add(type).add("null");
          object.remove("nullable");
        }
        object.elements().forEachRemaining(this::nullable);
      } else if (node instanceof ArrayNode array) array.elements().forEachRemaining(this::nullable);
    }
  }
}
