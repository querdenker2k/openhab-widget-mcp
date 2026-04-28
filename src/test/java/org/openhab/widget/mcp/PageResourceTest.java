package org.openhab.widget.mcp;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
@QuarkusTestResource(OpenHabTestResource.class)
class PageResourceTest {

    private static final String PAGE_UID = "test_page";
    // Widget UID only used as a reference in page config; widget need not exist in OpenHAB
    private static final String WIDGET_UID = "RD_test_widget";

    private static final String PAGE_REQUEST = """
            {"uid":"%s","label":"Test Page","widgetUid":"%s","propsJson":"{}"}
            """.formatted(PAGE_UID, WIDGET_UID);

    @BeforeEach
    void setUp() {
        deletePage();
    }

    @AfterEach
    void tearDown() {
        deletePage();
    }

    @Test
    void createPage_createsSuccessfully() {
        given()
                .contentType("application/json")
                .body(PAGE_REQUEST)
                .when().post("/api/pages")
                .then()
                .statusCode(200)
                .body("message", containsString("created successfully"));
    }

    @Test
    void createPage_whenAlreadyExists_updatesSuccessfully() {
        given()
                .contentType("application/json")
                .body(PAGE_REQUEST)
                .when().post("/api/pages")
                .then().statusCode(200);

        given()
                .contentType("application/json")
                .body(PAGE_REQUEST)
                .when().post("/api/pages")
                .then()
                .statusCode(200)
                .body("message", containsString("updated successfully"));
    }

    private void deletePage() {
        given()
                .baseUri(OpenHabTestResource.openHabUrl)
                .header("Authorization", "Bearer " + OpenHabTestResource.accessToken)
                .when().delete("/rest/ui/components/ui:page/" + PAGE_UID);
    }
}
