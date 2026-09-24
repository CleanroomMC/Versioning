package com.cleanroommc.versioning.gradle;

import com.cleanroommc.versioning.SemanticVersion;
import com.cleanroommc.versioning.Stage;

record GitTag(SemanticVersion version, Stage stage) {

    static GitTag parse(String name) {
        int separator = name.indexOf('-');
        if (separator < 0) {
            return new GitTag(SemanticVersion.parse(name), null);
        }
        SemanticVersion version = SemanticVersion.parse(name.substring(0, separator));
        return new GitTag(version, Stage.parse(name.substring(separator + 1)));
    }

}
