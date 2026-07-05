package org.openhab.widget.mcp.model;

public enum PageLayout {
    CANVAS, GRID;

    public static PageLayout fromString(String value) {
        if (value == null || value.isBlank()) {
            return CANVAS;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid layout '%s': must be one of canvas, grid".formatted(value));
        }
    }
}
