package org.openhab.widget.mcp.service;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;

@Startup
public class TokenRefreshService {
    @Inject
    ItemService itemService;

    @Scheduled(cron = "0 0/5 * * * ?")
    void refreshToken() {
        Log.info("Refreshing OpenHAB access token");
        itemService.listItems(null);
    }
}
