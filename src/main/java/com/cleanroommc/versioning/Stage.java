package com.cleanroommc.versioning;

import java.util.Arrays;
import java.util.Locale;

/**
 * Allowed release stages. An independent marker for upload sites and archive names, not part of the version.
 */
public enum Stage {

    ALPHA,
    BETA,
    RC,
    RELEASE;

    /**
     * Parses a stage name, case insensitively.
     *
     * @param value the text to parse
     * @return the parsed stage
     * @throws IllegalArgumentException if the text names no stage
     */
    public static Stage parse(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        for (Stage stage : values()) {
            if (stage.name().equals(normalized)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("versioning.stage must be one of "
                + Arrays.toString(values()).toLowerCase(Locale.ROOT) + " (got '" + value + "')");
    }

    /**
     * @return the lower case stage name
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

}
