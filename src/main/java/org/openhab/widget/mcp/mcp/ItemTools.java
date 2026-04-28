package org.openhab.widget.mcp.mcp;

import org.openhab.widget.mcp.service.ItemService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ItemTools {

    @Inject
    ItemService itemService;

    @Tool(description = "List all OpenHAB items. Optionally filter by name (case-insensitive substring match). "
            + "Returns a JSON array of item objects with name, type, state, and other properties.")
    public String listItems(
            @ToolArg(description = "Optional name filter string. Leave empty to list all items.") String nameFilter) {
        return itemService.listItems(nameFilter);
    }

    @Tool(description = "Get the current state of an OpenHAB item by its name. "
            + "Returns the state as a plain text value (e.g. ON, OFF, 75, 22.5).")
    public String getItemState(
            @ToolArg(description = "The item name as configured in OpenHAB, e.g. MyChargeState") String itemName) {
        return itemService.getItemState(itemName);
    }

    @Tool(description = "Send a command to an OpenHAB item. "
            + "Typical commands: ON, OFF, numeric values (0-100 for dimmers), UP, DOWN, STOP.")
    public String sendItemCommand(
            @ToolArg(description = "The item name to send the command to, e.g. MySwitch") String itemName,
            @ToolArg(description = "The command to send, e.g. ON, OFF, 75, UP, DOWN, STOP") String command) {
        return itemService.sendItemCommand(itemName, command);
    }
}
