package org.openhab.widget.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.openhab.widget.mcp.client.OpenHabClient;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@ApplicationScoped
public class WidgetService {

    @Inject
    @RestClient
    OpenHabClient openHabClient;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper = new ObjectMapper();

    private Response safeInvoke(Supplier<Response> call) {
        try {
            return call.get();
        } catch (WebApplicationException e) {
            return e.getResponse();
        }
    }

    public String listWidgets() {
        Log.info("listWidgets");
        try {
            Response response = safeInvoke(openHabClient::listWidgets);
            int status = response.getStatus();
            String body = response.readEntity(String.class);
            Log.infof("listWidgets: HTTP %d, %d chars", status, body.length());
            return body;
        } catch (Exception e) {
            Log.error("Error listing widgets", e);
            return "Error listing widgets: " + e.getMessage();
        }
    }

    public String getWidget(String uid) {
        Log.infof("getWidget: %s", uid);
        try {
            Response response = safeInvoke(() -> openHabClient.getWidget(uid));
            int status = response.getStatus();
            if (status == 404) {
                Log.infof("getWidget: %s not found", uid);
                return "Widget not found: " + uid;
            }
            String body = response.readEntity(String.class);
            Log.infof("getWidget: %s HTTP %d, %d chars", uid, status, body.length());
            return body;
        } catch (Exception e) {
            Log.error("Error getting widget: " + uid, e);
            return "Error getting widget: " + e.getMessage();
        }
    }

