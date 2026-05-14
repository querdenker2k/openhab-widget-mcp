package org.openhab.widget.mcp.mcp;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.openhab.widget.mcp.test.OpenHabTestResource;

@QuarkusTest
@QuarkusTestResource(OpenHabTestResource.class)
@Slf4j
public class McpPreviewLoadTest {

    private static final String WIDGET_UID = "RD_mcp_load_test_widget";
    private static final String PAGE_UID = "mcp_load_test_page";

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void loadTestMcpPreviewSequential() throws Exception {
        setupData();

        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
            int count = 10;
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < count; i++) {
                log.info("MCP Sequential requesting widget screenshot " + i);
                client.when().toolsCall("previewWidget", Map.of("uid", WIDGET_UID),
                        response -> assertThat(response.isError()).isFalse()).thenAssertResults();

                log.info("MCP Sequential requesting page screenshot " + i);
                client.when().toolsCall("screenshotPage", Map.of("uid", PAGE_UID),
                        response -> assertThat(response.isError()).isFalse()).thenAssertResults();
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("MCP Sequential load test finished in " + duration + "ms");
            client.disconnect();
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void loadTestMcpPreviewParallel() throws Exception {
        setupData();

        int count = 10;
        long startTime = System.currentTimeMillis();

        try (McpAssured.McpStreamableTestClient client = McpAssured.newConnectedStreamableClient()) {
            for (int index = 0; index < count; index++) {
                log.info("MCP Parallel requesting widget screenshot " + index);
                client.when().toolsCall("previewWidget", Map.of("uid", WIDGET_UID),
                        response -> assertThat(response.isError()).isFalse()).thenAssertResults();

                log.info("MCP Parallel requesting page screenshot " + index);
                client.when().toolsCall("screenshotPage", Map.of("uid", PAGE_UID),
                        response -> assertThat(response.isError()).isFalse()).thenAssertResults();
            }
            client.disconnect();
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("MCP Parallel load test finished in " + duration + "ms");
    }

    private void setupData() throws IOException {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        // Create a widget for testing
        String yaml = Files.readString(Path.of("src/test/resources/test-widget.yaml"));
        yaml = yaml.replace("uid: TestWidget", "uid: " + WIDGET_UID);

        log.info("Creating test widget " + WIDGET_UID);
        given().contentType("text/plain").body(yaml).when().post("/api/widgets/yaml").then().statusCode(200);

        // Create a page for testing
        log.info("Creating test page " + PAGE_UID);
        given().when().post("/api/pages/" + WIDGET_UID + "/testpage?pageUid=" + PAGE_UID).then().statusCode(200);
    }
}
