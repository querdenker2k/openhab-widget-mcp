package org.openhab.widget.mcp.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "openhab")
public interface OpenHabConfig {

    @WithDefault("http://localhost:8080")
    String url();

    String apiToken();

    String username();

    String password();

    @WithDefault("/tmp/openhab-screenshots")
    String outputDir();

    @WithDefault("true")
    boolean headless();

    Dimension page();

    Dimension screen();

    interface Dimension {
        int width();

        int height();
    }
}
