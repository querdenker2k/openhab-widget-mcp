package org.openhab.widget.mcp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openhab.widget.mcp.client.OpenHabClient;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@ApplicationScoped
public class ItemService {

    @Inject
    @RestClient
    OpenHabClient openHabClient;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    private Response safeInvoke(Supplier<Response> call) {
        try {
            return call.get();
        } catch (WebApplicationException e) {
            return e.getResponse();
        }
    }

    public String listItems(String nameFilter) {
        Log.infof("listItems: filter=%s", nameFilter);
        try {
            Response response = safeInvoke(openHabClient::listItems);
            int status = response.getStatus();
            String body = response.readEntity(String.class);
            if (status == 401 || status == 403) {
                return "Error listing items: HTTP " + status + " — configure openhab.api-token for authenticated access";
            }
            if (nameFilter == null || nameFilter.isBlank()) {
                Log.infof("listItems: HTTP %d, returning %d chars", status, body.length());
                return body;
            }
            List<Map<String, Object>> items = jsonMapper.readValue(body, new TypeReference<>() {});
            String filter = nameFilter.toLowerCase();
            List<Map<String, Object>> filtered = items.stream()
                    .filter(item -> {
                        String name = (String) item.getOrDefault("name", "");
                        return name.toLowerCase().contains(filter);
                    })
                    .toList();
            Log.infof("listItems: HTTP %d, %d/%d items match filter '%s'", status, filtered.size(), items.size(), nameFilter);
            return jsonMapper.writeValueAsString(filtered);
        } catch (Exception e) {
            Log.error("Error listing items", e);
            return "Error listing items: " + e.getMessage();
        }
    }

    public String getItemState(String itemName) {
        Log.infof("getItemState: %s", itemName);
        try {
            Response response = safeInvoke(() -> openHabClient.getItemState(itemName));
            int status = response.getStatus();
            if (status == 404) {
                Log.infof("getItemState: %s not found", itemName);
                return "Item not found: " + itemName;
            }
            String state = response.readEntity(String.class);
            Log.infof("getItemState: %s = %s (HTTP %d)", itemName, state, status);
            return state;
        } catch (Exception e) {
            Log.error("Error getting item state: " + itemName, e);
            return "Error getting item state: " + e.getMessage();
        }
    }

    public String sendItemCommand(String itemName, String command) {
        Log.infof("sendItemCommand: item=%s, command=%s", itemName, command);
        try {
            Response response = safeInvoke(() -> openHabClient.sendItemCommand(itemName, command));
            int status = response.getStatus();
            if (status == 200 || status == 202) {
                String result = "Command '" + command + "' sent to item '" + itemName + "' successfully";
                Log.infof("sendItemCommand: %s", result);
                return result;
            }
            if (status == 404) {
                Log.infof("sendItemCommand: item %s not found", itemName);
                return "Item not found: " + itemName;
            }
            Log.warnf("sendItemCommand: unexpected HTTP %d for item=%s command=%s", status, itemName, command);
            return "Error sending command to '" + itemName + "': HTTP " + status;
        } catch (Exception e) {
            Log.error("Error sending command to item: " + itemName, e);
            return "Error sending command: " + e.getMessage();
        }
    }
}
