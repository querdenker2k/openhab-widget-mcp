package org.openhab.widget.mcp.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.widget.mcp.test.OpenHabTestResource;

@QuarkusTest
@QuarkusTestResource(OpenHabTestResource.class)
class WidgetResourceTest {

  private static final String WIDGET_UID = "RD_test_widget";

  @BeforeEach
  void setUp() {
    deleteWidget(WIDGET_UID);
  }

  @AfterEach
  void tearDown() {
    deleteWidget(WIDGET_UID);
  }

  private void deleteWidget(String uid) {
    given()
        .baseUri(OpenHabTestResource.openHabUrl)
        .header("Authorization", "Bearer " + OpenHabTestResource.accessToken)
        .when()
        .delete("/rest/ui/components/ui:widget/" + uid);
  }

  @Test
  void listWidgets_returnsJsonArray() {
    given().when().get("/api/widgets").then().statusCode(200);
  }

  @Test
  void createWidget_fromYaml_createsSuccessfully() throws IOException {
    given()
        .contentType("text/plain")
        .body(loadTestWidgetYaml())
        .when()
        .post("/api/widgets/yaml")
        .then()
        .statusCode(200);
  }

  @Test
  void getWidget_existingWidget_returnsDefinition() throws IOException {
    createWidget();
    given()
        .when()
        .get("/api/widgets/" + WIDGET_UID)
        .then()
        .statusCode(200)
        .body(containsString(WIDGET_UID));
  }

  @Test
  void createWidget_whenAlreadyExists_updatesSuccessfully() throws IOException {
    createWidget();
    given()
        .contentType("text/plain")
        .body(loadTestWidgetYaml())
        .when()
        .post("/api/widgets/yaml")
        .then()
        .statusCode(200)
        .body("message.state", is("UPDATED"));
  }

  @Test
  void uploadWidget_viaMultipart_succeeds() {
    given()
        .multiPart("file", new File("src/test/resources/test-widget.yaml"), "text/plain")
        .when()
        .post("/api/widgets/upload")
        .then()
        .statusCode(200);
  }

  @Test
  void deleteWidget_existingWidget_deletesSuccessfully() throws IOException {
    createWidget();
    given().when().delete("/api/widgets/" + WIDGET_UID).then().statusCode(200);
  }

  @Test
  void getWidget_nonExistentWidget_returnsNotFound() {
    given()
        .when()
        .get("/api/widgets/" + WIDGET_UID)
        .then()
        .statusCode(200)
        .body(containsString("Widget not found"));
  }

  private void createWidget() throws IOException {
    given()
        .contentType("text/plain")
        .body(loadTestWidgetYaml())
        .when()
        .post("/api/widgets/yaml")
        .then()
        .statusCode(200);
  }

  private String loadTestWidgetYaml() throws IOException {
    return Files.readString(Path.of("src/test/resources/test-widget.yaml"));
  }
}
