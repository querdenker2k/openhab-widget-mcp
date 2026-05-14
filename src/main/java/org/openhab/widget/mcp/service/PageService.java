package org.openhab.widget.mcp.service;

import static org.openhab.widget.mcp.util.RsUtil.safeInvoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.openhab.widget.mcp.client.OpenHabClient;
import org.openhab.widget.mcp.config.OpenHabConfig;
import org.openhab.widget.mcp.model.DeleteState;
import org.openhab.widget.mcp.util.ImageUtil;

@ApplicationScoped
public class PageService {
  @Inject @RestClient OpenHabClient openHabClient;

  @Inject BrowserService browserService;

  @Inject OpenHabConfig config;

  private final ObjectMapper jsonMapper = new ObjectMapper();

  public DeleteState deletePage(String uid) {
    Log.infof("deletePage: %s", uid);
    Response response = safeInvoke(() -> openHabClient.deletePage(uid));
    int status = response.getStatus();
    if (status == 200 || status == 204) {
      Log.infof("deletePage: '%s' deleted HTTP %d", uid, status);
      return DeleteState.DELETED;
    } else if (status == 404) {
      return DeleteState.NOT_FOUND;
    } else {
      Log.warnf("deletePage: unexpected HTTP %d for '%s'", status, uid);
      throw new IllegalStateException("Error deleting page '%s': HTTP %d".formatted(uid, status));
    }
  }

  @SneakyThrows
  public CreateOrUpdatePage createOrUpdatePage(
      String uid, String label, String widgetUid, String propsJson) {
    Log.infof("createOrUpdatePage: uid=%s, label=%s, widgetUid=%s", uid, label, widgetUid);
    @SuppressWarnings("unchecked")
    Map<String, Object> props =
        propsJson == null || propsJson.isBlank()
            ? Map.of()
            : jsonMapper.readValue(propsJson, Map.class);

    Map<String, Object> widgetRef = new LinkedHashMap<>();
    widgetRef.put("component", "widget:" + widgetUid);
    widgetRef.put("config", props);

    Map<String, Object> canvasItem = new LinkedHashMap<>();
    canvasItem.put("component", "oh-canvas-item");
    canvasItem.put(
        "config", Map.of("x", 0, "y", 0, "h", config.page().height(), "w", config.page().width()));
    canvasItem.put("slots", Map.of("default", List.of(widgetRef)));

    Map<String, Object> canvasLayer = new LinkedHashMap<>();
    canvasLayer.put("component", "oh-canvas-layer");
    canvasLayer.put("config", Map.of());
    canvasLayer.put("slots", Map.of("default", List.of(canvasItem)));

    Map<String, Object> page = new LinkedHashMap<>();
    page.put("uid", uid);
    page.put("component", "oh-layout-page");
    page.put(
        "config",
        Map.of(
            "label",
            label,
            "layoutType",
            "fixed",
            "fixedType",
            "canvas",
            "gridEnable",
            true,
            "screenWidth",
            config.page().width(),
            "screenHeight",
            config.page().height(),
            "scale",
            false,
            "sidebar",
            true));
    page.put("tags", List.of());
    page.put("props", Map.of("parameters", List.of(), "parameterGroups", List.of()));
    page.put(
        "slots",
        Map.of(
            "default", List.of(),
            "masonry", List.of(),
            "grid", List.of(),
            "canvas", List.of(canvasLayer)));

    String jsonBody = jsonMapper.writeValueAsString(page);

    Response checkResponse = safeInvoke(() -> openHabClient.getPage(uid));
    if (checkResponse.getStatus() == 200) {
      Response updateResponse = safeInvoke(() -> openHabClient.updatePage(uid, jsonBody));
      int updateStatus = updateResponse.getStatus();
      if (updateStatus == 200) {
        Log.infof("createOrUpdatePage: updated '%s' HTTP %d", uid, updateStatus);
        return new CreateOrUpdatePage(uid, CreateOrUpdateState.UPDATED);
      } else {
        Log.warnf("createOrUpdatePage: update failed for '%s' HTTP %d", uid, updateStatus);
        throw new IllegalStateException(
            "Error updating page '%s': HTTP %d".formatted(uid, updateStatus));
      }
    } else {
      Response createResponse = safeInvoke(() -> openHabClient.createPage(jsonBody));
      int status = createResponse.getStatus();
      if (status == 200 || status == 201) {
        Log.infof("createOrUpdatePage: created '%s' HTTP %d", uid, status);
        return new CreateOrUpdatePage(uid, CreateOrUpdateState.CREATED);
      } else {
        Log.warnf("createOrUpdatePage: create failed for '%s' HTTP %d", uid, status);
        throw new IllegalStateException("Error creating page '%s': HTTP %d".formatted(uid, status));
      }
    }
  }

