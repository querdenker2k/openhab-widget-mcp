package org.openhab.widget.mcp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.openhab.widget.mcp.client.OpenHabClient;

@Slf4j
@ApplicationScoped
public class ItemService {

	private final ObjectMapper jsonMapper = new ObjectMapper();
	@Inject
	@RestClient
	OpenHabClient openHabClient;

	private Response safeInvoke(Supplier<Response> call) {
		try {
			return call.get();
		} catch (WebApplicationException e) {
			return e.getResponse();
		}
	}

	public String listItems(String nameFilter) {
		log.info("listItems: filter={}", nameFilter);
		try {
			Response response = safeInvoke(openHabClient::listItems);
			int status = response.getStatus();
			String body = response.readEntity(String.class);
			if (status == 401 || status == 403) {
				return "Error listing items: HTTP " + status
						+ " — configure openhab.api-token for authenticated access";
			}
			if (nameFilter == null || nameFilter.isBlank()) {
				log.info("listItems: HTTP {}, returning {} chars", status, body.length());
				return body;
			}
			List<Map<String, Object>> items = jsonMapper.readValue(body, new TypeReference<>() {
			});
			String filter = nameFilter.toLowerCase();
			List<Map<String, Object>> filtered = items.stream().filter(item -> {
				String name = (String) item.getOrDefault("name", "");
				return name.toLowerCase().contains(filter);
			}).toList();
			log.info("listItems: HTTP {}, {}/{} items match filter '{}'", status, filtered.size(), items.size(),
					nameFilter);
			return jsonMapper.writeValueAsString(filtered);
		} catch (Exception e) {
			log.error("Error listing items", e);
			return "Error listing items: " + e.getMessage();
		}
	}

	public String getItemState(String itemName) {
		log.info("getItemState: {}", itemName);
		try {
			Response response = safeInvoke(() -> openHabClient.getItemState(itemName));
			int status = response.getStatus();
			if (status == 404) {
				log.info("getItemState: {} not found", itemName);
				return "Item not found: " + itemName;
			}
			String state = response.readEntity(String.class);
			log.info("getItemState: {} = {} (HTTP {})", itemName, state, status);
			return state;
		} catch (Exception e) {
			log.error("Error getting item state: " + itemName, e);
			return "Error getting item state: " + e.getMessage();
		}
	}

	public String sendItemCommand(String itemName, String command) {
		log.info("sendItemCommand: item={}, command={}", itemName, command);
		try {
			Response response = safeInvoke(() -> openHabClient.sendItemCommand(itemName, command));
			int status = response.getStatus();
			if (status == 200 || status == 202) {
				String result = "Command '" + command + "' sent to item '" + itemName + "' successfully";
				log.info("sendItemCommand: {}", result);
				return result;
			}
			if (status == 404) {
				log.info("sendItemCommand: item {} not found", itemName);
				return "Item not found: " + itemName;
			}
			log.warn("sendItemCommand: unexpected HTTP {} for item={} command={}", status, itemName, command);
			return "Error sending command to '" + itemName + "': HTTP " + status;
		} catch (Exception e) {
			log.error("Error sending command to item: " + itemName, e);
			return "Error sending command: " + e.getMessage();
		}
	}

	public String createItem(String name, String type, String label, String category, List<String> groups) {
		log.info("createItem: name={}, type={}", name, type);
		try {
			java.util.Map<String, Object> item = new java.util.HashMap<>();
			item.put("name", name);
			item.put("type", type);
			item.put("label", label != null ? label : "");
			item.put("category", category != null ? category : "");
			item.put("groupNames", groups != null ? groups : List.of());

			String itemJson = jsonMapper.writeValueAsString(item);
			Response response = safeInvoke(() -> openHabClient.createOrUpdateItem(name, itemJson));
			int status = response.getStatus();
			if (status == 200 || status == 201) {
				return "Item '" + name + "' created/updated successfully";
			}
			String error = response.readEntity(String.class);
			log.warn("createItem: failed with HTTP {}: {}", status, error);
			return "Error creating item '" + name + "': HTTP " + status + " " + error;
		} catch (Exception e) {
			log.error("Error creating item: " + name, e);
			return "Error creating item: " + e.getMessage();
		}
	}

	public String deleteItem(String name) {
		log.info("deleteItem: {}", name);
		try {
			Response response = safeInvoke(() -> openHabClient.deleteItem(name));
			int status = response.getStatus();
			if (status == 200 || status == 204) {
				return "Item '" + name + "' deleted successfully";
			}
			if (status == 404) {
				return "Item not found: " + name;
			}
			String error = response.readEntity(String.class);
			log.warn("deleteItem: failed with HTTP {}: {}", status, error);
			return "Error deleting item '" + name + "': HTTP " + status + " " + error;
		} catch (Exception e) {
			log.error("Error deleting item: " + name, e);
			return "Error deleting item: " + e.getMessage();
		}
	}

	public String setItemMetadata(String name, String namespace, String value, Map<String, Object> config) {
		log.info("setItemMetadata: item={}, namespace={}", name, namespace);
		try {
			Map<String, Object> body = new java.util.HashMap<>();
			body.put("value", value != null ? value : " ");
			body.put("config", config != null ? config : Map.of());

			String json = jsonMapper.writeValueAsString(body);
			Response response = safeInvoke(() -> openHabClient.setItemMetadata(name, namespace, json));
			int status = response.getStatus();
			if (status == 200 || status == 201) {
				return "Metadata '" + namespace + "' set on item '" + name + "' successfully";
			}
			if (status == 404) {
				return "Item not found: " + name;
			}
			String error = response.readEntity(String.class);
			log.warn("setItemMetadata: failed with HTTP {}: {}", status, error);
			return "Error setting metadata '" + namespace + "' on '" + name + "': HTTP " + status + " " + error;
		} catch (Exception e) {
			log.error("Error setting item metadata: " + name + "/" + namespace, e);
			return "Error setting item metadata: " + e.getMessage();
		}
	}

	public String setItemStateDescriptionOptions(String name, String options) {
		log.info("setItemStateDescriptionOptions: item={}, options={}", name, options);
		Map<String, Object> config = new java.util.HashMap<>();
		config.put("options", options);
		return setItemMetadata(name, "stateDescription", " ", config);
	}

	public String deleteItemMetadata(String name, String namespace) {
		log.info("deleteItemMetadata: item={}, namespace={}", name, namespace);
		try {
			Response response = safeInvoke(() -> openHabClient.deleteItemMetadata(name, namespace));
			int status = response.getStatus();
			if (status == 200 || status == 204) {
				return "Metadata '" + namespace + "' removed from item '" + name + "'";
			}
			if (status == 404) {
				return "Item or metadata not found: " + name + "/" + namespace;
			}
			String error = response.readEntity(String.class);
			log.warn("deleteItemMetadata: failed with HTTP {}: {}", status, error);
			return "Error removing metadata '" + namespace + "' from '" + name + "': HTTP " + status + " " + error;
		} catch (Exception e) {
			log.error("Error removing item metadata: " + name + "/" + namespace, e);
			return "Error removing item metadata: " + e.getMessage();
		}
	}
}
