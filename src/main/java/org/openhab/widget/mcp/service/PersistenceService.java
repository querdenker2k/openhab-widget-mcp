package org.openhab.widget.mcp.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.openhab.widget.mcp.client.OpenHabClient;

@Slf4j
@ApplicationScoped
public class PersistenceService {

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

    public String addPersistenceData(String itemName, String time, String state) {
        log.info("addPersistenceData: item={}, time={}, state={}", itemName, time, state);
        try {
            Response response = safeInvoke(() -> openHabClient.addPersistenceData(itemName, time, state));
            int status = response.getStatus();
            String resultText;
            if (status == 200 || status == 201 || status == 204) {
                resultText = "Persistence data added for item '" + itemName + "' at " + time;
            } else {
                String error = "";
                try {
                    if (response.hasEntity()) {
                        error = response.readEntity(String.class);
                    }
                } catch (Exception ignored) {
                }
                log.warn("addPersistenceData: failed with HTTP {}: {}", status, error);
                resultText = "Error adding persistence data to '" + itemName + "': HTTP " + status + " " + error;
            }
            log.info("[DEBUG_LOG] PersistenceService result: {}", resultText);
            return resultText;
        } catch (Exception e) {
            log.error("Error adding persistence data: " + itemName, e);
            return "Error adding persistence data: " + e.getMessage();
        }
    }
}
