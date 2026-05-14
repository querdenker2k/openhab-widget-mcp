package org.openhab.widget.mcp.mcp;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.openhab.widget.mcp.service.PersistenceService;

@ApplicationScoped
public class PersistenceTools {

  @Inject PersistenceService persistenceService;

  @Tool(
      description =
          """
            Add historical or future data to an OpenHAB item using persistence service. \
            Requires the persistence service to be configured in OpenHAB.""")
  public String addPersistenceData(
      @ToolArg(description = "The name of the item to add data for") String itemName,
      @ToolArg(
              description =
                  "The timestamp for the data point, e.g. 2023-10-27T10:00:00Z. "
                      + "Must be in ISO 8601 format.")
          String time,
      @ToolArg(description = "The state/value to persist at that time") String state) {
    return persistenceService.addPersistenceData(itemName, time, state);
  }
}
