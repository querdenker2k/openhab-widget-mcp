package org.openhab.widget.mcp.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.openhab.widget.mcp.config.OpenHabConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class BrowserService {

    @Inject
    OpenHabConfig config;

    @Inject
    WidgetService widgetService;

    private Playwright playwright;
    private Browser browser;
    private Page page;
    private boolean loggedIn = false;
    private volatile String accessToken = "";

    void onStart(@Observes StartupEvent ev) {
        if (config.username().isPresent() && !config.username().get().isBlank()) {
            Log.info("Pre-initializing browser and logging in on startup");
            try {
                getPage();
            } catch (Exception e) {
                Log.warnf("Browser startup initialization failed: %s", e.getMessage(), e);
            }
        } else {
            Log.info("No username configured — skipping browser startup login");
        }
    }

    @PreDestroy
    synchronized void close() {
        Log.info("Shutting down browser");
        if (browser != null) {
            try { browser.close(); } catch (Exception e) { Log.warn("Error closing browser", e); }
            browser = null;
        }
        if (playwright != null) {
            try { playwright.close(); } catch (Exception e) { Log.warn("Error closing playwright", e); }
            playwright = null;
        }
        page = null;
        loggedIn = false;
        Log.info("Browser shut down");
    }

    synchronized Page getPage() {
        if (browser == null || !browser.isConnected()) {
            Log.info("Launching new Chromium browser instance (headless)");
            playwright = Playwright.create();
            browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage")));
            BrowserContext ctx = browser.newContext(
                    new Browser.NewContextOptions().setViewportSize(1920, 1080));
            page = ctx.newPage();
            loggedIn = false;
            Log.info("Browser launched, new page created (1920x1080)");
        } else {
            Log.debug("Browser already running, reusing existing page");
        }
        if (!loggedIn) {
            login();
        } else {
            Log.debug("Already logged in, skipping login");
        }
        return page;
    }

    private void login() {
        String username = config.username().orElse("");
        if (username.isBlank()) {
            Log.info("No username configured — proceeding without browser login");
            loggedIn = true;
            return;
        }
        Log.infof("Starting browser login for user '%s'", username);
        try {
            String overviewUrl = config.url() + "/overview/";
            Log.infof("Navigating to overview: %s", overviewUrl);
            page.navigate(overviewUrl);
            page.waitForLoadState(LoadState.LOAD);
            page.waitForTimeout(2000);

            String currentUrl = page.url();
            String currentPath = pathOf(currentUrl);
            Log.infof("After overview load, current URL: %s (path: %s)", currentUrl, currentPath);

            // If we land directly on /auth (no overview shown), skip the login-button step.
            // Otherwise (overview with login link), click the login button to reach /auth.
            if (!"/auth".equals(currentPath)) {
                Log.info("Not on /auth — clicking login button (a.button.color-gray) to reach auth");
                try {
                    page.click("a.button.color-gray", new Page.ClickOptions().setTimeout(5000));
                    page.waitForURL(url -> "/auth".equals(pathOf(url)),
                            new Page.WaitForURLOptions().setTimeout(15000));
                } catch (Exception e) {
                    Log.warnf("Could not reach /auth via login button: %s", e.getMessage());
                    return; // loggedIn stays false → retried on next call
                }
            }
            Log.infof("Auth page reached: %s", page.url());

            Log.infof("Filling login form for user '%s'", username);
            page.waitForSelector("input[placeholder='User Name']",
                    new Page.WaitForSelectorOptions().setTimeout(10000));
            page.fill("input[placeholder='User Name']", username);
            page.fill("input[placeholder='Password']", config.password().orElse(""));
            Log.info("Submitting login form");
            page.click("input[type='Submit']");

            Log.info("Waiting for SPA to process auth code and leave /auth");
            page.waitForURL(url -> !"/auth".equals(pathOf(url)),
                    new Page.WaitForURLOptions().setTimeout(30000));
            page.waitForTimeout(2000);
            Log.infof("Login complete, current URL: %s", page.url());

            extractAccessToken();
            if (accessToken.isBlank()) {
                Log.warn("Login appeared to succeed but no access token was extracted");
                return; // loggedIn stays false
            }
            loggedIn = true;
        } catch (Exception e) {
            Log.warnf(e, "Login failed (%s), will retry on next call", e.getMessage());
        }
    }

    private static String pathOf(String url) {
        try {
            String p = URI.create(url).getPath();
            return p == null ? "" : p;
        } catch (Exception e) {
            return "";
        }
    }

    private void extractAccessToken() {
        Log.info("Extracting OAuth2 access token from browser session");
        try {
            Object result = page.evaluate("""
                    async () => {
                        const rt = localStorage.getItem('openhab.ui:refreshToken');
                        if (!rt) return null;
                        const resp = await fetch('/rest/auth/token', {
                            method: 'POST',
                            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                            body: new URLSearchParams({
                                grant_type: 'refresh_token',
                                refresh_token: rt,
                                client_id: window.location.origin,
                                redirect_uri: window.location.origin + '/auth'
                            })
                        });
                        const data = await resp.json();
                        return data.access_token || null;
                    }
                    """);
            if (result instanceof String token && !token.isBlank()) {
                accessToken = token;
                Log.infof("OAuth2 access token extracted successfully (%d chars)", token.length());
            } else {
                Log.warn("No access token in token response — REST calls may fail without auth");
            }
        } catch (Exception e) {
            Log.warnf(e, "Failed to extract access token: %s", e.getMessage());
        }
    }

    public String getAccessToken() {
        return accessToken;
    }

    public synchronized String ensureToken() {
        if (accessToken.isBlank() && config.username().isPresent() && !config.username().get().isBlank()) {
            Log.info("No access token yet — waiting for browser login to complete");
            getPage();
            Log.infof("ensureToken: token available=%b", !accessToken.isBlank());
        }
        return accessToken;
    }

    /**
     * Navigates to targetUrl and verifies the browser actually landed on the expected path
     * (not just a redirect to /auth or /overview where the URL string still contains the
     * fragment via redirect_uri query params). Retries once after re-login if the path
     * mismatches. Throws IllegalStateException if the path is still wrong after retry.
     */
    private void navigateAuthenticated(Page p, String targetUrl, String expectedPath) {
        Log.infof("Navigating to: %s (expected path: %s)", targetUrl, expectedPath);
        p.navigate(targetUrl);
        p.waitForLoadState(LoadState.LOAD);
        // SPA may redirect to /auth on its own after the initial load — give it a moment
        p.waitForTimeout(1000);
        String currentUrl = p.url();
        String currentPath = pathOf(currentUrl);
        Log.infof("Landed on: %s (path: %s)", currentUrl, currentPath);
        if (!expectedPath.equals(currentPath)) {
            Log.warnf("Path '%s' != expected '%s' — session likely expired, re-logging in", currentPath, expectedPath);
            loggedIn = false;
            login();
            if (!loggedIn) {
                throw new IllegalStateException(
                        "Re-login failed while navigating to " + targetUrl + " — landed on " + currentUrl);
            }
            Log.infof("Re-login successful, retrying navigation to: %s", targetUrl);
            p.navigate(targetUrl);
            p.waitForLoadState(LoadState.LOAD);
            p.waitForTimeout(1000);
            currentUrl = p.url();
            currentPath = pathOf(currentUrl);
            Log.infof("Retry landed on: %s (path: %s)", currentUrl, currentPath);
            if (!expectedPath.equals(currentPath)) {
                throw new IllegalStateException(
                        "Navigation to " + targetUrl + " failed — final URL " + currentUrl
                                + " does not match expected path " + expectedPath);
            }
        }
    }

    public synchronized String screenshotWidget(String uid, String propsJson) throws IOException {
        Log.infof("screenshotWidget: uid=%s, props=%s", uid, propsJson);
        String tempPageUid = "_mcp_preview_" + uid;
        String normalizedProps = (propsJson == null || propsJson.isBlank()) ? "{}" : propsJson;
        Log.infof("screenshotWidget: creating temp page '%s'", tempPageUid);
        widgetService.createOrUpdatePage(tempPageUid, "MCP Widget Preview", uid, normalizedProps);
        try {
            return screenshotPage(tempPageUid);
        } finally {
            try {
                widgetService.deletePage(tempPageUid);
                Log.infof("screenshotWidget: temp page '%s' deleted", tempPageUid);
            } catch (Exception e) {
                Log.warnf("screenshotWidget: could not delete temp page '%s': %s", tempPageUid, e.getMessage());
            }
        }
    }

    public synchronized String screenshotPage(String uid) throws IOException {
        Log.infof("screenshotPage: uid=%s", uid);
        Path outputDir = Path.of(config.outputDir());
        Page p = getPage();

        navigateAuthenticated(p, config.url() + "/page/" + uid, "/page/" + uid);

        Log.info("Waiting for page layout root to appear");
        p.waitForSelector(".oh-layout-page, [class*='page'], f7-page",
                new Page.WaitForSelectorOptions().setTimeout(15000));
        Log.infof("Page layout root found, current URL: %s", p.url());
        // Allow item state bindings to load and render after the DOM is ready
        p.waitForTimeout(3000);
        // Guard against late SPA redirect to /auth (token expiry race)
        String finalPath = pathOf(p.url());
        if (!("/page/" + uid).equals(finalPath)) {
            throw new IllegalStateException(
                    "Page redirected away from /page/" + uid + " before screenshot — final path: " + finalPath);
        }

        Files.createDirectories(outputDir);
        Path screenshotPath = outputDir.resolve("page_" + uid + ".png");

        Log.infof("Capturing full-viewport screenshot → %s", screenshotPath);
        p.screenshot(new Page.ScreenshotOptions()
                .setPath(screenshotPath)
                .setFullPage(false));

        Log.infof("screenshotPage done: %s", screenshotPath.toAbsolutePath());
        return screenshotPath.toAbsolutePath().toString();
    }
}
