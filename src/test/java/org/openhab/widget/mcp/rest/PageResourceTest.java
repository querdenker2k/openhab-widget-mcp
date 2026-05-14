package org.openhab.widget.mcp.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.widget.mcp.test.OpenHabTestResource;

@QuarkusTest
@QuarkusTestResource(OpenHabTestResource.class)
class PageResourceTest {

    private static final String PAGE_UID = "test_page";
    // Widget UID only used as a reference in page config; widget need not exist in
    // OpenHAB
    private static final String WIDGET_UID = "RD_test_widget";

    private static final String PAGE_REQUEST = """
            {"uid":"%s","label":"Test Page","widgetUid":"%s","propsJson":"{}"}
            """.formatted(PAGE_UID, WIDGET_UID);

    @BeforeEach
    void setUp() {
        deletePage(PAGE_UID);
        deletePage(WIDGET_UID);
    }

    @AfterEach
    void tearDown() {
        deletePage(PAGE_UID);
        deletePage(WIDGET_UID);
    }

    @Test
    void createPage_createsSuccessfully() {
        given().contentType("application/json").body(PAGE_REQUEST).when().post("/api/pages").then().statusCode(200)
                .body("message.state", is("CREATED"));
    }

    @Test
    void createPage_whenAlreadyExists_updatesSuccessfully() {
        given().contentType("application/json").body(PAGE_REQUEST).when().post("/api/pages").then().statusCode(200);

        given().contentType("application/json").body(PAGE_REQUEST).when().post("/api/pages").then().statusCode(200)
                .body("message.state", is("UPDATED"));
    }

    @Test
    void createTestPage_withDefaults_createsPageAndReturnsUrl() {
        given().when().post("/api/pages/" + WIDGET_UID + "/testpage").then().statusCode(200).body("message.state",
                is("CREATED"));
    }

    @Test
    void createTestPage_calledTwice_updatesExistingPage() {
        given().when().post("/api/pages/" + WIDGET_UID + "/testpage").then().statusCode(200);

        given().when().post("/api/pages/" + WIDGET_UID + "/testpage").then().statusCode(200).body("message.state",
                is("UPDATED"));
    }

    private void deletePage(String uid) {
        given().baseUri(OpenHabTestResource.openHabUrl)
                .header("Authorization", "Bearer " + OpenHabTestResource.accessToken).when()
                .delete("/rest/ui/components/ui:page/" + uid);
    }
}
