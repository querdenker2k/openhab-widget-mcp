package org.openhab.widget.mcp.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "openhab")
public interface OpenHabConfig {

    @WithDefault("http://localhost:8080")
    String url();

    Optional<String> apiToken();

    Optional<String> username();

    Optional<String> password();

    @WithDefault("/tmp/openhab-screenshots")
    String outputDir();
}
