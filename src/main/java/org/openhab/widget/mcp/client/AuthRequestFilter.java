package org.openhab.widget.mcp.client;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AuthRequestFilter implements ClientRequestFilter {

    private final String authHeader;

    public AuthRequestFilter() {
        var config = ConfigProvider.getConfig();
        String apiToken = config.getOptionalValue("openhab.api-token", String.class).orElse("");
        if (!apiToken.isBlank()) {
            authHeader = "Bearer " + apiToken;
        } else {
            String username = config.getOptionalValue("openhab.username", String.class).orElse("");
            String password = config.getOptionalValue("openhab.password", String.class).orElse("");
            if (!username.isBlank()) {
                String credentials = username + ":" + password;
                authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            } else {
                authHeader = "";
            }
        }
    }

    @Override
    public void filter(ClientRequestContext ctx) throws IOException {
        if (!authHeader.isBlank()) {
            ctx.getHeaders().putSingle("Authorization", authHeader);
        }
    }
}
