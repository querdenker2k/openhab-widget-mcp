package org.openhab.widget.mcp.mcp;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.openhab.widget.mcp.service.ItemService;

@ApplicationScoped
public class ItemTools {

  @Inject ItemService itemService;

  @Tool(
      description =
          "List all OpenHAB items. Optionally filter by name (case-insensitive substring match). "
              + "Returns a JSON array of item objects with name, type, state, and other properties.")
  public String listItems(
      @ToolArg(description = "Optional name filter string. Leave empty to list all items.")
          String nameFilter) {
    return itemService.listItems(nameFilter);
  }

  @Tool(
      description =
          "Get the current state of an OpenHAB item by its name. "
              + "Returns the state as a plain text value (e.g. ON, OFF, 75, 22.5).")
  public String getItemState(
      @ToolArg(description = "The item name as configured in OpenHAB, e.g. MyChargeState")
          String itemName) {
    return itemService.getItemState(itemName);
  }

  @Tool(
      description =
          "Send a command to an OpenHAB item. "
              + "Typical commands: ON, OFF, numeric values (0-100 for dimmers), UP, DOWN, STOP.")
  public String sendItemCommand(
      @ToolArg(description = "The item name to send the command to, e.g. MySwitch") String itemName,
      @ToolArg(description = "The command to send, e.g. ON, OFF, 75, UP, DOWN, STOP")
          String command) {
    return itemService.sendItemCommand(itemName, command);
  }

  @Tool(description = "Create a new OpenHAB item or update an existing one.")
  public String createItem(
      @ToolArg(description = "The unique name of the item, e.g. MyLight") String itemName,
      @ToolArg(
              description =
                  "The type of the item, e.g. Switch, Dimmer, Number, String, Rollershutter, Color")
          String type,
      @ToolArg(required = false, description = "The label for the item") String label,
      @ToolArg(
              required = false,
              description = "The category/icon for the item, e.g. light, temperature")
          String category,
      @ToolArg(required = false, description = "The list of group names the item should belong to")
          List<String> groups) {
    return itemService.createItem(itemName, type, label, category, groups);
  }

  @Tool(description = "Delete an OpenHAB item by its name.")
  public String deleteItem(
      @ToolArg(description = "The name of the item to delete, e.g. MyOldItem") String itemName) {
    return itemService.deleteItem(itemName);
  }

  @Tool(
      description =
          "Set the stateDescription commandOptions on an OpenHAB item. "
              + "This is what drives oh-button action: options dialogs and the displayed label "
              + "for value-coded items (e.g. WLED preset IDs mapped to preset names). "
              + "Pass options as a comma-separated list of value=label pairs, e.g. '1=Sunset,2=Rainbow,3=Ocean'.")
  public String setItemStateDescriptionOptions(
      @ToolArg(description = "The item name, e.g. MyWledPreset") String itemName,
      @ToolArg(description = "Comma-separated value=label pairs, e.g. '1=Sunset,2=Rainbow'")
          String options) {
    return itemService.setItemStateDescriptionOptions(itemName, options);
  }

  @Tool(
      description =
          "Set arbitrary metadata on an OpenHAB item under a given namespace. "
              + "For stateDescription with commandOptions prefer setItemStateDescriptionOptions which is simpler. "
              + "The configJson must be a JSON object string, e.g. '{\"options\":\"1=Sunset,2=Rainbow\"}'.")
  public String setItemMetadata(
      @ToolArg(description = "The item name") String itemName,
      @ToolArg(description = "Metadata namespace, e.g. stateDescription, autoupdate, expire")
          String namespace,
      @ToolArg(
              required = false,
              description = "The metadata value (default: ' '). Many namespaces only use config.")
          String value,
      @ToolArg(
              required = false,
              description =
                  "Metadata config as JSON object string, e.g. '{\"options\":\"1=Foo,2=Bar\"}'")
          String configJson) {
    java.util.Map<String, Object> config = java.util.Map.of();
    if (configJson != null && !configJson.isBlank()) {
      try {
        config =
            new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(configJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
      } catch (Exception e) {
        return "Error parsing configJson: " + e.getMessage();
      }
    }
    return itemService.setItemMetadata(itemName, namespace, value, config);
  }

  @Tool(description = "Remove metadata from an OpenHAB item for a given namespace.")
  public String deleteItemMetadata(
      @ToolArg(description = "The item name") String itemName,
      @ToolArg(description = "Metadata namespace, e.g. stateDescription") String namespace) {
    return itemService.deleteItemMetadata(itemName, namespace);
  }
}