    public String createOrUpdateWidget(String filePath) {
        Log.infof("createOrUpdateWidget from file: %s", filePath);
        try {
            String yamlContent = Files.readString(Path.of(filePath));
            return createOrUpdateWidgetFromYaml(yamlContent);
        } catch (NoSuchFileException e) {
            return "Error: File not found: " + filePath;
        } catch (Exception e) {
            Log.error("Error reading widget file: " + filePath, e);
            return "Error reading file: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    public String createOrUpdateWidgetFromYaml(String yamlContent) {
        try {
            Map<String, Object> widgetMap = yamlMapper.readValue(yamlContent, Map.class);
            String uid = (String) widgetMap.get("uid");
            if (uid == null || uid.isBlank()) {
                return "Error: YAML content is missing required 'uid' field";
            }
            Log.infof("createOrUpdateWidgetFromYaml: uid=%s", uid);
            String jsonBody = jsonMapper.writeValueAsString(widgetMap);

            Response checkResponse = safeInvoke(() -> openHabClient.getWidget(uid));
            if (checkResponse.getStatus() == 200) {
                Response updateResponse = safeInvoke(() -> openHabClient.updateWidget(uid, jsonBody));
                int updateStatus = updateResponse.getStatus();
                if (updateStatus == 200) {
                    Log.infof("createOrUpdateWidgetFromYaml: updated '%s' HTTP %d", uid, updateStatus);
                    return "Widget '" + uid + "' updated successfully";
                }
                String err = updateResponse.readEntity(String.class);
                Log.warnf("createOrUpdateWidgetFromYaml: update failed for '%s' HTTP %d: %s", uid, updateStatus, err);
                return "Error updating widget '" + uid + "': HTTP " + updateStatus + " " + err;
            } else {
                Response createResponse = safeInvoke(() -> openHabClient.createWidget(jsonBody));
                int status = createResponse.getStatus();
                if (status == 200 || status == 201) {
                    Log.infof("createOrUpdateWidgetFromYaml: created '%s' HTTP %d", uid, status);
                    return "Widget '" + uid + "' created successfully";
                }
                String err = createResponse.readEntity(String.class);
                Log.warnf("createOrUpdateWidgetFromYaml: create failed for '%s' HTTP %d: %s", uid, status, err);
                return "Error creating widget '" + uid + "': HTTP " + status + " " + err;
            }
        } catch (Exception e) {
            Log.error("Error processing widget YAML", e);
            return "Error processing widget YAML: " + e.getMessage();
        }
    }

    public String deleteWidget(String uid) {
        Log.infof("deleteWidget: %s", uid);
        try {
            Response response = safeInvoke(() -> openHabClient.deleteWidget(uid));
            int status = response.getStatus();
            if (status == 200 || status == 204) {
                Log.infof("deleteWidget: '%s' deleted HTTP %d", uid, status);
                return "Widget '" + uid + "' deleted successfully";
            }
            if (status == 404) {
                Log.infof("deleteWidget: '%s' not found", uid);
                return "Widget not found: " + uid;
            }
            Log.warnf("deleteWidget: unexpected HTTP %d for '%s'", status, uid);
            return "Error deleting widget '" + uid + "': HTTP " + status;
        } catch (Exception e) {
            Log.error("Error deleting widget: " + uid, e);
            return "Error deleting widget: " + e.getMessage();
        }
    }

    public String deletePage(String uid) {
        Log.infof("deletePage: %s", uid);
        try {
            Response response = safeInvoke(() -> openHabClient.deletePage(uid));
            int status = response.getStatus();
            if (status == 200 || status == 204) {
                Log.infof("deletePage: '%s' deleted HTTP %d", uid, status);
                return "Page '" + uid + "' deleted successfully";
            }
            if (status == 404) {
                return "Page not found: " + uid;
            }
            Log.warnf("deletePage: unexpected HTTP %d for '%s'", status, uid);
            return "Error deleting page '" + uid + "': HTTP " + status;
        } catch (Exception e) {
            Log.error("Error deleting page: " + uid, e);
            return "Error deleting page: " + e.getMessage();
        }
    }

    public String createOrUpdatePage(String uid, String label, String widgetUid, String propsJson) {
        Log.infof("createOrUpdatePage: uid=%s, label=%s, widgetUid=%s", uid, label, widgetUid);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = propsJson == null || propsJson.isBlank()
                    ? Map.of()
                    : jsonMapper.readValue(propsJson, Map.class);

            Map<String, Object> widgetRef = new LinkedHashMap<>();
            widgetRef.put("component", "widget:" + widgetUid);
            widgetRef.put("config", props);

            Map<String, Object> canvasItem = new LinkedHashMap<>();
            canvasItem.put("component", "oh-canvas-item");
            canvasItem.put("config", Map.of("x", 0, "y", 0, "h", 600, "w", 800));
            canvasItem.put("slots", Map.of("default", List.of(widgetRef)));

            Map<String, Object> canvasLayer = new LinkedHashMap<>();
            canvasLayer.put("component", "oh-canvas-layer");
            canvasLayer.put("config", Map.of());
            canvasLayer.put("slots", Map.of("default", List.of(canvasItem)));

            Map<String, Object> page = new LinkedHashMap<>();
            page.put("uid", uid);
            page.put("component", "oh-layout-page");
            page.put("config", Map.of(
                    "label", label,
                    "layoutType", "fixed",
                    "fixedType", "canvas",
                    "gridEnable", false,
                    "screenWidth", 800,
                    "screenHeight", 600,
                    "scale", false,
                    "sidebar", true
            ));
            page.put("tags", List.of());
            page.put("props", Map.of("parameters", List.of(), "parameterGroups", List.of()));
            page.put("slots", Map.of(
                    "default", List.of(),
                    "masonry", List.of(),
                    "grid", List.of(),
                    "canvas", List.of(canvasLayer)
            ));

            String jsonBody = jsonMapper.writeValueAsString(page);

            Response checkResponse = safeInvoke(() -> openHabClient.getPage(uid));
            if (checkResponse.getStatus() == 200) {
                Response updateResponse = safeInvoke(() -> openHabClient.updatePage(uid, jsonBody));
                int updateStatus = updateResponse.getStatus();
                if (updateStatus == 200) {
                    Log.infof("createOrUpdatePage: updated '%s' HTTP %d", uid, updateStatus);
                    return "Page '" + uid + "' updated successfully";
                }
                Log.warnf("createOrUpdatePage: update failed for '%s' HTTP %d", uid, updateStatus);
                return "Error updating page '" + uid + "': HTTP " + updateStatus;
            } else {
                Response createResponse = safeInvoke(() -> openHabClient.createPage(jsonBody));
                int status = createResponse.getStatus();
                if (status == 200 || status == 201) {
                    Log.infof("createOrUpdatePage: created '%s' HTTP %d", uid, status);
                    return "Page '" + uid + "' created successfully";
                }
                Log.warnf("createOrUpdatePage: create failed for '%s' HTTP %d", uid, status);
                return "Error creating page '" + uid + "': HTTP " + status;
            }
        } catch (Exception e) {
            Log.error("Error processing page creation", e);
            return "Error processing page: " + e.getMessage();
        }
    }
}
