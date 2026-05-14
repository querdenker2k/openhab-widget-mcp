package org.openhab.widget.mcp.rest;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.openhab.widget.mcp.config.OpenHabConfig;
import org.openhab.widget.mcp.service.PageService;

@Path("/api/pages")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Pages", description = "Manage OpenHAB layout pages")
public class PageResource {
	@Inject
	OpenHabConfig config;

	@Inject
	PageService pageService;

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Operation(summary = "Create or update a canvas page", description = "Creates a fixed canvas layout page (800x600) embedding the specified widget.")
	public Response createOrUpdatePage(PageRequest request) {
		Log.infof("REST createOrUpdatePage: uid=%s, label=%s, widgetUid=%s", request.uid(), request.label(),
				request.widgetUid());
		PageService.CreateOrUpdatePage result = pageService.createOrUpdatePage(request.uid(), request.label(),
				request.widgetUid(), request.propsJson() != null ? request.propsJson() : "{}");
		return Response.ok(Map.of("message", result)).build();
	}

	@POST
	@Path("/{uid}/testpage")
	@Operation(summary = "Create or update a test page that embeds this widget", description = "Convenient endpoint to spin up a persistent OpenHAB page (visible in the sidebar) "
			+ "that embeds the given widget. Idempotent: calling again with the same widget UID updates "
			+ "the existing test page. All non-path parameters are optional; label and pageUid both "
			+ "default to the widget UID.")
	public Response createTestPage(
			@Parameter(description = "Widget UID to embed, e.g. RD_car_charging_widget") @PathParam("uid") String widgetUid,
			@Parameter(description = "Page label shown in the sidebar (default: widget UID)") @QueryParam("label") String label,
			@Parameter(description = "Page UID (default: widget UID)") @QueryParam("pageUid") String pageUid,
			@Parameter(description = "JSON object with widget props, e.g. {\"title\":\"Auto\"}. Default: {}") @QueryParam("propsJson") String propsJson) {
		String resolvedPageUid = (pageUid == null || pageUid.isBlank()) ? widgetUid : pageUid;
		String resolvedLabel = (label == null || label.isBlank()) ? widgetUid : label;
		String resolvedProps = (propsJson == null || propsJson.isBlank()) ? "{}" : propsJson;
		Log.infof("REST createTestPage: widgetUid=%s, pageUid=%s, label=%s, props=%s", widgetUid, resolvedPageUid,
				resolvedLabel, resolvedProps);
		PageService.CreateOrUpdatePage message = pageService.createOrUpdatePage(resolvedPageUid, resolvedLabel,
				widgetUid, resolvedProps);
		String pageUrl = config.url() + "/page/" + resolvedPageUid;
		return Response.ok(Map.of("message", message, "pageUid", resolvedPageUid, "pageUrl", pageUrl)).build();
	}

	@GET
	@Path("/{uid}/screenshot")
	@Produces("image/png")
	@Operation(summary = "Screenshot a page", description = "Opens the page in the browser and returns a PNG screenshot.")
	public Response screenshotPage(
			@Parameter(description = "Page UID, e.g. my_car_page") @PathParam("uid") String uid) {
		Log.infof("REST screenshotPage: %s", uid);
		try {
			String path = pageService.screenshotPage(uid);
			File file = new File(path);
			return Response.ok(file).header("Content-Disposition", "inline; filename=\"page_" + uid + ".png\"").build();
		} catch (Exception e) {
			Log.error("Error creating screenshot for page " + uid, e);
			return Response.serverError().type(MediaType.APPLICATION_JSON).entity(Map.of("error", e.getMessage()))
					.build();
		}
	}

	public record PageRequest(@Parameter(description = "Unique page ID, e.g. my_car_page") String uid,
			@Parameter(description = "Display label for the page") String label,
			@Parameter(description = "UID of the widget to embed") String widgetUid,
			@Parameter(description = "JSON object with widget props, e.g. {\"title\": \"Auto\"}") String propsJson) {
	}
}
