package org.openhab.widget.mcp;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.widget.mcp.mcp.PageTools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(OpenHabTestResource.class)
class PageToolsTest {

    private static final String PAGE_UID = "mcp_tools_test_page";
    private static final String PAGE_UID_B = "mcp_tools_test_page_b";
    // Widget reference in page config — widget need not exist in OpenHAB
    private static final String WIDGET_UID = "RD_nonexistent_ref_widget";

    @Inject
    PageTools pageTools;

    @BeforeEach
    void setUp() {
        deletePage(PAGE_UID);
        deletePage(PAGE_UID_B);
    }

    @AfterEach
    void tearDown() {
        deletePage(PAGE_UID);
        deletePage(PAGE_UID_B);
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
    void screenshotPage_existingPage_capturesActualPageNotLoginScreen() throws Exception {
        pageTools.createOrUpdatePage(PAGE_UID, "Test Page", WIDGET_UID, "{}");

        String result = pageTools.screenshotPage(PAGE_UID);

        assertThat(result).startsWith("Screenshot saved to:");
        Path path = extractPath(result);
        assertThat(Files.exists(path)).isTrue();

        BufferedImage img = ImageIO.read(path.toFile());
        assertThat(img).as("Screenshot must be readable PNG").isNotNull();
        assertThat(img.getWidth()).isGreaterThan(0);
        assertThat(img.getHeight()).isGreaterThan(0);

        // The OpenHAB login/auth page is mostly empty whitespace on the left.
        // Real pages have a sidebar (~270px wide) with logo, page list, admin menu.
        // We require enough non-white pixels in the leftmost 200px to prove the sidebar rendered.
        double leftSidebarFill = nonWhiteRatio(img, 0, 0, Math.min(200, img.getWidth()), img.getHeight());
        assertThat(leftSidebarFill)
                .as("Left sidebar region of '%s' looks empty (%.4f non-white) — screenshot is likely the login page",
                        path, leftSidebarFill)
                .isGreaterThan(0.01);
    }

    @Test
    void screenshotPage_differentPages_produceDifferentScreenshots() throws Exception {
        pageTools.createOrUpdatePage(PAGE_UID, "Page A Title", WIDGET_UID, "{}");
        pageTools.createOrUpdatePage(PAGE_UID_B, "Page B Title", WIDGET_UID, "{}");

        Path pathA = extractPath(pageTools.screenshotPage(PAGE_UID));
        Path pathB = extractPath(pageTools.screenshotPage(PAGE_UID_B));

        BufferedImage imgA = ImageIO.read(pathA.toFile());
        BufferedImage imgB = ImageIO.read(pathB.toFile());
        assertThat(imgA.getWidth()).isEqualTo(imgB.getWidth());
        assertThat(imgA.getHeight()).isEqualTo(imgB.getHeight());

        // The two pages have different labels in the header — pixels must differ somewhere.
        // (If both screenshots ended up on the same login screen, the byte arrays would be near-identical.)
        long differing = 0;
        for (int y = 0; y < imgA.getHeight(); y++) {
            for (int x = 0; x < imgA.getWidth(); x++) {
                if (imgA.getRGB(x, y) != imgB.getRGB(x, y)) differing++;
            }
        }
        long total = (long) imgA.getWidth() * imgA.getHeight();
        double diffRatio = (double) differing / total;
        assertThat(diffRatio)
                .as("Screenshots of '%s' and '%s' should differ — ratio %.4f", PAGE_UID, PAGE_UID_B, diffRatio)
                .isGreaterThan(0.0001);
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
