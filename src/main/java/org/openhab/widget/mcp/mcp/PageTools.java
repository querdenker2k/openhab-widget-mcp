package org.openhab.widget.mcp.mcp;

import org.openhab.widget.mcp.config.OpenHabConfig;
import org.openhab.widget.mcp.service.BrowserService;
import org.openhab.widget.mcp.service.WidgetService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PageTools {

    @Inject
    WidgetService widgetService;

    @Inject
    BrowserService browserService;

    @Inject
    OpenHabConfig config;

    @Tool(description = "Take a screenshot of an OpenHAB page as it appears in the browser. "
            + "Returns the absolute path to the saved PNG file.")
    public String screenshotPage(
            @ToolArg(description = "The page UID to screenshot, e.g. my_car_page") String uid) {
        try {
            String path = browserService.screenshotPage(uid);
            return "Screenshot saved to: " + path;
        } catch (Exception e) {
            return "Error taking page screenshot: " + e.getMessage();
        }
    }

    @Tool(description = "Create or update a persistent test page in OpenHAB that embeds an existing widget. "
            + "The page shows up in the sidebar so you can preview the widget on a real page. "
            + "Idempotent: calling again with the same widget UID updates the existing test page. "
            + "All arguments except widgetUid are optional (pass null/empty for defaults). "
            + "Both pageUid and label default to the widget UID.")
    public String createTestPageForWidget(
            @ToolArg(description = "UID of the widget to embed, e.g. RD_car_charging_widget") String widgetUid,
            @ToolArg(description = "Optional page label (default: widget UID)") String label,
            @ToolArg(description = "Optional page UID (default: widget UID)") String pageUid,
            @ToolArg(description = "Optional widget props JSON, e.g. {\"title\":\"Auto\"} (default: {})") String propsJson) {
        String resolvedPageUid = (pageUid == null || pageUid.isBlank()) ? widgetUid : pageUid;
        String resolvedLabel = (label == null || label.isBlank()) ? widgetUid : label;
        String resolvedProps = (propsJson == null || propsJson.isBlank()) ? "{}" : propsJson;
        String message = widgetService.createOrUpdatePage(resolvedPageUid, resolvedLabel, widgetUid, resolvedProps);
        String pageUrl = config.url() + "/page/" + resolvedPageUid;
        return message + " (page URL: " + pageUrl + ")";
    }
}
