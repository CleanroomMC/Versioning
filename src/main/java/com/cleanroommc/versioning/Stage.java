package com.cleanroommc.versioning;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Allowed release stages.
 */
public enum Stage {

    ALPHA("alpha"),
    BETA("beta"),
    RC("rc"),
    RELEASE("release");

    public static Stage parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new VersioningException("versioning.stage must be one of: " + ids() + " (got '" + value + "')");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (Stage stage : values()) {
            if (stage.id.equals(normalized)) {
                return stage;
            }
        }
        throw new VersioningException("versioning.stage must be one of: " + ids() + " (got '" + value + "')");
    }

    private static String ids() {
        return Arrays.stream(values()).map(Stage::id).collect(Collectors.joining(", ", "[", "]"));
    }

    private final String id;

    Stage(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public boolean isRelease() {
        return this == RELEASE;
    }

    public String artifactSuffix() {
        return isRelease() ? "" : "-" + this.id;
    }

}
