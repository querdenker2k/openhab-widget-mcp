package org.openhab.widget.mcp.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.widget.mcp.service.WidgetService;
import org.openhab.widget.mcp.test.ImageTestUtil;

@QuarkusTest
public class WidgetToolsTest {

    public static final String WIDGET_UID = "TestWidget";

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    @SneakyThrows
    void clean() {
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
            client.when().toolsCall("listWidgets", Map.of(), response -> {
                Assertions.assertThat(response.isError()).isFalse();
                String json = response.firstContent().asText().text();
                try {
                    List<Map<String, Object>> widgets = mapper.readValue(json, new TypeReference<>() {
                    });
                    for (Map<String, Object> widget : widgets) {
                        String uid = (String) widget.get("uid");
                        deleteWidget(client, uid);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).thenAssertResults();
            client.disconnect();
        }
    }

    static void deleteWidget(McpAssured.McpStreamableTestClient client, String uid) {
        client.when().toolsCall("deleteWidget", Map.of("uid", uid), response -> {
            Assertions.assertThat(response.isError()).isFalse();
        }).thenAssertResults();
    }

    static void deleteWidget(McpAssured.McpStreamableTestClient client) {
        deleteWidget(client, WIDGET_UID);
    }

    @Test
    void testListTools() {
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {

            client.when().toolsList(page -> {
                Assertions.assertThat(page.size()).isEqualTo(21);

                McpAssured.ToolInfo tool = page.findByName("listWidgets");
                Assertions.assertThat(tool.description()).isEqualTo(
                        "List all custom widgets registered in OpenHAB. Returns a JSON array of widget definitions.");
                Assertions.assertThat(tool.inputSchema()).isNotNull();
            }).thenAssertResults();

            client.disconnect();
        }
    }

    @Test
    void testCreateWidget() {
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
            createOrUpdateWidget(client, WidgetService.CreateOrUpdateState.CREATED);
            client.disconnect();
        }
    }

    @Test
    void testCreateWidget_Twice() {
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
            createOrUpdateWidget(client, WidgetService.CreateOrUpdateState.CREATED);
            createOrUpdateWidget(client, WidgetService.CreateOrUpdateState.UPDATED);
            client.disconnect();
        }
    }

    static void createOrUpdateWidget(McpAssured.McpStreamableTestClient client,
            WidgetService.CreateOrUpdateState state) {
        client.when().toolsCall("createOrUpdateWidget", Map.of("filePath", "src/test/resources/test-widget.yaml"),
                response -> {
                    Assertions.assertThat(response.isError()).isFalse();
                    TextContent content = response.firstContent().asText();
                    Assertions.assertThat(content.text()).isEqualTo("""
                            {"uid":"TestWidget","state":"%s"}""".formatted(state.name()));
                }).thenAssertResults();
    }

    @Test
    void testListWidgets_Empty() {
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
            client.when().toolsCall("listWidgets", Map.of(), response -> {
                Assertions.assertThat(response.isError()).isFalse();
                TextContent content = response.firstContent().asText();
                Assertions.assertThat(content.text()).isEqualTo("[]");
            }).thenAssertResults();
            client.disconnect();
        }
    }

    @Test
    void testListWidgets() {
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
            createOrUpdateWidget(client, WidgetService.CreateOrUpdateState.CREATED);
            client.when().toolsCall("listWidgets", Map.of(), response -> {
                Assertions.assertThat(response.isError()).isFalse();
                TextContent content = response.firstContent().asText();
                Assertions.assertThat(content.text()).contains("""
                        "uid":"TestWidget""");
            }).thenAssertResults();
            client.disconnect();
        }
    }

    @SneakyThrows
    @Test
    void testPreviewWidget() {
        String res = "openhab-screenshots/widget_TestWidget.png";
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
            createOrUpdateWidget(client, WidgetService.CreateOrUpdateState.CREATED);
            client.when().toolsCall("previewWidget", Map.of("uid", WIDGET_UID), response -> {
                Assertions.assertThat(response.isError()).isFalse();
                TextContent content = response.firstContent().asText();
                Assertions.assertThat(content.text()).endsWith(res);
            }).thenAssertResults();
            client.disconnect();
        }

        ImageTestUtil.assertMatchesReference(Files.readAllBytes(Path.of(res)), "widget_" + WIDGET_UID + ".png");
    }
}
