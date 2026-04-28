package org.openhab.widget.mcp.mcp;

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

    @Tool(description = "Create or update a canvas layout page in OpenHAB that displays a custom widget. "
            + "The page will be a fixed canvas layout (800x600) containing the specified widget.")
    public String createOrUpdatePage(
            @ToolArg(description = "Unique ID for the page, e.g. my_car_page") String uid,
            @ToolArg(description = "Display label for the page, e.g. Car Charging") String label,
            @ToolArg(description = "UID of the widget to embed in the page, e.g. RD_car_charging_widget") String widgetUid,
            @ToolArg(description = "JSON object with widget properties to pass to the widget, "
                    + "e.g. {\"title\": \"Auto\"}. Use {} for no props.")
            String propsJson) {
        return widgetService.createOrUpdatePage(uid, label, widgetUid, propsJson);
    }

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
}
