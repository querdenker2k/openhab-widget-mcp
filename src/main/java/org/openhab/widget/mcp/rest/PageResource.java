package org.openhab.widget.mcp.rest;

import org.openhab.widget.mcp.service.BrowserService;
import org.openhab.widget.mcp.service.WidgetService;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.File;
import java.util.Map;

@Path("/api/pages")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Pages", description = "Manage OpenHAB layout pages")
public class PageResource {

    @Inject
    WidgetService widgetService;

    @Inject
    BrowserService browserService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create or update a canvas page",
            description = "Creates a fixed canvas layout page (800x600) embedding the specified widget.")
    public Response createOrUpdatePage(PageRequest request) {
        Log.infof("REST createOrUpdatePage: uid=%s, label=%s, widgetUid=%s", request.uid(), request.label(), request.widgetUid());
        String result = widgetService.createOrUpdatePage(
                request.uid(), request.label(), request.widgetUid(),
                request.propsJson() != null ? request.propsJson() : "{}");
        return Response.ok(Map.of("message", result)).build();
    }

    @GET
    @Path("/{uid}/screenshot")
    @Produces("image/png")
    @Operation(summary = "Screenshot a page",
            description = "Opens the page in the browser and returns a PNG screenshot.")
    public Response screenshotPage(
            @Parameter(description = "Page UID, e.g. my_car_page")
            @PathParam("uid") String uid) {
        Log.infof("REST screenshotPage: %s", uid);
        try {
            String path = browserService.screenshotPage(uid);
            File file = new File(path);
            return Response.ok(file)
                    .header("Content-Disposition", "inline; filename=\"page_" + uid + ".png\"")
                    .build();
        } catch (Exception e) {
            return Response.serverError()
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    public record PageRequest(
            @Parameter(description = "Unique page ID, e.g. my_car_page") String uid,
            @Parameter(description = "Display label for the page") String label,
            @Parameter(description = "UID of the widget to embed") String widgetUid,
            @Parameter(description = "JSON object with widget props, e.g. {\"title\": \"Auto\"}") String propsJson
    ) {}
}
