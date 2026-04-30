package org.openhab.widget.mcp;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@QuarkusTestResource(OpenHabTestResource.class)
class PageResourceTest {

    private static final String PAGE_UID = "test_page";
    // Widget UID only used as a reference in page config; widget need not exist in OpenHAB
    private static final String WIDGET_UID = "RD_test_widget";
    private static final String DEFAULT_TEST_PAGE_UID = WIDGET_UID;

    private static final String PAGE_REQUEST = """
            {"uid":"%s","label":"Test Page","widgetUid":"%s","propsJson":"{}"}
            """.formatted(PAGE_UID, WIDGET_UID);

    @BeforeEach
    void setUp() {
        deletePage(PAGE_UID);
        deletePage(DEFAULT_TEST_PAGE_UID);
    }

    @AfterEach
    void tearDown() {
        deletePage(PAGE_UID);
        deletePage(DEFAULT_TEST_PAGE_UID);
        deletePage("custom_test_page");
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

    @Test
    void createTestPage_withDefaults_createsPageAndReturnsUrl() {
        given()
                .when().post("/api/widgets/" + WIDGET_UID + "/testpage")
                .then()
                .statusCode(200)
                .body("message", containsString("created successfully"))
                .body("pageUid", containsString(DEFAULT_TEST_PAGE_UID))
                .body("pageUrl", containsString("/page/" + DEFAULT_TEST_PAGE_UID));
    }

    @Test
    void createTestPage_calledTwice_updatesExistingPage() {
        given()
                .when().post("/api/widgets/" + WIDGET_UID + "/testpage")
                .then().statusCode(200);

        given()
                .when().post("/api/widgets/" + WIDGET_UID + "/testpage")
                .then()
                .statusCode(200)
                .body("message", containsString("updated successfully"))
                .body("pageUid", containsString(DEFAULT_TEST_PAGE_UID));
    }

    @Test
    void createTestPage_withCustomLabelAndPageUidAndProps_appliesThem() {
        given()
                .queryParam("label", "Custom Label")
                .queryParam("pageUid", "custom_test_page")
                .queryParam("propsJson", "{\"title\":\"Foo\"}")
                .when().post("/api/widgets/" + WIDGET_UID + "/testpage")
                .then()
                .statusCode(200)
                .body("pageUid", is("custom_test_page"))
                .body("pageUrl", containsString("/page/custom_test_page"));
    }

    private void deletePage(String uid) {
        given()
                .baseUri(OpenHabTestResource.openHabUrl)
                .header("Authorization", "Bearer " + OpenHabTestResource.accessToken)
                .when().delete("/rest/ui/components/ui:page/" + uid);
    }
}
