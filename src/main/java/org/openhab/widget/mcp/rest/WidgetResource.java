package org.openhab.widget.mcp.rest;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.openhab.widget.mcp.service.WidgetService;

@Path("/api/widgets")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Widgets", description = "Manage OpenHAB custom widgets")
public class WidgetResource {

    @Inject
    WidgetService widgetService;

    @GET
    @Operation(summary = "List all widgets", description = "Returns all custom widgets registered in OpenHAB.")
    public Response listWidgets() {
        Log.info("REST listWidgets");
        return Response.ok(widgetService.listWidgets()).build();
    }

    @GET
    @Path("/{uid}")
    @Operation(summary = "Get a widget by UID")
    public Response getWidget(
            @Parameter(description = "Widget UID, e.g. RD_car_charging_widget") @PathParam("uid") String uid) {
        Log.infof("REST getWidget: %s", uid);
        return Response.ok(widgetService.getWidget(uid)).build();
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Upload a widget YAML file", description = "Creates or updates a widget by uploading a YAML file. Use the Swagger UI file picker or curl -F.")
    public Response uploadWidget(@RestForm("file") FileUpload file) {
        Log.infof("REST uploadWidget: %s", file.fileName());
        try {
            String yamlContent = Files.readString(file.uploadedFile());
            WidgetService.CreateOrUpdateWidget result = widgetService.createOrUpdateWidgetFromYaml(yamlContent);
            return Response.ok(Map.of("message", result)).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @POST
    @Path("/yaml")
    @Consumes({"text/plain", "application/yaml", "application/x-yaml"})
    @Operation(summary = "Create or update widget from YAML string", description = "Accepts a raw YAML string in the request body. Creates or updates the widget.")
    public Response uploadWidgetYaml(String yamlContent) {
        Log.infof("REST uploadWidgetYaml: %d chars", yamlContent == null ? 0 : yamlContent.length());
        WidgetService.CreateOrUpdateWidget result = widgetService.createOrUpdateWidgetFromYaml(yamlContent);
        return Response.ok(Map.of("message", result)).build();
    }

    @DELETE
    @Path("/{uid}")
    @Operation(summary = "Delete a widget by UID")
    public Response deleteWidget(@Parameter(description = "Widget UID to delete") @PathParam("uid") String uid) {
        Log.infof("REST deleteWidget: %s", uid);
        widgetService.deleteWidget(uid);
        return Response.ok().build();
    }

    @GET
    @Path("/{uid}/screenshot")
    @Produces("image/png")
    @Operation(summary = "Screenshot widget preview", description = "Opens the widget in the OpenHAB developer UI, optionally sets props, and returns a PNG screenshot.")
    public Response screenshotWidget(@Parameter(description = "Widget UID") @PathParam("uid") String uid,
            @Parameter(description = "JSON props object, e.g. {\"title\":\"Auto\"}. Leave empty for no props.") @QueryParam("props") String propsJson) {
        Log.infof("REST screenshotWidget: %s, props=%s", uid, propsJson);
        try {
            String path = widgetService.screenshotWidget(uid, propsJson != null ? propsJson : "{}");
            File file = new File(path);
            return Response.ok(file).header("Content-Disposition", "inline; filename=\"" + uid + ".png\"").build();
        } catch (Exception e) {
            Log.error("Error creating screenshot for widget " + uid, e);
            return Response.serverError().type(MediaType.APPLICATION_JSON).entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
