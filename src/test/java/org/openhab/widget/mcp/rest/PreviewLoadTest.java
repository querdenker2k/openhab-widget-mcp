package org.openhab.widget.mcp.rest;

import static io.restassured.RestAssured.given;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.openhab.widget.mcp.test.OpenHabTestResource;

@QuarkusTest
@QuarkusTestResource(OpenHabTestResource.class)
@Slf4j
public class PreviewLoadTest {

	private static final String WIDGET_UID = "RD_load_test_widget";
	private static final String PAGE_UID = "load_test_page";

	@Test
	@Timeout(value = 10, unit = TimeUnit.MINUTES)
	void loadTestPreviewSequential() throws InterruptedException, IOException {
		setupData();
		int count = 10; // 10 widget + 10 page = 20 total
		int port = RestAssured.port;
		String baseUrl = "http://localhost:" + port;
		log.info("Using base URL: " + baseUrl);

		HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();

		long startTime = System.currentTimeMillis();

		for (int i = 0; i < count; i++) {

			log.info("Requesting widget screenshot " + i);
			HttpRequest reqWidget = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/api/widgets/" + WIDGET_UID + "/screenshot")).GET()
					.timeout(java.time.Duration.ofMinutes(2)).build();
			HttpResponse<Void> respWidget = client.send(reqWidget, HttpResponse.BodyHandlers.discarding());
			if (respWidget.statusCode() != 200) {
				throw new RuntimeException("Widget screenshot " + i + " failed with status " + respWidget.statusCode());
			}

			log.info("Requesting page screenshot " + i);
			HttpRequest reqPage = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/api/pages/" + PAGE_UID + "/screenshot")).GET()
					.timeout(java.time.Duration.ofMinutes(2)).build();
			HttpResponse<Void> respPage = client.send(reqPage, HttpResponse.BodyHandlers.discarding());
			if (respPage.statusCode() != 200) {
				throw new RuntimeException("Page screenshot " + i + " failed with status " + respPage.statusCode());
			}
		}

		long duration = System.currentTimeMillis() - startTime;
		log.info("Sequential load test finished in " + duration + "ms");
	}

	@Test
	@Timeout(value = 15, unit = TimeUnit.MINUTES)
	void loadTestPreviewParallel() throws Exception {
		setupData();
		int count = 10;
		int port = RestAssured.port;
		String baseUrl = "http://localhost:" + port;

		HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(30)).build();

		List<CompletableFuture<HttpResponse<Void>>> futures = new ArrayList<>();

		long startTime = System.currentTimeMillis();

		for (int i = 0; i < count; i++) {
			final int index = i;
			futures.add(CompletableFuture.supplyAsync(() -> {
				try {
					log.info("Parallel requesting widget screenshot " + index);
					HttpRequest req = HttpRequest.newBuilder()
							.uri(URI.create(baseUrl + "/api/widgets/" + WIDGET_UID + "/screenshot")).GET()
							.timeout(java.time.Duration.ofMinutes(5)) // Long timeout for server-side queue
							.build();
					HttpResponse<Void> response = client.send(req, HttpResponse.BodyHandlers.discarding());
					if (response.statusCode() != 200) {
						log.info("Widget screenshot " + index + " failed with status " + response.statusCode());
					}
					return response;
				} catch (Exception e) {
					log.info("Widget screenshot " + index + " failed with exception: " + e.getMessage());
					throw new RuntimeException(e);
				}
			}));

			futures.add(CompletableFuture.supplyAsync(() -> {
				try {
					log.info("Parallel requesting page screenshot " + index);
					HttpRequest req = HttpRequest.newBuilder()
							.uri(URI.create(baseUrl + "/api/pages/" + PAGE_UID + "/screenshot")).GET()
							.timeout(java.time.Duration.ofMinutes(5)).build();
					HttpResponse<Void> response = client.send(req, HttpResponse.BodyHandlers.discarding());
					if (response.statusCode() != 200) {
						log.info("Page screenshot " + index + " failed with status " + response.statusCode());
					}
					return response;
				} catch (Exception e) {
					log.info("Page screenshot " + index + " failed with exception: " + e.getMessage());
					throw new RuntimeException(e);
				}
			}));
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		for (CompletableFuture<HttpResponse<Void>> future : futures) {
			HttpResponse<Void> response = future.join();
			if (response.statusCode() != 200) {
				log.info("Parallel request failed with status " + response.statusCode());
				throw new RuntimeException("Parallel request failed with status " + response.statusCode());
			}
		}

		long duration = System.currentTimeMillis() - startTime;
		log.info("Parallel load test finished in " + duration + "ms");
	}

	private void setupData() throws IOException {
		RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
		// Create a widget for testing
		String yaml = Files.readString(Path.of("src/test/resources/test-widget.yaml"));
		yaml = yaml.replace("uid: TestWidget", "uid: " + WIDGET_UID);

		log.info("Creating test widget " + WIDGET_UID);
		given().contentType("text/plain").body(yaml).when().post("/api/widgets/yaml").then().statusCode(200);

		// Create a page for testing
		log.info("Creating test page " + PAGE_UID);
		given().when().post("/api/pages/" + WIDGET_UID + "/testpage?pageUid=" + PAGE_UID).then().statusCode(200);
	}
}
