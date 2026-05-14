package org.openhab.widget.mcp.rest;

import static io.restassured.RestAssured.config;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.github.romankh3.image.comparison.ImageComparison;
import com.github.romankh3.image.comparison.ImageComparisonUtil;
import com.github.romankh3.image.comparison.model.ImageComparisonResult;
import com.github.romankh3.image.comparison.model.ImageComparisonState;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.config.HttpClientConfig;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.widget.mcp.test.OpenHabTestResource;

/**
 * Integration test for screenshot endpoints using a real OpenHAB instance.
 *
 * <p>Each test renders a screenshot and compares it to a reference PNG in {@code
 * src/test/resources/screenshots/} via the {@code image-comparison} library. On the first run the
 * reference is auto-created; commit the file so future runs compare against it. Delete a reference
 * file to regenerate. On mismatch, the actual screenshot and a diff image (with differences
 * highlighted in red) are written next to the reference for debugging.
 */
@QuarkusTest
@QuarkusTestResource(OpenHabTestResource.class)
class ScreenshotResourceTest {

  private static final String WIDGET_UID = "RD_screenshot_widget";
  private static final String PAGE_UID = "screenshot_test_page";
  static final String TEST_ITEM = "ScreenshotTestItem";
  static final String TEST_ITEM_STATE = "ItemValueXYZ";

  /** Maximum percentage of differing pixels (0-100) tolerated vs. the reference. */
  private static final double MAX_DIFF_PERCENT = 10.0;

  private static final Path REFERENCE_DIR = Path.of("src/test/resources/screenshots");

  @BeforeEach
  void setUp() {
    // Create test item that the widget can reference via its statusItem prop.
    given()
        .baseUri(OpenHabTestResource.openHabUrl)
        .header("Authorization", "Bearer " + OpenHabTestResource.accessToken)
        .contentType("application/json")
        .body(
            """
                        {"name":"%s","type":"String","label":"Screenshot Test Item","groupNames":[]}
                        """
                .formatted(TEST_ITEM))
        .when()
        .put("/rest/items/" + TEST_ITEM)
        .then()
        .statusCode(
            org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.is(200), org.hamcrest.Matchers.is(201)));

