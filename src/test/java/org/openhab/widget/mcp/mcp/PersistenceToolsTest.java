package org.openhab.widget.mcp.mcp;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openhab.widget.mcp.test.OpenHabTestResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@QuarkusTest
@QuarkusTestResource(OpenHabTestResource.class)
public class PersistenceToolsTest {

  private static final Logger log = LoggerFactory.getLogger(PersistenceToolsTest.class);

  @Test
  void testAddPersistenceData() {
    try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {

      // Create an item first
      client
          .when()
          .toolsCall(
              "createItem",
              Map.of(
                  "itemName", "PersistenceTestItem",
                  "type", "Number",
                  "label", "",
                  "category", "",
                  "groups", List.of()),
              response -> {
                if (response.isError()) {
                  log.error("createItem failed: {}", response.firstContent());
                }
                Assertions.assertThat(response.isError()).isFalse();
              })
          .thenAssertResults();

      // Add persistence data
      client
          .when()
          .toolsCall(
              "addPersistenceData",
              Map.of(
                  "itemName", "PersistenceTestItem",
                  "time", "2023-10-27T10:00:00Z",
                  "state", "42.5"),
              response -> {
                TextContent content = response.firstContent().asText();
                String text = content.text();
                log.info("[DEBUG_LOG] Tool response: {}", text);
                System.out.println("[DEBUG_LOG] Tool response: " + text);

                Assertions.assertThat(response.isError())
                    .withFailMessage("Tool call failed: " + text)
                    .isFalse();
                Assertions.assertThat(text).contains("Persistence data added");
              })
          .thenAssertResults();

      client.disconnect();
    }
  }
}
