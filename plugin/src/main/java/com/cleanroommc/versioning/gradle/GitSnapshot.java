package com.cleanroommc.versioning.gradle;

import com.cleanroommc.versioning.GitState;
import com.cleanroommc.versioning.SemanticVersion;

import java.io.Serializable;

// Repository state only. Anything a project configures for itself, the release branch above all, is resolved by the
// project rather than stored here, because one snapshot is shared by every project in the repository.
record GitSnapshot(SemanticVersion latestTag, long commits, boolean exactTag, boolean dirty, boolean pushed,
                   boolean shallow, String currentBranch) implements Serializable {

    GitState gitState(SemanticVersion target, boolean forcePushed) {
        return new GitState(this.latestTag, target, this.commits, this.exactTag, this.dirty,
                this.pushed || forcePushed);
    }

}
