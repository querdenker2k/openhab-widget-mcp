package org.openhab.widget.mcp.service;

import static org.openhab.widget.mcp.util.RsUtil.safeInvoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.openhab.widget.mcp.client.OpenHabClient;
import org.openhab.widget.mcp.config.OpenHabConfig;
import org.openhab.widget.mcp.model.DeleteState;
import org.openhab.widget.mcp.model.PageLayout;
import org.openhab.widget.mcp.model.ViewportPreset;
import org.openhab.widget.mcp.util.ImageUtil;

@ApplicationScoped
public class PageService {
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    @Inject
    @RestClient
    OpenHabClient openHabClient;
    @Inject
    BrowserService browserService;
    @Inject
    OpenHabConfig config;

    public DeleteState deletePage(String uid) {
        Log.infof("deletePage: %s", uid);
        Response response = safeInvoke(() -> openHabClient.deletePage(uid));
        int status = response.getStatus();
        if (status == 200 || status == 204) {
            Log.infof("deletePage: '%s' deleted HTTP %d", uid, status);
            return DeleteState.DELETED;
        } else if (status == 404) {
            return DeleteState.NOT_FOUND;
        } else {
            Log.warnf("deletePage: unexpected HTTP %d for '%s'", status, uid);
            throw new IllegalStateException("Error deleting page '%s': HTTP %d".formatted(uid, status));
        }
    }

    public String listPages() {
        Log.info("listPages");
        Response response = safeInvoke(() -> openHabClient.listPages());
        int status = response.getStatus();
        String body = response.readEntity(String.class);
        Log.infof("listPages: HTTP %d, %d chars", status, body.length());
        return body;
    }

    public String getPageAsYaml(String uid) {
        Log.infof("getPageAsYaml: %s", uid);
        try {
            Response response = safeInvoke(() -> openHabClient.getPage(uid));
            int status = response.getStatus();
            if (status == 404) {
                Log.infof("getPageAsYaml: %s not found", uid);
                return "Page not found: " + uid;
            }
            Map<String, Object> pageMap = response.readEntity(Map.class);
            return yamlMapper.writeValueAsString(pageMap);
        } catch (Exception e) {
            Log.error("Error getting page: " + uid, e);
            return "Error getting page: " + e.getMessage();
        }
    }

    @SneakyThrows
    public CreateOrUpdatePage createOrUpdatePage(String uid, String label, String widgetUid, String propsJson,
            String layout, String device) {
        Log.infof("createOrUpdatePage: uid=%s, label=%s, widgetUid=%s, layout=%s, device=%s", uid, label, widgetUid,
                layout, device);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = propsJson == null || propsJson.isBlank()
                ? Map.of()
                : jsonMapper.readValue(propsJson, Map.class);
        OpenHabConfig.Dimension canvasSize = resolvePageCanvasDimension(device);

        Map<String, Object> widgetRef = new LinkedHashMap<>();
        widgetRef.put("component", "widget:" + widgetUid);
        widgetRef.put("config", props);

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("uid", uid);
        page.put("component", "oh-layout-page");
        page.put("tags", List.of());
        page.put("props", Map.of("parameters", List.of(), "parameterGroups", List.of()));

        if (PageLayout.fromString(layout) == PageLayout.GRID) {
            page.put("config", Map.of("label", label, "hideNavbar", true, "sidebar", true));
            page.put("slots", buildGridSlots(label, widgetRef));
        } else {
            page.put("config",
                    Map.of("label", label, "layoutType", "fixed", "fixedType", "canvas", "gridEnable", true,
                            "screenWidth", canvasSize.width(), "screenHeight", canvasSize.height(), "scale", false,
                            "sidebar", true));
            page.put("slots", buildCanvasSlots(widgetRef, canvasSize));
        }

        return upsertPage(uid, page, "createOrUpdatePage");
    }

    /**
     * Resolves the page canvas size for a device preset. "desktop" keeps using the
     * dedicated {@code openhab.page} default (unrelated to the browser viewport
     * used for screenshotting) for backward compatibility; "tablet"/"phone" reuse
     * the same dimensions as {@link ViewportPreset}.
     */
    private OpenHabConfig.Dimension resolvePageCanvasDimension(String device) {
        return switch (ViewportPreset.fromString(device)) {
            case DESKTOP -> config.desktop();
            case TABLET -> config.tablet();
            case PHONE -> config.phone();
        };
    }

