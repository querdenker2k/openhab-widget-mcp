package org.openhab.widget.mcp.mcp;

import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.WrapBusinessError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import lombok.SneakyThrows;
import org.openhab.widget.mcp.config.OpenHabConfig;
import org.openhab.widget.mcp.model.DeleteState;
import org.openhab.widget.mcp.service.WidgetService;

@SuppressWarnings("unused")
@ApplicationScoped
public class WidgetTools {

    @Inject
    WidgetService widgetService;

    @Inject
    OpenHabConfig config;

    @WrapBusinessError
    @Tool(description = "List all custom widgets registered in OpenHAB. Returns a JSON array of widget definitions.")
    public String listWidgets() {
        return widgetService.listWidgets();
    }

    @Tool(description = "Get a specific widget definition from OpenHAB by its UID.")
    public String getWidget(@ToolArg(description = "The widget UID, e.g. Car_Charging") String uid) {
        return widgetService.getWidget(uid);
    }

    @WrapBusinessError
    @Tool(description = "Upload a widget to OpenHAB from a YAML file on the server filesystem. "
            + "Creates the widget if it does not exist, updates it otherwise.")
    public WidgetService.CreateOrUpdateWidget createOrUpdateWidget(
            @ToolArg(description = "Absolute path to the widget YAML file, e.g. main-ui-widgets/MyWidget.yaml") String filePath) {
        return widgetService.createOrUpdateWidget(filePath);
    }

    @WrapBusinessError
    @Tool(description = "Upload a widget to OpenHAB from YAML content. "
            + "Creates the widget if it does not exist, updates it otherwise.")
    public WidgetService.CreateOrUpdateWidget createOrUpdateWidgetFromContent(
            @ToolArg(description = "Complete content of the widget YAML.") String yaml) {
        return widgetService.createOrUpdateWidgetFromYaml(yaml);
    }

    @Tool(description = "Delete a widget from OpenHAB by its UID.")
    @WrapBusinessError
    public DeleteState deleteWidget(@ToolArg(description = "The widget UID to delete, e.g. Car_Charging") String uid) {
        return widgetService.deleteWidget(uid);
    }

    @SneakyThrows
    @Tool(description = """
            Take a screenshot of a widget in the OpenHAB developer UI preview. \
            Optionally sets widget props before taking the screenshot. \
            Returns the screenshot as a PNG image.""")
    @WrapBusinessError
    public ToolResponse previewWidget(@ToolArg(description = "The widget UID to preview, e.g. Car_Charging") String uid,
            @ToolArg(required = false, defaultValue = "{}", description = "JSON object with widget props to set before screenshotting, "
                    + "e.g. {\"title\": \"Auto\", \"cablePluggedInItem\": \"MyItem\"}.") String propsJson,
            @ToolArg(required = false, defaultValue = "desktop", description = "Viewport to emulate: "
                    + "\"desktop\", \"tablet\", or \"phone\". Defaults to desktop.") String device) {
        try {
            byte[] screenshot = widgetService.screenshotWidget(uid, propsJson, device);
            String base64 = Base64.getEncoder().encodeToString(screenshot);
            return ToolResponse.success(new ImageContent(base64, "image/png"));
        } catch (Exception e) {
            return ToolResponse.error("Error taking widget preview screenshot: " + e.getMessage());
        }
    }

    @SneakyThrows
    @Tool(description = """
            Take a screenshot of a widget in the OpenHAB developer UI preview and save it to a file. \
            Optionally sets widget props before taking the screenshot. \
            Returns the absolute path to the saved PNG file.""")
    @WrapBusinessError
    public String previewWidgetToFile(@ToolArg(description = "The widget UID to preview, e.g. Car_Charging") String uid,
            @ToolArg(required = false, defaultValue = "{}", description = "JSON object with widget props to set before screenshotting, "
                    + "e.g. {\"title\": \"Auto\", \"cablePluggedInItem\": \"MyItem\"}.") String propsJson,
            @ToolArg(required = false, defaultValue = "desktop", description = "Viewport to emulate: "
                    + "\"desktop\", \"tablet\", or \"phone\". Defaults to desktop.") String device) {
        byte[] screenshot = widgetService.screenshotWidget(uid, propsJson, device);
        Path outputDir = Path.of(config.outputDir());
        Files.createDirectories(outputDir);
        String suffix = (device == null || device.isBlank() || "desktop".equalsIgnoreCase(device)) ? "" : "_" + device;
        Path screenshotPath = outputDir.resolve("widget_" + uid + suffix + ".png");
        Files.write(screenshotPath, screenshot);
        return screenshotPath.toAbsolutePath().toString();
    }
}