    given()
        .baseUri(OpenHabTestResource.openHabUrl)
        .header("Authorization", "Bearer " + OpenHabTestResource.accessToken)
        .contentType("text/plain")
        .body(TEST_ITEM_STATE)
        .when()
        .post("/rest/items/" + TEST_ITEM)
        .then()
        .statusCode(
            org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.is(200), org.hamcrest.Matchers.is(202)));

    given()
        .contentType("text/plain")
        .body(
            """
                        uid: RD_screenshot_widget
                        component: f7-card
                        config:
                          title: '=(props.title || "Screenshot Test Widget")'
                          content: '=((props.statusItem && items[props.statusItem]) ? items[props.statusItem].state : "Hello screenshot")'
                        props:
                          parameters:
                            - name: title
                              label: Title
                              type: TEXT
                            - name: statusItem
                              label: Status Item
                              type: TEXT
                              context: item
                        slots:
                          default: []
                        tags:
                          - test
                        """)
        .when()
        .post("/api/widgets/yaml")
        .then()
        .statusCode(200);

    given()
        .contentType("application/json")
        .body(
            """
                        {"uid":"%s","label":"Screenshot Test Page","widgetUid":"%s","propsJson":"{}"}
                        """
                .formatted(PAGE_UID, WIDGET_UID))
        .when()
        .post("/api/pages")
        .then()
        .statusCode(200);
  }

  @AfterEach
  void tearDown() {
    given().when().delete("/api/widgets/" + WIDGET_UID);
    given()
        .baseUri(OpenHabTestResource.openHabUrl)
        .header("Authorization", "Bearer " + OpenHabTestResource.accessToken)
        .when()
        .delete("/rest/ui/components/ui:page/" + PAGE_UID);
    given()
        .baseUri(OpenHabTestResource.openHabUrl)
        .header("Authorization", "Bearer " + OpenHabTestResource.accessToken)
        .when()
        .delete("/rest/items/" + TEST_ITEM);
  }

  @Test
  void screenshotWidget_default_matchesReference() throws IOException {
    byte[] png = screenshotWidget("{}");
    assertValidPng(png);
    assertMatchesReference(png, "widget_" + WIDGET_UID + ".png");
  }

  @Test
  void screenshotWidget_withCustomTitleProp_matchesReference() throws IOException {
    byte[] png = screenshotWidget("{\"title\":\"PropsTestCustomTitleXYZ\"}");
    assertValidPng(png);
    assertMatchesReference(png, "widget_" + WIDGET_UID + "_with_title.png");
  }

  @Test
  void screenshotWidget_withItemProp_matchesReference() throws IOException {
    byte[] png = screenshotWidget("{\"statusItem\":\"" + TEST_ITEM + "\"}");
    assertValidPng(png);
    assertMatchesReference(png, "widget_" + WIDGET_UID + "_with_item.png");
  }

  @Test
  void screenshotPage_matchesReference() throws IOException {
    byte[] png =
        given()
            .config(
                config()
                    .httpClient(
                        HttpClientConfig.httpClientConfig()
                            .setParam("http.socket.timeout", 120_000)
                            .setParam("http.connection.timeout", 30_000)))
            .when()
            .get("/api/pages/" + PAGE_UID + "/screenshot")
            .then()
            .statusCode(200)
            .contentType("image/png")
            .extract()
            .asByteArray();

    assertValidPng(png);
    assertMatchesReference(png, "page_" + PAGE_UID + ".png");
  }

  private static byte[] screenshotWidget(String propsJson) {
    return given()
        .config(
            config()
                .httpClient(
                    HttpClientConfig.httpClientConfig()
                        .setParam("http.socket.timeout", 120_000)
                        .setParam("http.connection.timeout", 30_000)))
        .queryParam("props", propsJson)
        .when()
        .get("/api/widgets/" + WIDGET_UID + "/screenshot")
        .then()
        .statusCode(200)
        .contentType("image/png")
        .extract()
        .asByteArray();
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
   * Reference comparison via romankh3/image-comparison. Creates the reference on first run; on
   * mismatch writes the actual screenshot and a diff image (red rectangles around changed regions)
   * next to the reference and fails with the diff percent.
   */
  private static void assertMatchesReference(byte[] currentBytes, String fileName)
      throws IOException {
    Path referenceFile = REFERENCE_DIR.resolve(fileName);

    if (!Files.exists(referenceFile)) {
      Files.createDirectories(REFERENCE_DIR);
      Files.write(referenceFile, currentBytes);
      System.out.println("[Screenshot test] Created reference: " + referenceFile);
      return;
    }

    BufferedImage reference = ImageIO.read(referenceFile.toFile());
    BufferedImage current = ImageIO.read(new ByteArrayInputStream(currentBytes));

    ImageComparisonResult result =
        new ImageComparison(reference, current)
            .setAllowingPercentOfDifferentPixels(MAX_DIFF_PERCENT)
            .setDifferenceRectangleColor(Color.RED)
            .compareImages();

    if (result.getImageComparisonState() != ImageComparisonState.MATCH) {
      String stem =
          fileName.endsWith(".png") ? fileName.substring(0, fileName.length() - 4) : fileName;
      Path diffFile = referenceFile.resolveSibling(stem + ".diff.png");
      Path actualFile = referenceFile.resolveSibling(stem + ".actual.png");
      ImageComparisonUtil.saveImage(diffFile.toFile(), result.getResult());
      Files.write(actualFile, currentBytes);
      fail(
          "Screenshot does not match reference '%s' (state=%s, diff=%.2f%%, limit=%.1f%%). "
              + "Actual saved to '%s', diff to '%s'.",
          fileName,
          result.getImageComparisonState(),
          result.getDifferencePercent(),
          MAX_DIFF_PERCENT,
          actualFile,
          diffFile);
    }
  }
}
