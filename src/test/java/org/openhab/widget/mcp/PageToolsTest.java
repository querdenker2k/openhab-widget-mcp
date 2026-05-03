package org.openhab.widget.mcp;

import io.quarkus.logging.Log;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.widget.mcp.mcp.PageTools;
import org.openhab.widget.mcp.service.PageService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(OpenHabTestResource.class)
class PageToolsTest {

    private static final String PAGE_UID_A = "mcp_tools_test_page_a";
    private static final String PAGE_UID_B = "mcp_tools_test_page_b";
    // Widget reference in page config — widget need not exist in OpenHAB
    private static final String WIDGET_UID_A = "RD_nonexistent_ref_widget_a";
    private static final String WIDGET_UID_B = "RD_nonexistent_ref_widget_b";
    private static final String DEFAULT_TEST_PAGE_UID = WIDGET_UID_A;
    private static final String CUSTOM_TEST_PAGE_UID = "mcp_custom_test_page";

    @Inject
    PageTools pageTools;

    @BeforeEach
    void setUp() {
        deletePage(PAGE_UID_A);
        deletePage(PAGE_UID_B);
        deletePage(DEFAULT_TEST_PAGE_UID);
        deletePage(CUSTOM_TEST_PAGE_UID);
    }

    @AfterEach
    void tearDown() {
        deletePage(PAGE_UID_A);
        deletePage(PAGE_UID_B);
        deletePage(DEFAULT_TEST_PAGE_UID);
        deletePage(CUSTOM_TEST_PAGE_UID);
    }

//    @Test
//    void createOrUpdatePage_newPage_returnsCreatedMessage() {
//        PageService.CreateOrUpdatePage result = pageTools.createTestPageForWidget(PAGE_UID_A, "Test Page", WIDGET_UID_A, "{}");
//        assertThat(result).containsIgnoringCase("created successfully");
//    }
//
//    @Test
//    void createOrUpdatePage_existingPage_returnsUpdatedMessage() {
//        pageTools.createTestPageForWidget(PAGE_UID_A, "Test Page", WIDGET_UID_A, "{}");
//        PageService.CreateOrUpdatePage result = pageTools.createTestPageForWidget(PAGE_UID_A, "Test Page", WIDGET_UID_A, "{}");
//        assertThat(result).containsIgnoringCase("updated successfully");
//    }
//
//    @Test
//    void createOrUpdatePage_withPropsJson_works() {
//        PageService.CreateOrUpdatePage result = pageTools.createTestPageForWidget(PAGE_UID_A, "Test Page", WIDGET_UID_A, "{\"title\":\"Test\"}");
//        assertThat(result).containsIgnoringCase("created successfully");
//    }
//
//    @Test
//    void createOrUpdatePage_withEmptyProps_works() {
//        PageService.CreateOrUpdatePage result = pageTools.createTestPageForWidget(PAGE_UID_A, "Test Page", WIDGET_UID_A, "{}");
//        assertThat(result).doesNotContainIgnoringCase("error");
//    }
//
//    @Test
//    void createOrUpdatePage_withNullProps_works() {
//        PageService.CreateOrUpdatePage result = pageTools.createTestPageForWidget(PAGE_UID_A, "Test Page", WIDGET_UID_A, null);
//        assertThat(result).doesNotContainIgnoringCase("error");
//    }
//
//    @Test
//    void createTestPageForWidget_withDefaults_returnsSuccessAndPageUrl() {
//        PageService.CreateOrUpdatePage result = pageTools.createTestPageForWidget(WIDGET_UID_A, null, null, null);
//        assertThat(result).containsIgnoringCase("created successfully");
//        assertThat(result).contains("/page/" + DEFAULT_TEST_PAGE_UID);
//    }
//
//    @Test
//    void createTestPageForWidget_calledTwice_updatesExistingPage() {
//        pageTools.createTestPageForWidget(WIDGET_UID_A, null, null, null);
//        PageService.CreateOrUpdatePage result = pageTools.createTestPageForWidget(WIDGET_UID_A, null, null, null);
//        assertThat(result).containsIgnoringCase("updated successfully");
//    }
//
//    @Test
//    void createTestPageForWidget_withCustomArgs_appliesThem() {
//        PageService.CreateOrUpdatePage result = pageTools.createTestPageForWidget(
//                WIDGET_UID_A, "Custom Label", CUSTOM_TEST_PAGE_UID, "{\"title\":\"Foo\"}");
//        assertThat(result).containsIgnoringCase("created successfully");
//        assertThat(result).contains("/page/" + CUSTOM_TEST_PAGE_UID);
//    }

    /**
     * End-to-end: verify the page actually lands in OpenHAB with the right widget reference,
     * label and props by reading it back through the OpenHAB REST API.
     */
    @Test
    void createTestPageForWidget_pageIsActuallyPersistedInOpenHab() {
        pageTools.createTestPageForWidget(
                WIDGET_UID_A, "Persistence Check Label", null, "{\"title\":\"PersistedTitleXYZ\"}");

        String body = given()
                .baseUri(OpenHabTestResource.openHabUrl)
                .header("Authorization", "Bearer " + OpenHabTestResource.accessToken)
                .when().get("/rest/ui/components/ui:page/" + DEFAULT_TEST_PAGE_UID)
                .then().statusCode(200).extract().asString();

        assertThat(body)
                .as("Page %s must exist and embed widget %s with the configured label and props",
                        DEFAULT_TEST_PAGE_UID, WIDGET_UID_A)
                .contains("\"uid\":\"" + DEFAULT_TEST_PAGE_UID + "\"")
                .contains("widget:" + WIDGET_UID_A)
                .contains("Persistence Check Label")
                .contains("PersistedTitleXYZ");
    }

    private static Path extractPath(String toolResult) {
        return Path.of(toolResult.replace("Screenshot saved to: ", "").trim());
    }

    private static double nonWhiteRatio(BufferedImage img, int x0, int y0, int w, int h) {
        long count = 0;
        long total = 0;
        for (int y = y0; y < y0 + h && y < img.getHeight(); y++) {
            for (int x = x0; x < x0 + w && x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                if (rgb != 0xFFFFFF) count++;
                total++;
            }
        }
        return total == 0 ? 0 : (double) count / total;
    }

    private void deletePage(String uid) {
        given()
                .baseUri(OpenHabTestResource.openHabUrl)
                .header("Authorization", "Bearer " + OpenHabTestResource.accessToken)
                .when().delete("/rest/ui/components/ui:page/" + uid);
    }
}
