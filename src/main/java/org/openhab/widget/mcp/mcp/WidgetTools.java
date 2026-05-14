package org.openhab.widget.mcp.mcp;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.WrapBusinessError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import org.openhab.widget.mcp.model.DeleteState;
import org.openhab.widget.mcp.service.WidgetService;

@ApplicationScoped
public class WidgetTools {

	@Inject
	WidgetService widgetService;

	@WrapBusinessError
	@Tool(description = "List all custom widgets registered in OpenHAB. Returns a JSON array of widget definitions.")
	public String listWidgets() {
		return widgetService.listWidgets();
	}

	@Tool(description = "Get a specific widget definition from OpenHAB by its UID.")
	public String getWidget(@ToolArg(description = "The widget UID, e.g. RD_car_charging_widget") String uid) {
		return widgetService.getWidget(uid);
	}

	@WrapBusinessError
	@Tool(description = "Upload a widget to OpenHAB from a YAML file on the server filesystem. "
			+ "Creates the widget if it does not exist, updates it otherwise.")
	public WidgetService.CreateOrUpdateWidget createOrUpdateWidget(
			@ToolArg(description = "Absolute path to the widget YAML file, e.g. /home/robert/VSCode Projects/main-ui-widgets/RD_MyWidget.yaml") String filePath) {
		return widgetService.createOrUpdateWidget(filePath);
	}

	@Tool(description = "Delete a widget from OpenHAB by its UID.")
	@WrapBusinessError
	public DeleteState deleteWidget(
			@ToolArg(description = "The widget UID to delete, e.g. RD_car_charging_widget") String uid) {
		return widgetService.deleteWidget(uid);
	}

	@SneakyThrows
	@Tool(description = """
			Take a screenshot of a widget in the OpenHAB developer UI preview. \
			Optionally sets widget props before taking the screenshot. \
			Returns the absolute path to the saved PNG file.""")
	@WrapBusinessError
	public String previewWidget(
			@ToolArg(description = "The widget UID to preview, e.g. RD_car_charging_widget") String uid,
			@ToolArg(required = false, defaultValue = "{}", description = "JSON object with widget props to set before screenshotting, "
					+ "e.g. {\"title\": \"Auto\", \"cablePluggedInItem\": \"MyItem\"}.") String propsJson) {
		return widgetService.screenshotWidget(uid, propsJson);
	}
}