    private Map<String, Object> buildCanvasSlots(Map<String, Object> widgetRef, OpenHabConfig.Dimension canvasSize) {
        Map<String, Object> canvasItem = new LinkedHashMap<>();
        canvasItem.put("component", "oh-canvas-item");
        canvasItem.put("config", Map.of("x", 0, "y", 0, "h", canvasSize.height(), "w", canvasSize.width()));
        canvasItem.put("slots", Map.of("default", List.of(widgetRef)));

        Map<String, Object> canvasLayer = new LinkedHashMap<>();
        canvasLayer.put("component", "oh-canvas-layer");
        canvasLayer.put("config", Map.of());
        canvasLayer.put("slots", Map.of("default", List.of(canvasItem)));

        return Map.of("default", List.of(), "masonry", List.of(), "grid", List.of(), "canvas", List.of(canvasLayer));
    }

    private Map<String, Object> buildGridSlots(String label, Map<String, Object> widgetRef) {
        Map<String, Object> gridCol = new LinkedHashMap<>();
        gridCol.put("component", "oh-grid-col");
        gridCol.put("config", Map.of("width", "100"));
        gridCol.put("slots", Map.of("default", List.of(widgetRef)));

        Map<String, Object> gridRow = new LinkedHashMap<>();
        gridRow.put("component", "oh-grid-row");
        gridRow.put("config", Map.of());
        gridRow.put("slots", Map.of("default", List.of(gridCol)));

        Map<String, Object> block = new LinkedHashMap<>();
        block.put("component", "oh-block");
        block.put("config", Map.of("title", label));
        block.put("slots", Map.of("default", List.of(gridRow)));

        return Map.of("default", List.of(block), "masonry", List.of(), "grid", List.of(), "canvas", List.of());
    }

    @SneakyThrows
    private CreateOrUpdatePage upsertPage(String uid, Map<String, Object> page, String logPrefix) {
        String jsonBody = jsonMapper.writeValueAsString(page);

        Response checkResponse = safeInvoke(() -> openHabClient.getPage(uid));
        if (checkResponse.getStatus() == 200) {
            Response updateResponse = safeInvoke(() -> openHabClient.updatePage(uid, jsonBody));
            int updateStatus = updateResponse.getStatus();
            if (updateStatus == 200) {
                Log.infof("%s: updated '%s' HTTP %d", logPrefix, uid, updateStatus);
                return new CreateOrUpdatePage(uid, CreateOrUpdateState.UPDATED);
            } else {
                Log.warnf("%s: update failed for '%s' HTTP %d", logPrefix, uid, updateStatus);
                throw new IllegalStateException("Error updating page '%s': HTTP %d".formatted(uid, updateStatus));
            }
        } else {
            Response createResponse = safeInvoke(() -> openHabClient.createPage(jsonBody));
            int status = createResponse.getStatus();
            if (status == 200 || status == 201) {
                Log.infof("%s: created '%s' HTTP %d", logPrefix, uid, status);
                return new CreateOrUpdatePage(uid, CreateOrUpdateState.CREATED);
            } else {
                Log.warnf("%s: create failed for '%s' HTTP %d", logPrefix, uid, status);
                throw new IllegalStateException("Error creating page '%s': HTTP %d".formatted(uid, status));
            }
        }
    }

