package org.openhab.widget.mcp.mcp;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openhab.widget.mcp.test.OpenHabTestResource;

import java.util.List;
import java.util.Map;

@QuarkusTest
@QuarkusTestResource(OpenHabTestResource.class)
public class ItemToolsTest {

    private static final Logger log = LoggerFactory.getLogger(ItemToolsTest.class);

    @Test
    void testCreateItem() {
        try (McpAssured.McpStreamableTestClient client =
                     McpAssured.newConnectedStreamableClient()) {

            client.when()
                    .toolsCall("createItem",
                            Map.of("itemName", "TestItem",
                                   "type", "Switch",
                                   "label", "Test Label",
                                   "category", "light",
                                   "groups", List.of()),
                            response -> {
                                if (response.isError()) {
                                    log.error("createItem failed: {}", response.firstContent());
                                }
                                Assertions.assertThat(response.isError()).isFalse();
                                TextContent content = response.firstContent().asText();
                                Assertions.assertThat(content.text()).contains("Item 'TestItem' created/updated successfully");
                            })
                    .thenAssertResults();

            client.disconnect();
        }
    }

    @Test
    void testListItems() {
        try (McpAssured.McpStreamableTestClient client =
                     McpAssured.newConnectedStreamableClient()) {

            // First ensure the item exists
            client.when()
                    .toolsCall("createItem",
                            Map.of("itemName", "ListTestItem",
                                   "type", "String",
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

            client.when()
                    .toolsCall("listItems",
                            Map.of("nameFilter", "ListTestItem"),
                            response -> {
                                Assertions.assertThat(response.isError()).isFalse();
                                TextContent content = response.firstContent().asText();
                                Assertions.assertThat(content.text()).contains("ListTestItem");
                            })
                    .thenAssertResults();

            client.disconnect();
        }
    }
}
