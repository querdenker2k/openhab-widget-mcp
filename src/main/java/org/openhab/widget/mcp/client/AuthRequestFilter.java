package org.openhab.widget.mcp.client;

import io.quarkus.arc.Arc;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import org.eclipse.microprofile.config.ConfigProvider;
import org.openhab.widget.mcp.service.BrowserService;

import java.io.IOException;

public class AuthRequestFilter implements ClientRequestFilter {

    private final String apiToken;

    public AuthRequestFilter() {
        var config = ConfigProvider.getConfig();
        this.apiToken = config.getOptionalValue("openhab.api-token", String.class).orElse("");
    }

    @Override
    public void filter(ClientRequestContext ctx) throws IOException {
        if (!apiToken.isBlank()) {
            ctx.getHeaders().putSingle("Authorization", "Bearer " + apiToken);
            return;
        }
        // Use OAuth2 access token from browser session.
        // ensureToken() blocks if login is still in progress (race between HTTP server
        // accepting connections and the startup browser login completing).
        try {
            BrowserService browserService = Arc.container().instance(BrowserService.class).get();
            String token = browserService.getAccessToken();
            if (token.isBlank()) {
                token = browserService.ensureToken();
            }
            if (!token.isBlank()) {
                ctx.getHeaders().putSingle("Authorization", "Bearer " + token);
            }
        } catch (Exception e) {
            // No browser service or token available — proceed without auth
        }
    }
}