    @SneakyThrows
    public CreateOrUpdatePage createComplexPage(String uid, String label, List<WidgetPlacement> placements,
            String device) {
        Log.infof("createComplexPage: uid=%s, label=%s, %d placements, device=%s", uid, label, placements.size(),
                device);
        OpenHabConfig.Dimension canvasSize = resolvePageCanvasDimension(device);

        List<Map<String, Object>> canvasItems = new ArrayList<>();
        for (WidgetPlacement p : placements) {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (p.propsJson() == null || p.propsJson().isBlank())
                    ? Map.of()
                    : jsonMapper.readValue(p.propsJson(), Map.class);

            Map<String, Object> widgetRef = new LinkedHashMap<>();
            widgetRef.put("component", "widget:" + p.widgetUid());
            widgetRef.put("config", props);

            Map<String, Object> canvasItem = new LinkedHashMap<>();
            canvasItem.put("component", "oh-canvas-item");
            canvasItem.put("config", Map.of("x", p.x(), "y", p.y(), "h", p.h(), "w", p.w()));
            canvasItem.put("slots", Map.of("default", List.of(widgetRef)));
            canvasItems.add(canvasItem);
        }

        Map<String, Object> canvasLayer = new LinkedHashMap<>();
        canvasLayer.put("component", "oh-canvas-layer");
        canvasLayer.put("config", Map.of());
        canvasLayer.put("slots", Map.of("default", canvasItems));

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("uid", uid);
        page.put("component", "oh-layout-page");
        page.put("config",
                Map.of("label", label, "layoutType", "fixed", "fixedType", "canvas", "gridEnable", true, "screenWidth",
                        canvasSize.width(), "screenHeight", canvasSize.height(), "scale", false, "sidebar", false));
        page.put("tags", List.of());
        page.put("props", Map.of("parameters", List.of(), "parameterGroups", List.of()));
        page.put("slots",
                Map.of("default", List.of(), "masonry", List.of(), "grid", List.of(), "canvas", List.of(canvasLayer)));

        return upsertPage(uid, page, "createComplexPage");
    }

    @SneakyThrows
    public CreateOrUpdatePage createPageFromYaml(String yaml) {
        Log.info("createPageFromYaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> page = yamlMapper.readValue(yaml, Map.class);
        String uid = (String) page.get("uid");
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("YAML page definition must contain a 'uid' field");
        }

        return upsertPage(uid, page, "createPageFromYaml");
    }

    public byte[] screenshotPage(String uid, String device) throws IOException {
        synchronized (browserService) {
            Log.infof("screenshotPage: uid=%s, device=%s", uid, device);
            OpenHabConfig.Dimension viewport = ViewportPreset.fromString(device).dimension(config);
            Page p = browserService.createPage(viewport.width(), viewport.height());
            try {
                String targetUrl = config.url() + "/page/" + uid;
                String expectedPath = "/page/" + uid;

                browserService.navigateAuthenticated(p, targetUrl, expectedPath);

                Log.info("Waiting for page content");
                String contentSelector = String.join(", ", ".oh-canvas-layout", ".oh-block", ".oh-grid-row",
                        ".masonry-page", ".oh-masonry");
                try {
                    p.waitForSelector(contentSelector,
                            new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
                } catch (TimeoutError e) {
                    Log.warnf("screenshotPage: no known content selector matched for page '%s' – capturing anyway",
                            uid);
                }

                p.waitForTimeout(1200);

                Locator canvasLayout = p.locator(".oh-canvas-layout");
                if (canvasLayout.count() > 0) {
                    byte[] screenshot = canvasLayout.screenshot(new Locator.ScreenshotOptions());
                    if (ImageUtil.isCompletelyWhite(screenshot, 0, true)) {
                        throw new IllegalStateException("Screenshot is completely white");
                    }
                    return screenshot;
                }

                // Responsive pages: clip from x-position of first content element to exclude
                // sidebar
                @SuppressWarnings("unchecked")
                Map<String, Object> clip = (Map<String, Object>) p.evaluate("""
                        () => {
                            const el = document.querySelector('.oh-block, .oh-grid-row, .masonry-page, .oh-masonry');
                            if (!el) return null;
                            const r = el.getBoundingClientRect();
                            return { x: Math.round(r.left), y: 0,
                                     width: Math.round(window.innerWidth - r.left), height: window.innerHeight };
                        }
                        """);
                Page.ScreenshotOptions opts = new Page.ScreenshotOptions();
                if (clip != null && ((Number) clip.get("x")).intValue() > 0) {
                    opts.setClip(((Number) clip.get("x")).doubleValue(), ((Number) clip.get("y")).doubleValue(),
                            ((Number) clip.get("width")).doubleValue(), ((Number) clip.get("height")).doubleValue());
                }
                byte[] screenshot = p.screenshot(opts);

                if (ImageUtil.isCompletelyWhite(screenshot, 0, true)) {
                    throw new IllegalStateException("Screenshot is completely white");
                }
                return screenshot;
            } finally {
                p.close();
            }
        }
    }

    public enum CreateOrUpdateState {
        CREATED, UPDATED
    }

    public record WidgetPlacement(String widgetUid, int x, int y, int w, int h, String propsJson) {
    }

    public record CreateOrUpdatePage(String uid, CreateOrUpdateState state) {
    }
}
