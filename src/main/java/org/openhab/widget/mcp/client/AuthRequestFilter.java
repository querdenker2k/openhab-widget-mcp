package org.openhab.widget.mcp.client;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;

@Slf4j
public class AuthRequestFilter implements ClientRequestFilter {

  private final String staticAuthHeader;

  public AuthRequestFilter() {
    String apiToken =
        ConfigProvider.getConfig().getOptionalValue("openhab.api-token", String.class).orElse("");
    this.staticAuthHeader = apiToken.isBlank() ? "" : "Bearer " + apiToken;
  }

  @Override
  public void filter(ClientRequestContext ctx) {
    if (!staticAuthHeader.isBlank()) {
      ctx.getHeaders().putSingle("Authorization", staticAuthHeader);
    }
  }
}
