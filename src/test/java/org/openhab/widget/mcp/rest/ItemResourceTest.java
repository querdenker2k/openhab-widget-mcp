package org.openhab.widget.mcp.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.widget.mcp.test.OpenHabTestResource;

@QuarkusTest
@QuarkusTestResource(OpenHabTestResource.class)
class ItemResourceTest {

	@BeforeEach
	void setUp() {
		String url = OpenHabTestResource.openHabUrl;
		String bearer = "Bearer " + OpenHabTestResource.accessToken;

		given().baseUri(url).header("Authorization", bearer).contentType("application/json").body("""
				{"type":"Switch","name":"TestSwitch","label":"Test Switch","tags":[],"groupNames":[]}
				""").when().put("/rest/items/TestSwitch").then().statusCode(anyOf(is(200), is(201)));

		given().baseUri(url).header("Authorization", bearer).contentType("application/json").body("""
				{"type":"Dimmer","name":"TestDimmer","label":"Test Dimmer","tags":[],"groupNames":[]}
				""").when().put("/rest/items/TestDimmer").then().statusCode(anyOf(is(200), is(201)));

		given().baseUri(url).header("Authorization", bearer).contentType("application/json").body("""
				{"type":"String","name":"OtherItem","label":"Other Item","tags":[],"groupNames":[]}
				""").when().put("/rest/items/OtherItem").then().statusCode(anyOf(is(200), is(201)));
	}

	@AfterEach
	void tearDown() {
		String url = OpenHabTestResource.openHabUrl;
		String bearer = "Bearer " + OpenHabTestResource.accessToken;
		given().baseUri(url).header("Authorization", bearer).when().delete("/rest/items/TestSwitch");
		given().baseUri(url).header("Authorization", bearer).when().delete("/rest/items/TestDimmer");
		given().baseUri(url).header("Authorization", bearer).when().delete("/rest/items/OtherItem");
	}

	@Test
	void listItems_returnsAllItems() {
		given().when().get("/api/items").then().statusCode(200).body(containsString("TestSwitch"))
				.body(containsString("TestDimmer")).body(containsString("OtherItem"));
	}

	@Test
	void listItems_withFilter_returnsOnlyMatchingItems() {
		given().when().get("/api/items?filter=test").then().statusCode(200).body(containsString("TestSwitch"))
				.body(containsString("TestDimmer")).body(not(containsString("OtherItem")));
	}

	@Test
	void getItemState_existingItem_returnsState() {
		given().when().get("/api/items/TestSwitch/state").then().statusCode(200);
	}

	@Test
	void getItemState_unknownItem_returnsNotFoundMessage() {
		given().when().get("/api/items/GhostItem/state").then().statusCode(200).body(containsString("Item not found"));
	}

	@Test
	void sendItemCommand_acceptsCommand() {
		given().contentType("text/plain").body("ON").when().post("/api/items/TestSwitch/command").then().statusCode(200)
				.body("message", containsString("sent to item"));
	}

	@Test
	void sendItemCommand_unknownItem_returnsNotFoundMessage() {
		given().contentType("text/plain").body("ON").when().post("/api/items/GhostItem/command").then().statusCode(200)
				.body("message", containsString("Item not found"));
	}
}
