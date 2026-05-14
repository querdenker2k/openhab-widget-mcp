package org.openhab.widget.mcp.rest;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.openhab.widget.mcp.service.ItemService;

@Path("/api/items")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Items", description = "Query and control OpenHAB items")
public class ItemResource {

    @Inject
    ItemService itemService;

    @GET
    @Operation(summary = "List all items", description = "Returns all OpenHAB items. Optional ?filter= parameter for name-based filtering.")
    public Response listItems(
            @Parameter(description = "Case-insensitive name filter, e.g. charge") @QueryParam("filter") String filter) {
        Log.infof("REST listItems: filter=%s", filter);
        return Response.ok(itemService.listItems(filter)).build();
    }

    @GET
    @Path("/{name}/state")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Get item state", description = "Returns the current state of the item as plain text.")
    public Response getItemState(
            @Parameter(description = "Item name, e.g. MyChargeState") @PathParam("name") String name) {
        Log.infof("REST getItemState: %s", name);
        return Response.ok(itemService.getItemState(name)).build();
    }

    @POST
    @Path("/{name}/command")
    @Consumes(MediaType.TEXT_PLAIN)
    @Operation(summary = "Send command to item", description = "Sends a command to an OpenHAB item. Body is plain text: ON, OFF, 0-100, UP, DOWN, STOP, etc.")
    public Response sendCommand(@Parameter(description = "Item name, e.g. MySwitch") @PathParam("name") String name,
            String command) {
        Log.infof("REST sendCommand: item=%s, command=%s", name, command);
        String result = itemService.sendItemCommand(name, command);
        return Response.ok(Map.of("message", result)).build();
    }
}
