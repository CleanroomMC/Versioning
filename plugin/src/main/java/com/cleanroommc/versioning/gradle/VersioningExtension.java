package com.cleanroommc.versioning.gradle;

import com.cleanroommc.versioning.ComputedVersion;
import com.cleanroommc.versioning.GitDescribe;

import javax.inject.Inject;

/**
 * Read-only view of the version computed when the plugin was applied.
 */
public abstract class VersioningExtension {

    private final ComputedVersion computed;

    @Inject
    public VersioningExtension(ComputedVersion computed) {
        this.computed = computed;
    }

    public ComputedVersion getComputed() {
        return this.computed;
    }

    public String getVersion() {
        return this.computed.version();
    }

    public String getBaseVersion() {
        return this.computed.baseVersion();
    }

    public String getStage() {
        return this.computed.stage().id();
    }

    public GitDescribe getGit() {
        return this.computed.git();
    }

    public String getTag() {
        return this.computed.git().tag();
    }

    public int getDistance() {
        return this.computed.git().distance();
    }

    public boolean isPublish() {
        return this.computed.publish();
    }

}
