package org.openhab.widget.mcp.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.widget.mcp.service.PageService;
import org.openhab.widget.mcp.service.WidgetService;
import org.openhab.widget.mcp.test.ImageTestUtil;

@QuarkusTest
public class PageToolsTest {

    public static final String PAGE_UID = "TestWidget";

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    @SneakyThrows
    void clean() {
        Awaitility.setDefaultTimeout(30, TimeUnit.SECONDS);
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
            client.when().toolsCall("listPages", Map.of(), response -> {
                Assertions.assertThat(response.isError()).isFalse();
                String json = response.firstContent().asText().text();
                try {
                    List<Map<String, Object>> pages = mapper.readValue(json, new TypeReference<>() {
                    });
                    for (Map<String, Object> page : pages) {
                        String uid = (String) page.get("uid");
                        deletePage(client, uid);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).thenAssertResults();
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
                Assertions.assertThat(content.text()).contains(PAGE_UID);
            }).thenAssertResults();
            client.disconnect();
        }
    }

    @Test
    void testGetPageAsYaml() {
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
            WidgetToolsTest.createOrUpdateWidget(client, WidgetService.CreateOrUpdateState.CREATED);
            createTestPageForWidget(client);

            client.when().toolsCall("getPageAsYaml", Map.of("uid", PAGE_UID), response -> {
                Assertions.assertThat(response.isError()).isFalse();
                TextContent content = response.firstContent().asText();
                Assertions.assertThat(content.text()).contains("uid: \"TestWidget\"");
                Assertions.assertThat(content.text()).contains("component: \"oh-layout-page\"");
            }).thenAssertResults();
            client.disconnect();
        }
    }

    private static void deletePage(McpAssured.McpStreamableTestClient client, String uid) {
        client.when().toolsCall("deletePage", Map.of("uid", uid), response -> {
            Assertions.assertThat(response.isError()).isFalse();
        }).thenAssertResults();
    }

    private static void deletePage(McpAssured.McpStreamableTestClient client) {
        deletePage(client, PAGE_UID);
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
        client.when().toolsCall("createTestPageForWidget", Map.of("widgetUid", PAGE_UID), response -> {
            Assertions.assertThat(response.isError()).isFalse();
            TextContent content = response.firstContent().asText();
            Assertions.assertThat(content.text()).isEqualTo("""
                    {"uid":"TestWidget","state":"%s"}""".formatted(PageService.CreateOrUpdateState.CREATED));
        }).thenAssertResults();
    }

    @Test
    void testScreenshotPage() {
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {

            WidgetToolsTest.createOrUpdateWidget(client, WidgetService.CreateOrUpdateState.CREATED);
            createTestPageForWidget(client);

            client.when().toolsCall("screenshotPage", Map.of("uid", PAGE_UID), response -> {
                Assertions.assertThat(response.isError()).isFalse();
                ImageContent content = (ImageContent) response.firstContent();
                Assertions.assertThat(content.mimeType()).isEqualTo("image/png");
                Assertions.assertThat(content.data()).isNotEmpty();

                byte[] screenshot = Base64.getDecoder().decode(content.data());
                try {
                    ImageTestUtil.assertMatchesReference(screenshot, "page_" + PAGE_UID + ".png");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).thenAssertResults();
            client.disconnect();
        }
    }

    @Test
    void testScreenshotResponsivePage() {
        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
            String yaml = """
                    uid: "TestResponsivePage"
                    component: "oh-layout-page"
                    config:
                      label: "Test Responsive Page"
                    tags: []
                    props:
                      parameters: []
                      parameterGroups: []
                    slots:
                      default:
                        - component: "oh-block"
                          config:
                            title: "Hello Block"
                          slots:
                            default: []
                    """;

            client.when().toolsCall("createPageFromYaml", Map.of("yaml", yaml), response -> {
                Assertions.assertThat(response.isError()).isFalse();
            }).thenAssertResults();

            client.when().toolsCall("screenshotPage", Map.of("uid", "TestResponsivePage"), response -> {
                Assertions.assertThat(response.isError()).isFalse();
                ImageContent content = (ImageContent) response.firstContent();
                Assertions.assertThat(content.mimeType()).isEqualTo("image/png");
                Assertions.assertThat(content.data()).isNotEmpty();

                byte[] screenshot = Base64.getDecoder().decode(content.data());
                try {
                    ImageTestUtil.assertMatchesReference(screenshot, "page_TestResponsivePage.png");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).thenAssertResults();
            client.disconnect();
        }
    }
}
