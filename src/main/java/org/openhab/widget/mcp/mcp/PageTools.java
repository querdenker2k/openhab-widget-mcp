package org.openhab.widget.mcp.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.WrapBusinessError;
import lombok.SneakyThrows;
import org.openhab.widget.mcp.config.OpenHabConfig;
import org.openhab.widget.mcp.model.DeleteState;
import org.openhab.widget.mcp.service.PageService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class PageTools {

    @Inject
    PageService pageService;

    @Inject
    OpenHabConfig config;

    @Tool(description = "Take a screenshot of an OpenHAB page as it appears in the browser. "
            + "Returns the absolute path to the saved PNG file.")
    public String screenshotPage(
            @ToolArg(description = "The page UID to screenshot, e.g. my_car_page") String uid) {
        try {
            String path = pageService.screenshotPage(uid);
            return "Screenshot saved to: " + path;
        } catch (Exception e) {
            return "Error taking page screenshot: " + e.getMessage();
        }
    }

    @Tool(description = """
            Create or update a persistent test page in OpenHAB that embeds an existing widget. \
            The page shows up in the sidebar so you can preview the widget on a real page. \
            Idempotent: calling again with the same widget UID updates the existing test page. \
            All arguments except widgetUid are optional (pass null/empty for defaults). \
            Both pageUid and label default to the widget UID. \
            A page should never created twice for the same widget. \
            Check before if there already a test page exist for this widget.""")
    public PageService.CreateOrUpdatePage createTestPageForWidget(
            @ToolArg(description = "UID of the widget to embed, e.g. RD_car_charging_widget") String widgetUid,
            @ToolArg(required = false, defaultValue = "", description = "Optional page label") String label,
            @ToolArg(required = false, defaultValue = "", description = "Optional page UID") String pageUid,
            @ToolArg(required = false, defaultValue = "{}", description = "Optional widget props JSON, e.g. {\"title\":\"Auto\"}") String propsJson) {
        String resolvedPageUid = (pageUid == null || pageUid.isBlank()) ? widgetUid : pageUid;
        String resolvedLabel = (label == null || label.isBlank()) ? widgetUid : label;
        String resolvedProps = (propsJson == null || propsJson.isBlank()) ? "{}" : propsJson;
        return pageService.createOrUpdatePage(resolvedPageUid, resolvedLabel, widgetUid, resolvedProps);
    }

    @Tool(description = "Delete a page from OpenHAB by its UID.")
    @WrapBusinessError
    public DeleteState deletePage(
            @ToolArg(description = "The page UID to delete, e.g. RD_car_charging_widget") String uid) {
        return pageService.deletePage(uid);
    }

    @SneakyThrows
    @Tool(description = """
            Create or update an OpenHAB page with multiple widgets arranged on a canvas. \
            Accepts a JSON array of widget placements, each with widgetUid, x, y, w, h, \
            and optional propsJson. Canvas size comes from server config (openhab.page-width/height). \
            Idempotent: calling again with the same pageUid updates the existing page.""")
    @WrapBusinessError
    public PageService.CreateOrUpdatePage createPage(
            @ToolArg(description = "The page UID, e.g. my_living_room_page") String pageUid,
            @ToolArg(description = "The page label shown in the sidebar") String label,
            @ToolArg(description = """
                    JSON array of widget placements. Each entry: \
                    widgetUid (required), x, y, w, h (all int, required), propsJson (optional). \
                    Example: [{"widgetUid":"car_widget","x":0,"y":0,"w":600,"h":400,"propsJson":"{}"},\
                    {"widgetUid":"weather_widget","x":600,"y":0,"w":600,"h":400,"propsJson":"{}"}]""")
            String placementsJson) {
        List<PageService.WidgetPlacement> placements = new ObjectMapper()
                .readValue(placementsJson, new TypeReference<>() {});
        return pageService.createComplexPage(pageUid, label, placements);
    }
}