  public record WidgetPlacement(String widgetUid, int x, int y, int w, int h, String propsJson) {}

  @SneakyThrows
  public CreateOrUpdatePage createComplexPage(
      String uid, String label, List<WidgetPlacement> placements) {
    Log.infof("createComplexPage: uid=%s, label=%s, %d placements", uid, label, placements.size());

    List<Map<String, Object>> canvasItems = new ArrayList<>();
    for (WidgetPlacement p : placements) {
      @SuppressWarnings("unchecked")
      Map<String, Object> props =
          (p.propsJson() == null || p.propsJson().isBlank())
              ? Map.of()
              : jsonMapper.readValue(p.propsJson(), Map.class);

      Map<String, Object> widgetRef = new LinkedHashMap<>();
      widgetRef.put("component", "widget:" + p.widgetUid());
      widgetRef.put("config", props);

      Map<String, Object> canvasItem = new LinkedHashMap<>();
      canvasItem.put("component", "oh-canvas-item");
      canvasItem.put("config", Map.of("x", p.x(), "y", p.y(), "h", p.h(), "w", p.w()));
      canvasItem.put("slots", Map.of("default", List.of(widgetRef)));
      canvasItems.add(canvasItem);
    }

    Map<String, Object> canvasLayer = new LinkedHashMap<>();
    canvasLayer.put("component", "oh-canvas-layer");
    canvasLayer.put("config", Map.of());
    canvasLayer.put("slots", Map.of("default", canvasItems));

    Map<String, Object> page = new LinkedHashMap<>();
    page.put("uid", uid);
    page.put("component", "oh-layout-page");
    page.put(
        "config",
        Map.of(
            "label",
            label,
            "layoutType",
            "fixed",
            "fixedType",
            "canvas",
            "gridEnable",
            true,
            "screenWidth",
            config.page().width(),
            "screenHeight",
            config.page().height(),
            "scale",
            false,
            "sidebar",
            false));
    page.put("tags", List.of());
    page.put("props", Map.of("parameters", List.of(), "parameterGroups", List.of()));
    page.put(
        "slots",
        Map.of(
            "default", List.of(),
            "masonry", List.of(),
            "grid", List.of(),
            "canvas", List.of(canvasLayer)));

    String jsonBody = jsonMapper.writeValueAsString(page);

    Response checkResponse = safeInvoke(() -> openHabClient.getPage(uid));
    if (checkResponse.getStatus() == 200) {
      Response updateResponse = safeInvoke(() -> openHabClient.updatePage(uid, jsonBody));
      int updateStatus = updateResponse.getStatus();
      if (updateStatus == 200) {
        Log.infof("createComplexPage: updated '%s' HTTP %d", uid, updateStatus);
        return new CreateOrUpdatePage(uid, CreateOrUpdateState.UPDATED);
      } else {
        throw new IllegalStateException(
            "Error updating page '%s': HTTP %d".formatted(uid, updateStatus));
      }
    } else {
      Response createResponse = safeInvoke(() -> openHabClient.createPage(jsonBody));
      int status = createResponse.getStatus();
      if (status == 200 || status == 201) {
        Log.infof("createComplexPage: created '%s' HTTP %d", uid, status);
        return new CreateOrUpdatePage(uid, CreateOrUpdateState.CREATED);
      } else {
        throw new IllegalStateException("Error creating page '%s': HTTP %d".formatted(uid, status));
      }
    }
  }

  public record CreateOrUpdatePage(String uid, CreateOrUpdateState state) {}

  public enum CreateOrUpdateState {
    CREATED,
    UPDATED
  }

  public String screenshotPage(String uid) throws IOException {
    synchronized (browserService) {
      Log.infof("screenshotPage: uid=%s", uid);
      Page p = browserService.createPage();
      try {
        Path outputDir = Path.of(config.outputDir());
        if (!Files.exists(outputDir)) {
          Files.createDirectories(outputDir);
        }

        String targetUrl = config.url() + "/page/" + uid;
        String expectedPath = "/page/" + uid;

        browserService.navigateAuthenticated(p, targetUrl, expectedPath);

        Log.info("Waiting for page to settle");
        p.waitForTimeout(3000);

        Path outputPath = outputDir.resolve("page_" + uid + ".png");

        Locator locator = p.locator(".oh-canvas-layout");

        // 2. Screenshot des gesamten Frame-Inhalts (body) machen
        locator.screenshot(new Locator.ScreenshotOptions().setPath(outputPath));

        Log.infof("Screenshot saved to: %s", outputPath.toAbsolutePath());
        if (ImageUtil.isCompletelyWhite(outputPath, 0, true)) {
          throw new IllegalStateException("Screenshot is completely white");
        }
        return outputPath.toAbsolutePath().toString();
      } finally {
        p.close();
      }
    }
  }
}
