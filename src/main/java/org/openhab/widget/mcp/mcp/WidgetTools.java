package org.openhab.widget.mcp.mcp;

import org.openhab.widget.mcp.service.BrowserService;
import org.openhab.widget.mcp.service.WidgetService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class WidgetTools {

    @Inject
    WidgetService widgetService;

    @Inject
    BrowserService browserService;

    @Tool(description = "List all custom widgets registered in OpenHAB. Returns a JSON array of widget definitions.")
    public String listWidgets() {
        return widgetService.listWidgets();
    }

    @Tool(description = "Get a specific widget definition from OpenHAB by its UID.")
    public String getWidget(
            @ToolArg(description = "The widget UID, e.g. RD_car_charging_widget") String uid) {
        return widgetService.getWidget(uid);
    }

    @Tool(description = "Upload a widget to OpenHAB from a YAML file on the server filesystem. "
            + "Creates the widget if it does not exist, updates it otherwise.")
    public String createOrUpdateWidget(
            @ToolArg(description = "Absolute path to the widget YAML file, e.g. /home/robert/VSCode Projects/main-ui-widgets/RD_MyWidget.yaml")
            String filePath) {
        return widgetService.createOrUpdateWidget(filePath);
    }

    @Tool(description = "Delete a widget from OpenHAB by its UID.")
    public String deleteWidget(
            @ToolArg(description = "The widget UID to delete, e.g. RD_car_charging_widget") String uid) {
        return widgetService.deleteWidget(uid);
    }

    @Tool(description = "Take a screenshot of a widget in the OpenHAB developer UI preview. "
            + "Optionally sets widget props before taking the screenshot. "
            + "Returns the absolute path to the saved PNG file.")
    public String previewWidget(
            @ToolArg(description = "The widget UID to preview, e.g. RD_car_charging_widget") String uid,
            @ToolArg(description = "JSON object with widget props to set before screenshotting, "
                    + "e.g. {\"title\": \"Auto\", \"cablePluggedInItem\": \"MyItem\"}. Use {} for no props.")
            String propsJson) {
        try {
            String path = browserService.screenshotWidget(uid, propsJson);
            return "Screenshot saved to: " + path;
        } catch (Exception e) {
            return "Error taking widget screenshot: " + e.getMessage();
        }
    }
}
