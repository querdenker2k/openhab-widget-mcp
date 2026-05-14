package org.openhab.widget.mcp.mcp;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkus.test.junit.QuarkusTest;
import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.widget.mcp.service.PageService;
import org.openhab.widget.mcp.service.WidgetService;
import org.openhab.widget.mcp.test.ImageTestUtil;

@QuarkusTest
public class PageToolsTest {
    @BeforeEach
    void clean() {
        Awaitility.setDefaultTimeout(30, TimeUnit.SECONDS);
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
            deletePage(client);
            WidgetToolsTest.deleteWidget(client);
            client.disconnect();
        }
    }

    @Test
    void testListPages() {
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
            WidgetToolsTest.createOrUpdateWidget(client, WidgetService.CreateOrUpdateState.CREATED);
            createTestPageForWidget(client);

            client.when().toolsCall("listPages", Map.of(), response -> {
                Assertions.assertThat(response.isError()).isFalse();
                TextContent content = response.firstContent().asText();
                Assertions.assertThat(content.text()).contains("TestWidget");
            }).thenAssertResults();
            client.disconnect();
        }
    }

    @Test
    void testGetPageAsYaml() {
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
            WidgetToolsTest.createOrUpdateWidget(client, WidgetService.CreateOrUpdateState.CREATED);
            createTestPageForWidget(client);

            client.when().toolsCall("getPageAsYaml", Map.of("uid", "TestWidget"), response -> {
                Assertions.assertThat(response.isError()).isFalse();
                TextContent content = response.firstContent().asText();
                Assertions.assertThat(content.text()).contains("uid: \"TestWidget\"");
                Assertions.assertThat(content.text()).contains("component: \"oh-layout-page\"");
            }).thenAssertResults();
            client.disconnect();
        }
    }

    private static void deletePage(McpAssured.McpStreamableTestClient client) {
        client.when().toolsCall("deletePage", Map.of("uid", "TestWidget"), response -> {
            Assertions.assertThat(response.isError()).isFalse();
        }).thenAssertResults();
    }

    @Test
    void testCreatePage() {
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {

            WidgetToolsTest.createOrUpdateWidget(client, WidgetService.CreateOrUpdateState.CREATED);
            createTestPageForWidget(client);

            client.disconnect();
        }
    }

    private static void createTestPageForWidget(McpAssured.McpStreamableTestClient client) {
        client.when().toolsCall("createTestPageForWidget", Map.of("widgetUid", "TestWidget"), response -> {
            Assertions.assertThat(response.isError()).isFalse();
            TextContent content = response.firstContent().asText();
            Assertions.assertThat(content.text()).isEqualTo("""
                    {"uid":"TestWidget","state":"%s"}""".formatted(PageService.CreateOrUpdateState.CREATED));
        }).thenAssertResults();
    }

    @SneakyThrows
    @Test
    void testScreenshotPage() {
        String res = "openhab-screenshots/page_TestWidget.png";
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {

            WidgetToolsTest.createOrUpdateWidget(client, WidgetService.CreateOrUpdateState.CREATED);
            createTestPageForWidget(client);

            client.when().toolsCall("screenshotPage", Map.of("uid", "TestWidget"), response -> {
                Assertions.assertThat(response.isError()).isFalse();
                TextContent content = response.firstContent().asText();
                Assertions.assertThat(content.text()).endsWith(res);
            }).thenAssertResults();
            client.disconnect();
        }

        ImageTestUtil.compareWithReference(new File(res),
                FileUtils.toFile(IOUtils.resourceToURL("/ref/page_TestWidget.png")));
    }
}
