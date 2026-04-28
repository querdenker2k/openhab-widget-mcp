package org.openhab.widget.mcp;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.restassured.config.HttpClientConfig;

import static io.restassured.RestAssured.config;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for screenshot endpoints using a real OpenHAB instance.
 *
 * On the first run reference screenshots are created automatically in
 * src/test/resources/screenshots/ — commit those files so future runs
 * compare against them. Delete a file to regenerate its reference.
 */
@QuarkusTest
@QuarkusTestResource(OpenHabTestResource.class)
class ScreenshotResourceTest {

    private static final String WIDGET_UID = "RD_screenshot_widget";
    private static final String PAGE_UID = "screenshot_test_page";

    private static final double MAX_PIXEL_DIFF_RATIO = 0.10;
    private static final Path REFERENCE_DIR = Path.of("src/test/resources/screenshots");

    @BeforeEach
    void setUp() {
        given()
                .contentType("text/plain")
                .body("""
                        uid: RD_screenshot_widget
                        component: f7-card
                        config:
                          title: Screenshot Test Widget
                          content: Hello screenshot
                        props:
                          parameters:
                            - name: title
                              label: Title
                              type: TEXT
                        slots:
                          default: []
                        tags:
                          - test
                        """)
                .when().post("/api/widgets/yaml")
                .then().statusCode(200);

        given()
                .contentType("application/json")
                .body("""
                        {"uid":"%s","label":"Screenshot Test Page","widgetUid":"%s","propsJson":"{}"}
                        """.formatted(PAGE_UID, WIDGET_UID))
                .when().post("/api/pages")
                .then().statusCode(200);
    }

    @AfterEach
    void tearDown() {
        given().when().delete("/api/widgets/" + WIDGET_UID);
        given()
                .baseUri(OpenHabTestResource.openHabUrl)
                .header("Authorization", "Bearer " + OpenHabTestResource.accessToken)
                .when().delete("/rest/ui/components/ui:page/" + PAGE_UID);
    }

    @Test
    void screenshotWidget_isValidPngMatchingReference() throws IOException {
        byte[] png = given()
                .config(config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.socket.timeout", 120_000)
                        .setParam("http.connection.timeout", 30_000)))
                .queryParam("props", "{}")
                .when().get("/api/widgets/" + WIDGET_UID + "/screenshot")
                .then()
                .statusCode(200)
                .contentType("image/png")
                .extract().asByteArray();

        assertValidPng(png);
        assertMatchesReference(png, "widget_" + WIDGET_UID + ".png");
    }

    @Test
    void screenshotPage_isValidPngMatchingReference() throws IOException {
        byte[] png = given()
                .config(config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.socket.timeout", 120_000)
                        .setParam("http.connection.timeout", 30_000)))
                .when().get("/api/pages/" + PAGE_UID + "/screenshot")
                .then()
                .statusCode(200)
                .contentType("image/png")
                .extract().asByteArray();

        assertValidPng(png);
        assertMatchesReference(png, "page_" + PAGE_UID + ".png");
    }

    private static void assertValidPng(byte[] bytes) throws IOException {
        assertThat(bytes).isNotEmpty();
        assertThat(bytes[0]).isEqualTo((byte) 0x89);
        assertThat(bytes[1]).isEqualTo((byte) 'P');
        assertThat(bytes[2]).isEqualTo((byte) 'N');
        assertThat(bytes[3]).isEqualTo((byte) 'G');

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(img).isNotNull();
        assertThat(img.getWidth()).isGreaterThan(0);
        assertThat(img.getHeight()).isGreaterThan(0);
    }

    /**
     * Golden-file comparison. Creates the reference on first run; compares on all subsequent runs.
     * Delete the reference file to regenerate it.
     */
    private static void assertMatchesReference(byte[] currentBytes, String fileName) throws IOException {
        Path referenceFile = REFERENCE_DIR.resolve(fileName);

        if (!Files.exists(referenceFile)) {
            Files.createDirectories(REFERENCE_DIR);
            Files.write(referenceFile, currentBytes);
            System.out.println("[Screenshot test] Created reference: " + referenceFile);
            return;
        }

        BufferedImage reference = ImageIO.read(referenceFile.toFile());
        BufferedImage current = ImageIO.read(new ByteArrayInputStream(currentBytes));

        assertThat(current.getWidth())
                .as("Screenshot width should match reference")
                .isEqualTo(reference.getWidth());
        assertThat(current.getHeight())
                .as("Screenshot height should match reference")
                .isEqualTo(reference.getHeight());

        long totalPixels = (long) reference.getWidth() * reference.getHeight();
        long differentPixels = 0;
        for (int y = 0; y < reference.getHeight(); y++) {
            for (int x = 0; x < reference.getWidth(); x++) {
                if (reference.getRGB(x, y) != current.getRGB(x, y)) {
                    differentPixels++;
                }
            }
        }

        double diffRatio = (double) differentPixels / totalPixels;
        assertThat(diffRatio)
                .as("Pixel difference ratio vs reference '%s' (%.1f%% differs, limit %.0f%%)",
                        fileName, diffRatio * 100, MAX_PIXEL_DIFF_RATIO * 100)
                .isLessThanOrEqualTo(MAX_PIXEL_DIFF_RATIO);
    }
}
