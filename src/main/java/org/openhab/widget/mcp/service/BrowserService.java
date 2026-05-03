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

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Manages the Playwright infrastructure: Chromium instance, BrowserContext, and persistent Pages.
 */
@ApplicationScoped
public class BrowserService {

    @Inject
    OpenHabConfig config;

    @Inject
    LoginService loginService;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;

    void onStart(@Observes StartupEvent ev) {
        if (config.username().isPresent() && !config.username().get().isBlank()) {
            Log.info("Pre-initializing browser on startup");
            try {
                ensureBrowser();
            } catch (Exception e) {
                Log.warnf("Browser startup initialization failed: %s", e.getMessage(), e);
            }
        } else {
            Log.info("No username configured — skipping browser startup initialization");
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
        context = null;
        Log.info("Browser shut down");
    }

    public synchronized void ensureBrowser() {
        if (browser == null || !browser.isConnected()) {
            Log.info("Launching new Chromium browser instance (headless)");
            playwright = Playwright.create();
            browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage")));

            context = browser.newContext(
                    new Browser.NewContextOptions().setViewportSize(1280, 800));

//            config.apiToken().ifPresent(token -> context.setExtraHTTPHeaders(Map.of("Authorization", "Bearer " + token)));
            Log.info("Browser launched (1920x1080)");
        }
    }

    public synchronized Page createPage() {
        ensureBrowser();
        return context.newPage();
    }

    public void navigateAuthenticated(Page p, String targetUrl, String expectedPath) {
        Log.infof("Navigating to: %s (expected path: %s)", targetUrl, expectedPath);

        // Try to navigate first
        p.navigate(targetUrl);
        p.waitForLoadState(LoadState.LOAD);
        p.waitForTimeout(2000);

        String currentPath = pathOf(p.url());
        if ("/auth".equals(currentPath)) {
            Log.info("Landed on /auth — session expired or token insufficient. Triggering login...");
            loginService.login(p);
            // After login, navigate to the actual target
            Log.infof("Retrying navigation to: %s", targetUrl);
            p.navigate(targetUrl);
            p.waitForLoadState(LoadState.LOAD);
            p.waitForTimeout(2000);
            currentPath = pathOf(p.url());
        }

        Log.infof("Landed on: %s (path: %s)", p.url(), currentPath);
        if (!expectedPath.equals(currentPath)) {
            throw new IllegalStateException(
                    "Navigation to " + targetUrl + " failed — final path " + currentPath
                            + " does not match expected " + expectedPath);
        }
    }

    static String pathOf(String url) {
        try {
            String p = URI.create(url).getPath();
            return p == null ? "" : p;
        } catch (Exception e) {
            return "";
        }
    }
}
