package com.cleanroommc.versioning.gradle;

import com.cleanroommc.versioning.GitState;
import com.cleanroommc.versioning.SemanticVersion;
import com.cleanroommc.versioning.Stage;

import java.io.Serializable;

// Projects in a repository share this snapshot, including the tag selected by CI.
// Project settings such as the release branch are resolved separately.
record GitSnapshot(SemanticVersion latestTag, Stage stage, long distance, boolean dirty, boolean pushed, boolean shallow,
                   String currentBranch) implements Serializable {

    GitState gitState(SemanticVersion target, boolean forcePushed) {
        return new GitState(this.latestTag, target, this.distance, this.dirty, this.pushed || forcePushed);
    }

}
