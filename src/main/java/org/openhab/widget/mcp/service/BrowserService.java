package org.openhab.widget.mcp.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.List;
import org.openhab.widget.mcp.config.OpenHabConfig;

/**
 * Manages the Playwright infrastructure: Chromium instance, BrowserContext, and
 * persistent Pages.
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

    static String pathOf(String url) {
        try {
            String p = URI.create(url).getPath();
            return p == null ? "" : p;
        } catch (Exception e) {
            return "";
        }
    }

    void onStart(@Observes StartupEvent ev) {
        Log.info("Pre-initializing browser on startup");
        try {
            ensureBrowser();
        } catch (Exception e) {
            Log.warnf("Browser startup initialization failed: %s", e.getMessage(), e);
        }
    }

    @PreDestroy
    synchronized void close() {
        Log.info("Shutting down browser");
        if (browser != null) {
            try {
                browser.close();
            } catch (Exception e) {
                Log.warn("Error closing browser", e);
            }
            browser = null;
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception e) {
                Log.warn("Error closing playwright", e);
            }
            playwright = null;
        }
        context = null;
        Log.info("Browser shut down");
    }

    public synchronized void ensureBrowser() {
        if (browser == null || !browser.isConnected()) {
            Log.info("Launching new Chromium browser instance (headless)");
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(config.headless())
                    .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage")));

            context = browser.newContext(
                    new Browser.NewContextOptions().setViewportSize(config.screen().width(), config.screen().height()));

            Log.info("Browser launched (%sx%s)".formatted(config.screen().width(), config.screen().height()));
        }
    }

    public synchronized Page createPage() {
        ensureBrowser();
        return context.newPage();
    }

    public synchronized void navigateAuthenticated(Page p, String targetUrl, String expectedPath) {
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
            throw new IllegalStateException("Navigation to " + targetUrl + " failed — final path " + currentPath
                    + " does not match expected " + expectedPath);
        }
    }
}
