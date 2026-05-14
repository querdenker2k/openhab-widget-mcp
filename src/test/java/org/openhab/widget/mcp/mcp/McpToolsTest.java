package org.openhab.widget.mcp.mcp;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openhab.widget.mcp.test.OpenHabTestResource;

/**
 * Tests MCP tools in two ways: 1. Direct CDI injection — verifies business
 * logic against a real OpenHAB instance. 2. SSE transport endpoint — verifies
 * /mcp/sse is reachable and streams events.
 */
@QuarkusTest
@QuarkusTestResource(OpenHabTestResource.class)
class McpToolsTest {

	private static final String WIDGET_UID = "RD_mcp_test_widget";

	@Inject
	WidgetTools widgetTools;

	@Inject
	ItemTools itemTools;

	@AfterEach
	void tearDown() {
		given().when().delete("/api/widgets/" + WIDGET_UID);
	}

	@Test
	void listWidgets_returnsJsonArray() {
		assertThat(widgetTools.listWidgets()).startsWith("[");
	}

	@Test
	void getWidget_unknownUid_returnsNotFoundMessage() {
		assertThat(widgetTools.getWidget("does_not_exist_xyz")).containsIgnoringCase("not found");
	}

	@Test
	void listItems_noFilter_returnsJsonArray() {
		assertThat(itemTools.listItems(null)).startsWith("[");
	}

	@Test
	void getItemState_unknownItem_returnsNotFoundMessage() {
		assertThat(itemTools.getItemState("ItemThatDoesNotExist")).containsIgnoringCase("not found");
	}

	@Test
	void mcpSseEndpoint_respondsWithEventStream() throws Exception {
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + RestAssured.port + "/mcp/sse"))
				.header("Accept", "text/event-stream").build();

		HttpResponse<Stream<String>> response = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).get(5,
				TimeUnit.SECONDS);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("content-type").orElse("")).contains("text/event-stream");

		CompletableFuture<String> firstData = CompletableFuture
				.supplyAsync(() -> response.body().filter(line -> line.startsWith("data:")).findFirst().orElse(""));
		String event = firstData.get(5, TimeUnit.SECONDS);
		response.body().close();

		assertThat(event).startsWith("data:");
	}
}
