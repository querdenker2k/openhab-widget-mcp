package org.openhab.widget.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class OpenHabTestResource implements QuarkusTestResourceLifecycleManager {

    static final String TEST_USER = "admin";
    static final String TEST_PASSWORD = "admin1234";

    public static volatile String openHabUrl;
    public static volatile String accessToken;

    private static final GenericContainer<?> CONTAINER =
            new GenericContainer<>("openhab/openhab:5.1.4")
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/rest/")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(5)));

    @Override
    public Map<String, String> start() {
        CONTAINER.start();
        openHabUrl = "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(8080);
        waitForAuthEndpoint();
        accessToken = setupAdminUserAndGetToken();
        return Map.of(
                "quarkus.rest-client.openhab.url", openHabUrl,
                "openhab.url", openHabUrl,
                "openhab.api-token", accessToken,
                "openhab.username", TEST_USER,
                "openhab.password", TEST_PASSWORD
        );
    }

    @Override
    public void stop() {
        CONTAINER.stop();
    }

    // The /rest/ health check fires when Jetty is up but the auth bundle may still be initializing.
    // Poll /auth until it responds (non-404) before handing off to Playwright.
    private void waitForAuthEndpoint() {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        long deadline = System.currentTimeMillis() + 300_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(openHabUrl + "/auth"))
                        .GET()
                        .timeout(Duration.ofSeconds(5))
                        .build();
                HttpResponse<Void> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() != 404) {
                    return;
                }
            } catch (Exception ignored) {}
            try { Thread.sleep(2000); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new RuntimeException("OpenHAB auth endpoint did not become available within 5 minutes");
    }

    private String setupAdminUserAndGetToken() {
        AtomicReference<String> token = new AtomicReference<>();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage")));
            BrowserContext context = browser.newContext();
            int i = 0;
            do {
                try {
                    Page page = context.newPage();

                    ObjectMapper mapper = new ObjectMapper();
                    page.onResponse(response -> {
                        if (response.url().contains("/rest/auth/token") && response.status() == 200) {
                            try {
                                @SuppressWarnings("unchecked")
                                var body = mapper.readValue(response.text(), Map.class);
                                token.set((String) body.get("access_token"));
                            } catch (Exception ignored) {
                            }
                        }
                    });

                    page.navigate(openHabUrl + "/");
                    page.waitForSelector("input[placeholder='User Name']",
                            new Page.WaitForSelectorOptions().setTimeout(10000));
                    page.fill("input[placeholder='User Name']", TEST_USER);
                    page.fill("input[placeholder='Password']", TEST_PASSWORD);
                    page.fill("input[placeholder='Confirm New Password']", TEST_PASSWORD);
                    page.click("input[type='Submit']");
                    page.waitForURL("**/setup-wizard/**", new Page.WaitForURLOptions().setTimeout(10000));
                    page.navigate(openHabUrl + "/overview/");
                    if (!page.url().equals(openHabUrl + "/overview/")) {
                        throw new RuntimeException("Could not navigate to overview page");
                    }
                    browser.close();
                    break;
                } catch (RuntimeException e) {
                    i++;
                }
            } while (i < 3);
        }

        if (token.get() == null) {
            throw new RuntimeException("Could not obtain access token from OpenHAB setup wizard");
        }
        return token.get();
    }
}
