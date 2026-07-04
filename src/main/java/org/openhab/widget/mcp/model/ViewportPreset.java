package org.openhab.widget.mcp.model;

import org.openhab.widget.mcp.config.OpenHabConfig;

public enum ViewportPreset {
    DESKTOP, TABLET, PHONE;

    public static ViewportPreset fromString(String value) {
        if (value == null || value.isBlank()) {
            return DESKTOP;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid device '%s': must be one of desktop, tablet, phone".formatted(value));
        }
    }

    public OpenHabConfig.Dimension dimension(OpenHabConfig config) {
        return switch (this) {
            case DESKTOP -> config.desktop();
            case TABLET -> config.tablet();
            case PHONE -> config.phone();
        };
    }
}
