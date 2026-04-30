package org.openhab.widget.mcp.client;

import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import org.eclipse.microprofile.config.ConfigProvider;
import org.openhab.widget.mcp.service.BrowserService;

import java.io.IOException;

/**
 * Adds an {@code Authorization} header to outgoing OpenHAB REST calls. Resolution order:
 * <ol>
 *   <li>{@code openhab.api-token} → {@code Bearer <token>} (set once at startup, fastest path)</li>
 *   <li>OAuth2 access token extracted from {@link BrowserService}'s logged-in browser session
 *       (works whenever {@code openhab.username}/{@code password} are configured for browser
 *       login — OpenHAB 5 does NOT accept HTTP Basic Auth for REST calls)</li>
 *   <li>No header — anonymous (only reads will succeed)</li>
 * </ol>
 */
public class AuthRequestFilter implements ClientRequestFilter {

    private final String staticAuthHeader;

    public AuthRequestFilter() {
        var config = ConfigProvider.getConfig();
        String apiToken = config.getOptionalValue("openhab.api-token", String.class).orElse("");
        this.staticAuthHeader = apiToken.isBlank() ? "" : "Bearer " + apiToken;
    }

    @Override
    public void filter(ClientRequestContext ctx) throws IOException {
        if (!staticAuthHeader.isBlank()) {
            ctx.getHeaders().putSingle("Authorization", staticAuthHeader);
            return;
        }
        // Fall back to the OAuth2 access token extracted from the BrowserService's browser
        // session. ensureToken() blocks if startup login is still in progress.
        try {
            BrowserService browserService = Arc.container().instance(BrowserService.class).get();
            if (browserService == null) {
                return;
            }
            String token = browserService.getAccessToken();
            if (token.isBlank()) {
                token = browserService.ensureToken();
            }
            if (!token.isBlank()) {
                ctx.getHeaders().putSingle("Authorization", "Bearer " + token);
            }
        } catch (Exception e) {
            Log.warn("Error getting OAuth2 access token from browser session", e);
            // No browser-derived token available — proceed without auth
        }
    }
}
