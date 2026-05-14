package org.openhab.widget.mcp.service;

import static org.openhab.widget.mcp.util.RsUtil.safeInvoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.openhab.widget.mcp.client.OpenHabClient;
import org.openhab.widget.mcp.config.OpenHabConfig;
import org.openhab.widget.mcp.model.DeleteState;
import org.openhab.widget.mcp.util.ImageUtil;

@ApplicationScoped
public class WidgetService {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper = new ObjectMapper();
    @Inject
    @RestClient
    OpenHabClient openHabClient;
    @Inject
    BrowserService browserService;
    @Inject
    OpenHabConfig config;

    private static String pathOf(String url) {
        try {
            String p = URI.create(url).getPath();
            return p == null ? "" : p;
        } catch (Exception e) {
            return "";
        }
    }

    public String listWidgets() {
        Log.info("listWidgets");
        Response response = safeInvoke(() -> openHabClient.listWidgets());
        int status = response.getStatus();
        String body = response.readEntity(String.class);
        Log.infof("listWidgets: HTTP %d, %d chars", status, body.length());
        return body;
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

    @SneakyThrows
    public CreateOrUpdateWidget createOrUpdateWidget(String filePath) {
        Log.infof("createOrUpdateWidget from file: %s", filePath);
        String yamlContent = Files.readString(Path.of(filePath));
        return createOrUpdateWidgetFromYaml(yamlContent);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public CreateOrUpdateWidget createOrUpdateWidgetFromYaml(String yamlContent) {
        Map<String, Object> widgetMap = yamlMapper.readValue(yamlContent, Map.class);
        String uid = (String) widgetMap.get("uid");
        if (uid == null || uid.isBlank()) {
            throw new IllegalStateException("Error: YAML content is missing required 'uid' field");
        }
        Log.infof("createOrUpdateWidgetFromYaml: uid=%s", uid);
        String jsonBody = jsonMapper.writeValueAsString(widgetMap);

        Response checkResponse = safeInvoke(() -> openHabClient.getWidget(uid));
        if (checkResponse.getStatus() == 200) {
            Response updateResponse = safeInvoke(() -> openHabClient.updateWidget(uid, jsonBody));
            int updateStatus = updateResponse.getStatus();
            if (updateStatus == 200) {
                Log.infof("createOrUpdateWidgetFromYaml: updated '%s' HTTP %d", uid, updateStatus);
                return new CreateOrUpdateWidget(uid, CreateOrUpdateState.UPDATED);
            } else {
                String err = updateResponse.readEntity(String.class);
                Log.warnf("createOrUpdateWidgetFromYaml: update failed for '%s' HTTP %d: %s", uid, updateStatus, err);
                throw new IllegalStateException(
                        "Error updating widget '%s': HTTP %d %s".formatted(uid, updateStatus, err));
            }
        } else {
            Response createResponse = safeInvoke(() -> openHabClient.createWidget(jsonBody));
            int status = createResponse.getStatus();
            if (status == 200 || status == 201) {
                Log.infof("createOrUpdateWidgetFromYaml: created '%s' HTTP %d", uid, status);
                return new CreateOrUpdateWidget(uid, CreateOrUpdateState.CREATED);
            } else {
                String err = createResponse.readEntity(String.class);
                Log.warnf("createOrUpdateWidgetFromYaml: create failed for '%s' HTTP %d: %s", uid, status, err);
                throw new IllegalStateException("Error creating widget '%s': HTTP %d %s".formatted(uid, status, err));
            }
        }
    }

    public DeleteState deleteWidget(String uid) {
        Log.infof("deleteWidget: %s", uid);
        Response response = safeInvoke(() -> openHabClient.deleteWidget(uid));
        int status = response.getStatus();
        if (status == 200 || status == 204) {
            Log.infof("deleteWidget: '%s' deleted HTTP %d", uid, status);
            return DeleteState.DELETED;
        } else if (status == 404) {
            Log.infof("deleteWidget: '%s' not found", uid);
            return DeleteState.NOT_FOUND;
        } else {
            Log.warnf("deleteWidget: unexpected HTTP %d for '%s'", status, uid);
            throw new IllegalStateException("Error deleting widget '%s': HTTP %d".formatted(uid, status));
        }
    }

    public String screenshotWidget(String uid, String propsJson) throws IOException {
        synchronized (browserService) {
            Log.infof("screenshotWidget: uid=%s, props=%s", uid, propsJson);
            Page editorPage = browserService.createPage();
            try {
                String expectedPath = "/developer/widgets/" + uid;
                browserService.navigateAuthenticated(editorPage, config.url() + expectedPath, expectedPath);

                Log.info("Waiting for widget editor preview to render");
                editorPage.waitForSelector(".widget-preview, .preview-pane, .f7-page",
                        new Page.WaitForSelectorOptions().setTimeout(15000));
                editorPage.waitForTimeout(2000);

                if (propsJson != null && !propsJson.isBlank() && !propsJson.equals("{}")) {
                    applyPropsViaDialog(editorPage, uid, propsJson);
                }

                String finalPath = pathOf(editorPage.url());
                if (!expectedPath.equals(finalPath)) {
                    Log.warnf("Landed on %s instead of %s after applying props", finalPath, expectedPath);
                }

                Path outputDir = Path.of(config.outputDir());
                Files.createDirectories(outputDir);
                Path screenshotPath = outputDir.resolve("widget_" + uid + ".png");

                Locator locator = editorPage.locator(".card");
                locator.screenshot(new Locator.ScreenshotOptions().setPath(screenshotPath));

                Log.infof("Screenshot saved to: %s", screenshotPath.toAbsolutePath());

                if (ImageUtil.isCompletelyWhite(screenshotPath, 0, true)) {
                    throw new IllegalStateException("Screenshot is completely white");
                }

                return screenshotPath.toAbsolutePath().toString();
            } finally {
                editorPage.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyPropsViaDialog(Page editorPage, String uid, String propsJson) {
        Map<String, Object> props;
        try {
            props = jsonMapper.readValue(propsJson, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid propsJson: " + propsJson, e);
        }
        if (props.isEmpty()) {
            return;
        }
        List<ParamInfo> params = getWidgetParams(uid);
        Log.infof("Opening Set Props dialog to apply %d props; widget params: %s", props.size(), params);

        editorPage.locator("a:has-text('Set Props')").first().click(new Locator.ClickOptions().setTimeout(5000));

        Locator dialog = editorPage.locator(".popup.modal-in, .dialog.modal-in").first();
        dialog.waitFor(new Locator.WaitForOptions().setTimeout(5000));
        editorPage.waitForTimeout(500);

        int itemPickerIndex = 0;
        for (ParamInfo p : params) {
            boolean isItem = "item".equals(p.context);
            if (props.containsKey(p.name)) {
                String value = String.valueOf(props.get(p.name));
                try {
                    if (isItem) {
                        setItemProp(editorPage, dialog, itemPickerIndex, p, value);
                    } else {
                        setTextProp(dialog, p.name, value);
                    }
                    Log.infof("Set prop %s = %s (context=%s)", p.name, value, isItem ? "item" : "text");
                } catch (Exception e) {
                    Log.errorf("Failed to set prop %s = %s (context=%s): %s", p.name, value, isItem ? "item" : "text",
                            e.getMessage());
                    throw new IllegalStateException("Could not set prop '" + p.name + "': " + e.getMessage(), e);
                }
            }
            if (isItem) {
                itemPickerIndex++;
            }
        }

        Locator closeBtn = dialog.locator(".popup-close, a.link.popup-close, a:has-text('Done')").first();
        if (closeBtn.count() > 0) {
            closeBtn.click(new Locator.ClickOptions().setTimeout(3000));
        } else {
            editorPage.keyboard().press("Escape");
        }
        editorPage.waitForTimeout(2000);
    }

    private void setTextProp(Locator dialog, String name, String value) {
        Locator input = dialog.locator("input[name='" + name + "']").first();
        input.fill(value, new Locator.FillOptions().setTimeout(5000));
        input.press("Tab");
    }

    private void setItemProp(Page editorPage, Locator dialog, int itemPickerIndex, ParamInfo param, String itemName) {
        Locator picker = dialog.locator("li.item-picker").nth(itemPickerIndex);
        picker.waitFor(new Locator.WaitForOptions().setTimeout(5000));

        picker.locator("a.item-link").first().click(new Locator.ClickOptions().setTimeout(5000));

        Locator pickerPopup = editorPage.locator(".popup.modal-in:not(.popup-behind):not(.widgetprops-popup)").first();
        pickerPopup.waitFor(new Locator.WaitForOptions().setTimeout(5000));
        editorPage.waitForTimeout(300);

        Locator search = pickerPopup.locator("input[type='search'][placeholder='Search items']").first();
        search.fill(itemName, new Locator.FillOptions().setTimeout(5000));
        editorPage.waitForTimeout(500);

        Locator radioLabel = pickerPopup.locator("label.item-radio")
                .filter(new Locator.FilterOptions().setHasText("(" + itemName + ")")).first();
        if (radioLabel.count() == 0) {
            radioLabel = pickerPopup.locator("label.item-radio")
                    .filter(new Locator.FilterOptions().setHasText(itemName)).first();
        }
        if (radioLabel.count() == 0) {
            throw new IllegalStateException("Item '" + itemName + "' not in picker results for prop '" + param.name
                    + "' (label='" + param.label + "')");
        }
        radioLabel.click(new Locator.ClickOptions().setForce(true).setTimeout(5000));

        editorPage.waitForTimeout(500);
        if (pickerPopup.count() > 0) {
            Locator closeNested = pickerPopup.locator("a.link:has-text('Close'), .popup-close").first();
            if (closeNested.count() > 0) {
                try {
                    closeNested.click(new Locator.ClickOptions().setTimeout(2000));
                } catch (Exception ignored) {
                    editorPage.keyboard().press("Escape");
                }
            }
        }
        editorPage.waitForTimeout(500);
    }

    @SuppressWarnings("unchecked")
    public List<ParamInfo> getWidgetParams(String uid) {
        List<ParamInfo> params = new ArrayList<>();
        try {
            String json = getWidget(uid);
            if (json == null || json.startsWith("Widget not found") || json.startsWith("Error")) {
                Log.warnf("Cannot read widget definition for %s: %s", uid, json);
                return params;
            }
            Map<String, Object> widget = jsonMapper.readValue(json, Map.class);
            Map<String, Object> propsMap = (Map<String, Object>) widget.get("props");
            if (propsMap == null)
                return params;
            List<Map<String, Object>> parameters = (List<Map<String, Object>>) propsMap.get("parameters");
            if (parameters == null)
                return params;
            for (Map<String, Object> param : parameters) {
                String name = (String) param.get("name");
                String label = (String) param.get("label");
                String context = (String) param.get("context");
                if (name != null) {
                    params.add(new ParamInfo(name, label == null ? name : label, context == null ? "" : context));
                }
            }
        } catch (Exception e) {
            Log.warnf("Could not parse widget definition for %s: %s", uid, e.getMessage());
        }
        return params;
    }

    public enum CreateOrUpdateState {
        CREATED, UPDATED
    }

    public record CreateOrUpdateWidget(String uid, CreateOrUpdateState state) {
    }

    public record ParamInfo(String name, String label, String context) {
    }
}
