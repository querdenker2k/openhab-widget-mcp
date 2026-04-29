package org.openhab.widget.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages a single Chromium instance with two persistent pages that share auth via a common
 * BrowserContext: an "editor" page kept warm under {@code /developer/widgets/} and a "preview"
 * page kept warm under {@code /page/}. Each screenshot operation only re-navigates within its
 * own area, so tab churn between widget development and page previews is avoided.
 *
 * <p>Playwright is not thread-safe; all public methods are {@code synchronized} to serialise
 * access to the shared Browser/pages.
 */
@ApplicationScoped
public class BrowserService {

    @Inject
    OpenHabConfig config;

    @Inject
    WidgetService widgetService;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page editorPage;
    private Page previewPage;
    private boolean loggedIn = false;
    private volatile String accessToken = "";

    void onStart(@Observes StartupEvent ev) {
        if (config.username().isPresent() && !config.username().get().isBlank()) {
            Log.info("Pre-initializing browser and logging in on startup");
            try {
                ensureBrowser();
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
        context = null;
        editorPage = null;
        previewPage = null;
        loggedIn = false;
        Log.info("Browser shut down");
    }

    private void ensureBrowser() {
        if (browser == null || !browser.isConnected()) {
            Log.info("Launching new Chromium browser instance (headless)");
            playwright = Playwright.create();
            browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage")));
            context = browser.newContext(
                    new Browser.NewContextOptions().setViewportSize(1920, 1080));
            editorPage = context.newPage();
            previewPage = context.newPage();
            loggedIn = false;
            Log.info("Browser launched, two pages created (editor + preview, 1920x1080)");
        }
        if (!loggedIn) {
            login();
        }
    }

    private void login() {
        String username = config.username().orElse("");
        if (username.isBlank()) {
            Log.info("No username configured — proceeding without browser login");
            loggedIn = true;
            return;
        }
        Log.infof("Starting browser login for user '%s'", username);
        Page p = editorPage;
        try {
            String overviewUrl = config.url() + "/overview/";
            Log.infof("Navigating to overview: %s", overviewUrl);
            p.navigate(overviewUrl);
            p.waitForLoadState(LoadState.LOAD);
            p.waitForTimeout(2000);

            String currentUrl = p.url();
            String currentPath = pathOf(currentUrl);
            Log.infof("After overview load, current URL: %s (path: %s)", currentUrl, currentPath);

            // If we land directly on /auth (no overview shown), skip the login-button step.
            // Otherwise (overview with login link), click the login button to reach /auth.
            if (!"/auth".equals(currentPath)) {
                Log.info("Not on /auth — clicking login button (a.button.color-gray) to reach auth");
                try {
                    p.click("a.button.color-gray", new Page.ClickOptions().setTimeout(5000));
                    p.waitForURL(url -> "/auth".equals(pathOf(url)),
                            new Page.WaitForURLOptions().setTimeout(15000));
                } catch (Exception e) {
                    Log.warnf("Could not reach /auth via login button: %s", e.getMessage());
                    return; // loggedIn stays false → retried on next call
                }
            }
            Log.infof("Auth page reached: %s", p.url());

            Log.infof("Filling login form for user '%s'", username);
            p.waitForSelector("input[placeholder='User Name']",
                    new Page.WaitForSelectorOptions().setTimeout(10000));
            p.fill("input[placeholder='User Name']", username);
            p.fill("input[placeholder='Password']", config.password().orElse(""));
            Log.info("Submitting login form");
            p.click("input[type='Submit']");

            Log.info("Waiting for SPA to process auth code and leave /auth");
            p.waitForURL(url -> !"/auth".equals(pathOf(url)),
                    new Page.WaitForURLOptions().setTimeout(30000));
            p.waitForTimeout(2000);
            Log.infof("Login complete, current URL: %s", p.url());

            extractAccessToken(p);
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

    private void extractAccessToken(Page p) {
        Log.info("Extracting OAuth2 access token from browser session");
        try {
            Object result = p.evaluate("""
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
            ensureBrowser();
            Log.infof("ensureToken: token available=%b", !accessToken.isBlank());
        }
        return accessToken;
    }

    /**
     * Navigates {@code p} to {@code targetUrl} and verifies it actually landed on {@code expectedPath}.
     * Retries once after re-login if the path mismatches; throws {@link IllegalStateException} if the
     * path is still wrong after retry. Path comparison uses {@link URI#getPath()} so query-string
     * fragments like {@code ?redirect_uri=.../page/...} cannot fool the check.
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

    /**
     * Screenshots the widget as it renders in the OpenHAB Developer Tools widget editor
     * (URL: {@code /developer/widgets/{uid}}). If {@code propsJson} is non-empty, opens the
     * Set-Props dialog and fills each prop based on its parameter context — plain inputs by
     * {@code name}, {@code context: item} via the item-picker (search + radio-click).
     */
    public synchronized String screenshotWidget(String uid, String propsJson) throws IOException {
        Log.infof("screenshotWidget: uid=%s, props=%s", uid, propsJson);
        ensureBrowser();
        Path outputDir = Path.of(config.outputDir());

        String expectedPath = "/developer/widgets/" + uid;
        navigateAuthenticated(editorPage, config.url() + expectedPath, expectedPath);

        Log.info("Waiting for widget editor preview to render");
        // The developer editor splits into a YAML editor on the left and a live preview on
        // the right. The preview pane shares page-level layout chrome (.f7-page) plus its own
        // .widget-preview / .preview-pane container; we wait on whichever appears first.
        editorPage.waitForSelector(".widget-preview, .preview-pane, .f7-page",
                new Page.WaitForSelectorOptions().setTimeout(15000));
        Log.infof("Editor layout ready, current URL: %s", editorPage.url());
        editorPage.waitForTimeout(2000);

        if (propsJson != null && !propsJson.isBlank() && !propsJson.equals("{}")) {
            applyPropsViaDialog(uid, propsJson);
        }

        String finalPath = pathOf(editorPage.url());
        if (!expectedPath.equals(finalPath)) {
            throw new IllegalStateException(
                    "Editor redirected away from " + expectedPath + " before screenshot — final path: " + finalPath);
        }

        Files.createDirectories(outputDir);
        Path screenshotPath = outputDir.resolve("widget_" + uid + ".png");

        Log.infof("Capturing full-viewport screenshot → %s", screenshotPath);
        editorPage.screenshot(new Page.ScreenshotOptions()
                .setPath(screenshotPath)
                .setFullPage(false));

        Log.infof("screenshotWidget done: %s", screenshotPath.toAbsolutePath());
        return screenshotPath.toAbsolutePath().toString();
    }

    /** Parameter metadata read from a widget's {@code props.parameters[]}, in declaration order. */
    private record ParamInfo(String name, String label, String context) {}

    @SuppressWarnings("unchecked")
    private void applyPropsViaDialog(String uid, String propsJson) {
        Map<String, Object> props;
        try {
            props = jsonMapper.readValue(propsJson, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid propsJson: " + propsJson, e);
        }
        if (props.isEmpty()) {
            return;
        }
        List<ParamInfo> params = getWidgetParams(uid);
        Log.infof("Opening Set Props dialog to apply %d props; widget params: %s", props.size(), params);

        // Click the "Set Props" footer button (also bound to Ctrl-P, but the click is more
        // reliable across keyboard focus states).
        editorPage.locator("a:has-text('Set Props')").first()
                .click(new Locator.ClickOptions().setTimeout(5000));

        Locator dialog = editorPage.locator(".popup.modal-in, .dialog.modal-in").first();
        dialog.waitFor(new Locator.WaitForOptions().setTimeout(5000));
        editorPage.waitForTimeout(500);

        // Iterate in widget-definition order so we know each item-picker's positional index in the dialog.
        int itemPickerIndex = 0;
        for (ParamInfo p : params) {
            boolean isItem = "item".equals(p.context);
            if (props.containsKey(p.name)) {
                String value = String.valueOf(props.get(p.name));
                try {
                    if (isItem) {
                        setItemProp(dialog, itemPickerIndex, p, value);
                    } else {
                        setTextProp(dialog, p.name, value);
                    }
                    Log.infof("Set prop %s = %s (context=%s)", p.name, value, isItem ? "item" : "text");
                } catch (Exception e) {
                    Log.errorf("Failed to set prop %s = %s (context=%s): %s",
                            p.name, value, isItem ? "item" : "text", e.getMessage());
                    dumpDialogHtml(dialog);
                    throw new IllegalStateException("Could not set prop '" + p.name + "': " + e.getMessage(), e);
                }
            }
            if (isItem) {
                itemPickerIndex++;
            }
        }

        // Close the dialog so the preview can redraw with the new props
        Locator closeBtn = dialog.locator(".popup-close, a.link.popup-close, a:has-text('Done')").first();
        if (closeBtn.count() > 0) {
            closeBtn.click(new Locator.ClickOptions().setTimeout(3000));
        } else {
            editorPage.keyboard().press("Escape");
        }
        editorPage.waitForTimeout(2000); // give the preview time to redraw
    }

    private void setTextProp(Locator dialog, String name, String value) {
        Locator input = dialog.locator("input[name='" + name + "']").first();
        input.fill(value, new Locator.FillOptions().setTimeout(5000));
        // Some Vue forms commit on blur — press Tab to ensure change is propagated
        input.press("Tab");
        String actual = input.inputValue();
        if (!value.equals(actual)) {
            Log.warnf("setTextProp '%s': expected '%s' but input now holds '%s'", name, value, actual);
        }
    }

    /**
     * Sets a {@code context: item} prop. The Set-Props dialog renders one {@code <li class="item-picker">}
     * per item param as a collapsed trigger; clicking its inner {@code <a.item-link>} opens a nested popup
     * with the search input and a list of {@code <label class="item-radio">} entries. Each label contains
     * text like "Item Label (ItemName)" — we match by the {@code (itemName)} suffix for uniqueness.
     */
    private void setItemProp(Locator dialog, int itemPickerIndex, ParamInfo param, String itemName) {
        Locator picker = dialog.locator("li.item-picker").nth(itemPickerIndex);
        picker.waitFor(new Locator.WaitForOptions().setTimeout(5000));

        // Open the picker popup (Framework7 teleports it to the body root, the Set-Props popup
        // gets the `popup-behind` class). The picker popup is then the topmost {@code .popup.modal-in}
        // that is NOT marked popup-behind and NOT the Set-Props (.widgetprops-popup) popup itself.
        picker.locator("a.item-link").first()
                .click(new Locator.ClickOptions().setTimeout(5000));

        Locator pickerPopup = editorPage.locator(
                ".popup.modal-in:not(.popup-behind):not(.widgetprops-popup)").first();
        pickerPopup.waitFor(new Locator.WaitForOptions().setTimeout(5000));
        editorPage.waitForTimeout(300);

        Locator search = pickerPopup.locator("input[type='search'][placeholder='Search items']").first();
        search.fill(itemName, new Locator.FillOptions().setTimeout(5000));
        editorPage.waitForTimeout(500);

        // Radio labels read like "Item Label (ItemName)" — match the unique (ItemName) suffix.
        Locator radioLabel = pickerPopup.locator("label.item-radio")
                .filter(new Locator.FilterOptions().setHasText("(" + itemName + ")"))
                .first();
        if (radioLabel.count() == 0) {
            radioLabel = pickerPopup.locator("label.item-radio")
                    .filter(new Locator.FilterOptions().setHasText(itemName))
                    .first();
        }
        if (radioLabel.count() == 0) {
            throw new IllegalStateException("Item '" + itemName + "' not in picker results for prop '"
                    + param.name + "' (label='" + param.label + "')");
        }
        radioLabel.click(new Locator.ClickOptions().setForce(true).setTimeout(5000));

        // The picker popup typically closes on selection. If not, press the Close link.
        editorPage.waitForTimeout(500);
        if (pickerPopup.count() > 0) {
            Locator closeNested = pickerPopup.locator("a.link:has-text('Close'), .popup-close").first();
            if (closeNested.count() > 0) {
                try {
                    closeNested.click(new Locator.ClickOptions().setTimeout(2000));
                } catch (Exception ignored) {
                    editorPage.keyboard().press("Escape");
                }
            }
        }
        editorPage.waitForTimeout(500);
    }

    private void dumpDialogHtml(Locator dialog) {
        try {
            Object html = dialog.evaluate("el => el.outerHTML");
            if (html instanceof String s) {
                String trimmed = s.length() > 4000 ? s.substring(0, 4000) + "...[truncated]" : s;
                Log.errorf("Dialog HTML at failure: %s", trimmed);
            }
        } catch (Exception e) {
            Log.warnf("Could not dump dialog HTML: %s", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<ParamInfo> getWidgetParams(String uid) {
        List<ParamInfo> params = new java.util.ArrayList<>();
        try {
            String json = widgetService.getWidget(uid);
            if (json == null || json.startsWith("Widget not found") || json.startsWith("Error")) {
                Log.warnf("Cannot read widget definition for %s: %s", uid, json);
                return params;
            }
            Map<String, Object> widget = jsonMapper.readValue(json, Map.class);
            Map<String, Object> propsMap = (Map<String, Object>) widget.get("props");
            if (propsMap == null) return params;
            List<Map<String, Object>> parameters = (List<Map<String, Object>>) propsMap.get("parameters");
            if (parameters == null) return params;
            for (Map<String, Object> param : parameters) {
                String name = (String) param.get("name");
                String label = (String) param.get("label");
                String context = (String) param.get("context");
                if (name != null) {
                    params.add(new ParamInfo(name, label == null ? name : label, context == null ? "" : context));
                }
            }
        } catch (Exception e) {
            Log.warnf("Could not parse widget definition for %s: %s", uid, e.getMessage());
        }
        return params;
    }

    public synchronized String screenshotPage(String uid) throws IOException {
        Log.infof("screenshotPage: uid=%s", uid);
        ensureBrowser();
        Path outputDir = Path.of(config.outputDir());

        String expectedPath = "/page/" + uid;
        navigateAuthenticated(previewPage, config.url() + expectedPath, expectedPath);

        Log.info("Waiting for page layout root to appear");
        previewPage.waitForSelector(".oh-layout-page, [class*='page'], f7-page",
                new Page.WaitForSelectorOptions().setTimeout(15000));
        Log.infof("Page layout root found, current URL: %s", previewPage.url());
        // Allow item state bindings to load and render after the DOM is ready
        previewPage.waitForTimeout(3000);
        // Guard against late SPA redirect to /auth (token expiry race)
        String finalPath = pathOf(previewPage.url());
        if (!expectedPath.equals(finalPath)) {
            throw new IllegalStateException(
                    "Page redirected away from " + expectedPath + " before screenshot — final path: " + finalPath);
        }

        Files.createDirectories(outputDir);
        Path screenshotPath = outputDir.resolve("page_" + uid + ".png");

        Log.infof("Capturing full-viewport screenshot → %s", screenshotPath);
        previewPage.screenshot(new Page.ScreenshotOptions()
                .setPath(screenshotPath)
                .setFullPage(false));

        Log.infof("screenshotPage done: %s", screenshotPath.toAbsolutePath());
        return screenshotPath.toAbsolutePath().toString();
    }
}
