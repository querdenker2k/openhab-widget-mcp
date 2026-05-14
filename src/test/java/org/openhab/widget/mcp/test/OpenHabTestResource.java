package org.openhab.widget.mcp.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

@Slf4j
public class OpenHabTestResource implements QuarkusTestResourceLifecycleManager {
    static final String TEST_USER = "admin";
    static final String TEST_PASSWORD = "admin1234";

    public static volatile String openHabUrl;
    public static volatile String accessToken;

    private static final Network NETWORK = Network.newNetwork();

    private static final String INFLUX_TOKEN = "my-super-secret-admin-token";
    private static final String INFLUX_ORG = "openhab-org";
    private static final String INFLUX_BUCKET = "openhab-bucket";

    private static final GenericContainer<?> INFLUX_CONTAINER = new GenericContainer<>("influxdb:2.7.12")
            .withNetwork(NETWORK).withNetworkAliases("influxdb").withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
            .withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "admin").withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "admin123456")
            .withEnv("DOCKER_INFLUXDB_INIT_ORG", INFLUX_ORG).withEnv("DOCKER_INFLUXDB_INIT_BUCKET", INFLUX_BUCKET)
            .withEnv("DOCKER_INFLUXDB_INIT_ADMIN_TOKEN", INFLUX_TOKEN).withExposedPorts(8086)
            .waitingFor(Wait.forHttp("/health").forStatusCode(200));

    private static final GenericContainer<?> CONTAINER = new GenericContainer<>("openhab/openhab:5.1.4")
            .withNetwork(NETWORK).withExposedPorts(8080).withLogConsumer(new Slf4jLogConsumer(log))
            .withCopyFileToContainer(MountableFile.forClasspathResource("openhab/conf/services/influxdb.cfg"),
                    "/openhab/conf/services/influxdb.cfg")
            .withCopyFileToContainer(MountableFile.forClasspathResource("openhab/conf/services/runtime.cfg"),
                    "/openhab/conf/services/runtime.cfg")
            .withCopyFileToContainer(MountableFile.forClasspathResource("openhab/conf/services/addons.cfg"),
                    "/openhab/conf/services/addons.cfg")
            .withCopyFileToContainer(MountableFile.forClasspathResource("openhab/conf/persistence/influxdb.persist"),
                    "/openhab/conf/persistence/influxdb.persist")
            .waitingFor(Wait.forHttp("/rest/").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(5)));

    @Override
    public Map<String, String> start() {
        INFLUX_CONTAINER.start();
        CONTAINER.start();
        openHabUrl = "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(8080);

        waitForAuthEndpoint();
        accessToken = setupAdminUserAndGetToken();
        installInfluxPersistence();
        return Map.of("quarkus.rest-client.openhab.url", openHabUrl, "openhab.url", openHabUrl, "openhab.api-token",
                accessToken, "openhab.username", TEST_USER, "openhab.password", TEST_PASSWORD);
    }

    @SneakyThrows
    @Override
    public void stop() {
        String logs = CONTAINER.execInContainer("cat", "userdata/logs/openhab.log").getStdout();
        log.info("openhab log: {}", logs);
        CONTAINER.stop();
        INFLUX_CONTAINER.stop();
        NETWORK.close();
    }

    private void installInfluxPersistence() {
        HttpClient httpClient = HttpClient.newBuilder().build();
        try {
            // Install InfluxDB Persistence Addon
            HttpRequest installRequest = HttpRequest
                    .newBuilder(URI.create(openHabUrl + "/rest/addons/persistence-influxdb/install"))
                    .POST(HttpRequest.BodyPublishers.noBody()).header("Authorization", "Bearer " + accessToken).build();
            httpClient.send(installRequest, HttpResponse.BodyHandlers.discarding());

            // Poll until addon is installed
            long deadline = System.currentTimeMillis() + 60_000;
            boolean installed = false;
            while (System.currentTimeMillis() < deadline) {
                HttpRequest statusRequest = HttpRequest
                        .newBuilder(URI.create(openHabUrl + "/rest/addons/persistence-influxdb")).GET()
                        .header("Authorization", "Bearer " + accessToken).build();
                HttpResponse<String> response = httpClient.send(statusRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("\"installed\":true")) {
                    installed = true;
                    break;
                }
                Thread.sleep(2000);
            }
            if (!installed) {
                throw new RuntimeException("InfluxDB persistence addon was not installed in time");
            }

            // Configuration is now handled via mounted files (org.openhab.influxdb.cfg
            // etc.)

            // Wait a bit for the service to start
            Thread.sleep(5000);

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Could not configure InfluxDB persistence", e);
        }
    }

    private void waitForAuthEndpoint() {
        HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        long deadline = System.currentTimeMillis() + 300_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(openHabUrl + "/auth")).GET()
                        .timeout(Duration.ofSeconds(5)).build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() != 404) {
                    return;
                }
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new RuntimeException("OpenHAB auth endpoint did not become available within 5 minutes");
    }

    private String setupAdminUserAndGetToken() {
        AtomicReference<String> token = new AtomicReference<>();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true)
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
