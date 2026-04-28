package org.openhab.widget.mcp;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.widget.mcp.mcp.PageTools;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(OpenHabTestResource.class)
class PageToolsTest {

    private static final String PAGE_UID = "mcp_tools_test_page";
    // Widget reference in page config — widget need not exist in OpenHAB
    private static final String WIDGET_UID = "RD_nonexistent_ref_widget";

    @Inject
    PageTools pageTools;

    @BeforeEach
    void setUp() {
        deletePage();
    }

    @AfterEach
    void tearDown() {
        deletePage();
    }

    @Test
    void createOrUpdatePage_newPage_returnsCreatedMessage() {
        String result = pageTools.createOrUpdatePage(PAGE_UID, "Test Page", WIDGET_UID, "{}");
        assertThat(result).containsIgnoringCase("created successfully");
    }

    @Test
    void createOrUpdatePage_existingPage_returnsUpdatedMessage() {
        pageTools.createOrUpdatePage(PAGE_UID, "Test Page", WIDGET_UID, "{}");
        String result = pageTools.createOrUpdatePage(PAGE_UID, "Test Page", WIDGET_UID, "{}");
        assertThat(result).containsIgnoringCase("updated successfully");
    }

    @Test
    void createOrUpdatePage_withPropsJson_works() {
        String result = pageTools.createOrUpdatePage(PAGE_UID, "Test Page", WIDGET_UID, "{\"title\":\"Test\"}");
        assertThat(result).containsIgnoringCase("created successfully");
    }

    @Test
    void createOrUpdatePage_withEmptyProps_works() {
        String result = pageTools.createOrUpdatePage(PAGE_UID, "Test Page", WIDGET_UID, "{}");
        assertThat(result).doesNotContainIgnoringCase("error");
    }

    @Test
    void createOrUpdatePage_withNullProps_works() {
        String result = pageTools.createOrUpdatePage(PAGE_UID, "Test Page", WIDGET_UID, null);
        assertThat(result).doesNotContainIgnoringCase("error");
    }

    @Test
    void screenshotPage_existingPage_returnsFilePath() throws Exception {
        pageTools.createOrUpdatePage(PAGE_UID, "Test Page", WIDGET_UID, "{}");

        String result = pageTools.screenshotPage(PAGE_UID);

        assertThat(result).startsWith("Screenshot saved to:");
        String path = result.replace("Screenshot saved to: ", "").trim();
        assertThat(Files.exists(Path.of(path))).isTrue();
    }

    private void deletePage() {
        given()
                .baseUri(OpenHabTestResource.openHabUrl)
                .header("Authorization", "Bearer " + OpenHabTestResource.accessToken)
                .when().delete("/rest/ui/components/ui:page/" + PAGE_UID);
    }
}
