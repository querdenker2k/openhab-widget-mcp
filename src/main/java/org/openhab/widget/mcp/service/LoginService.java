package org.openhab.widget.mcp.service;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.openhab.widget.mcp.config.OpenHabConfig;

@ApplicationScoped
public class LoginService {

  @Inject OpenHabConfig config;

  @Inject BrowserService browserService;

  public synchronized void login(Page p) {
    String staticToken = config.apiToken();

    if (!staticToken.isBlank()) {
      Log.info("API Token configured — trying to pre-populate localStorage");
      try {
        p.evaluate(
            "token => {"
                + "  localStorage.setItem('openhab.ui:token', token);"
                + "  localStorage.setItem('openhab.ui:refreshToken', token);"
                + "  localStorage.setItem('openhab.ui:token_expiry', (Date.now() + 86400000).toString());"
                + "}",
            staticToken);
        Log.info("LocalStorage populated with static token");
      } catch (Exception e) {
        Log.warnf("Failed to populate localStorage: %s", e.getMessage());
      }
    }

    Log.infof("Starting interactive browser login for user '%s'", config.username());
    try {
      // If we are already on /auth, use it. Otherwise go there.
      if (!"/auth".equals(BrowserService.pathOf(p.url()))) {
        String overviewUrl = config.url() + "/overview/";
        p.navigate(overviewUrl);
        p.waitForLoadState(LoadState.LOAD);
        p.waitForTimeout(2000);
      }

      if (!"/auth".equals(BrowserService.pathOf(p.url()))) {
        Log.info("Not on /auth after overview load — clicking login button");
        try {
          p.click("a.button.color-gray", new Page.ClickOptions().setTimeout(5000));
          p.waitForURL(
              url -> "/auth".equals(BrowserService.pathOf(url)),
              new Page.WaitForURLOptions().setTimeout(15000));
        } catch (Exception e) {
          Log.warnf("Could not reach /auth via login button: %s", e.getMessage());
          // Fallback: try navigating to /auth directly
          p.navigate(config.url() + "/auth");
        }
      }
      Log.infof("Auth page reached: %s", p.url());

      Log.infof("Filling login form for user '%s'", config.username());
      p.waitForSelector(
          "input[placeholder='User Name']", new Page.WaitForSelectorOptions().setTimeout(10000));
      p.fill("input[placeholder='User Name']", config.username());
      p.fill("input[placeholder='Password']", config.password());
      Log.info("Submitting login form");
      p.click("input[type='Submit']");

      Log.info("Waiting for SPA to process auth code and leave /auth");
      p.waitForURL(
          url -> !"/auth".equals(BrowserService.pathOf(url)),
          new Page.WaitForURLOptions().setTimeout(30000));
      p.waitForTimeout(2000);
      Log.infof("Login complete, current URL: %s", p.url());

      extractAccessToken(p);
    } catch (Exception e) {
      Log.errorf(e, "Browser login failed: %s", e.getMessage());
    }
  }

  public synchronized void login() {
    Page p = browserService.createPage();
    login(p);
    p.close();
  }

  private void extractAccessToken(Page p) {
    Log.info("Extracting OAuth2 access token from browser session");
    try {
      Object result =
          p.evaluate(
              """
                    async () => {
                        const rt = localStorage.getItem('openhab.ui:refreshToken');
                        if (!rt) return null;
                        const resp = await fetch('/rest/auth/token', {
                            method: 'POST',
                            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                            body: new URLSearchParams({
                                grant_type: 'refresh_token',
                                refresh_token: rt,
                                client_id: window.location.origin,
                                redirect_uri: window.location.origin + '/auth'
                            })
                        });
                        const data = await resp.json();
                        return data.access_token || null;
                    }
                    """);
      if (result instanceof String token && !token.isBlank()) {
        Log.infof("OAuth2 access token extracted successfully (%d chars)", token.length());
      } else {
        Log.warn("No access token in token response — REST calls may fail without auth");
      }
    } catch (Exception e) {
      Log.warnf(e, "Failed to extract access token: %s", e.getMessage());
    }
  }
}
