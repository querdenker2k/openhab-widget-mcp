package org.openhab.widget.mcp.mcp;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkus.test.junit.QuarkusTest;
import java.io.File;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.widget.mcp.service.WidgetService;
import org.openhab.widget.mcp.test.ImageTestUtil;

@QuarkusTest
public class WidgetToolsTest {
  @BeforeEach
  void clean() {
    try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
      deleteWidget(client);
      client.disconnect();
    }
  }

  static void deleteWidget(McpAssured.McpStreamableTestClient client) {
    client
        .when()
        .toolsCall(
            "deleteWidget",
            Map.of("uid", "TestWidget"),
            response -> {
              Assertions.assertThat(response.isError()).isFalse();
            })
        .thenAssertResults();
  }

  @Test
  void testListTools() {
    try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {

      client
          .when()
          .toolsList(
              page -> {
                Assertions.assertThat(page.size()).isEqualTo(13);

                McpAssured.ToolInfo tool = page.findByName("listWidgets");
                Assertions.assertThat(tool.description())
                    .isEqualTo(
                        "List all custom widgets registered in OpenHAB. Returns a JSON array of widget definitions.");
                Assertions.assertThat(tool.inputSchema()).isNotNull();
              })
          .thenAssertResults();

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

  static void createOrUpdateWidget(
      McpAssured.McpStreamableTestClient client, WidgetService.CreateOrUpdateState state) {
    client
        .when()
        .toolsCall(
            "createOrUpdateWidget",
            Map.of("filePath", "src/test/resources/test-widget.yaml"),
            response -> {
              Assertions.assertThat(response.isError()).isFalse();
              TextContent content = response.firstContent().asText();
              Assertions.assertThat(content.text())
                  .isEqualTo(
                      """
                                    {"uid":"TestWidget","state":"%s"}"""
                          .formatted(state.name()));
            })
        .thenAssertResults();
  }

  @Test
  void testListWidgets_Empty() {
    try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
      client
          .when()
          .toolsCall(
              "listWidgets",
              Map.of(),
              response -> {
                Assertions.assertThat(response.isError()).isFalse();
                TextContent content = response.firstContent().asText();
                Assertions.assertThat(content.text()).isEqualTo("[]");
              })
          .thenAssertResults();
      client.disconnect();
    }
  }

  @Test
  void testListWidgets() {
    try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
      createOrUpdateWidget(client, WidgetService.CreateOrUpdateState.CREATED);
      client
          .when()
          .toolsCall(
              "listWidgets",
              Map.of(),
              response -> {
                Assertions.assertThat(response.isError()).isFalse();
                TextContent content = response.firstContent().asText();
                Assertions.assertThat(content.text())
                    .contains(
                        """
                                        "uid":"TestWidget""");
              })
          .thenAssertResults();
      client.disconnect();
    }
  }

  @SneakyThrows
  @Test
  void testPreviewWidget() {
    String res = "openhab-screenshots/widget_TestWidget.png";
    try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
      createOrUpdateWidget(client, WidgetService.CreateOrUpdateState.CREATED);
      client
          .when()
          .toolsCall(
              "previewWidget",
              Map.of("uid", "TestWidget"),
              response -> {
                Assertions.assertThat(response.isError()).isFalse();
                TextContent content = response.firstContent().asText();
                Assertions.assertThat(content.text()).endsWith(res);
              })
          .thenAssertResults();
      client.disconnect();
    }

    ImageTestUtil.compareWithReference(
        new File(res), FileUtils.toFile(IOUtils.resourceToURL("/ref/widget_TestWidget.png")));
  }
}
