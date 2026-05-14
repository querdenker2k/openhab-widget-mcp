package org.openhab.widget.mcp.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "openhab")
@RegisterProvider(AuthRequestFilter.class)
@Path("/rest")
public interface OpenHabClient {

  // --- Widgets ---

  @GET
  @Path("/ui/components/ui:widget")
  @Produces(MediaType.APPLICATION_JSON)
  Response listWidgets();

  @GET
  @Path("/ui/components/ui:widget/{uid}")
  @Produces(MediaType.APPLICATION_JSON)
  Response getWidget(@PathParam("uid") String uid);

  @POST
  @Path("/ui/components/ui:widget")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  Response createWidget(String body);

  @PUT
  @Path("/ui/components/ui:widget/{uid}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  Response updateWidget(@PathParam("uid") String uid, String body);

  @DELETE
  @Path("/ui/components/ui:widget/{uid}")
  Response deleteWidget(@PathParam("uid") String uid);

  // --- Pages ---

  @GET
  @Path("/ui/components/ui:page/{uid}")
  @Produces(MediaType.APPLICATION_JSON)
  Response getPage(@PathParam("uid") String uid);

  @POST
  @Path("/ui/components/ui:page")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  Response createPage(String body);

  @PUT
  @Path("/ui/components/ui:page/{uid}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  Response updatePage(@PathParam("uid") String uid, String body);

  @DELETE
  @Path("/ui/components/ui:page/{uid}")
  Response deletePage(@PathParam("uid") String uid);

  // --- Items ---

  @GET
  @Path("/items")
  @Produces(MediaType.APPLICATION_JSON)
  Response listItems();

  @GET
  @Path("/items/{name}/state")
  @Produces(MediaType.TEXT_PLAIN)
  Response getItemState(@PathParam("name") String name);

  @POST
  @Path("/items/{name}")
  @Consumes(MediaType.TEXT_PLAIN)
  Response sendItemCommand(@PathParam("name") String name, String command);

  @PUT
  @Path("/items/{name}")
  @Consumes(MediaType.APPLICATION_JSON)
  Response createOrUpdateItem(@PathParam("name") String name, String itemJson);

  @DELETE
  @Path("/items/{name}")
  Response deleteItem(@PathParam("name") String name);

  @PUT
  @Path("/items/{name}/metadata/{namespace}")
  @Consumes(MediaType.APPLICATION_JSON)
  Response setItemMetadata(
      @PathParam("name") String name, @PathParam("namespace") String namespace, String body);

  @DELETE
  @Path("/items/{name}/metadata/{namespace}")
  Response deleteItemMetadata(
      @PathParam("name") String name, @PathParam("namespace") String namespace);

  // --- Persistence ---

  @PUT
  @Path("/persistence/items/{name}")
  Response addPersistenceData(
      @PathParam("name") String name,
      @QueryParam("time") String time,
      @QueryParam("state") String state);
}
