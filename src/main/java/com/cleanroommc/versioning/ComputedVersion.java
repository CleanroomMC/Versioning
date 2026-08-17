package com.cleanroommc.versioning;

import java.util.Objects;

/**
 * Result of {@link Versioning#compute(VersionRequest)}.
 */
public final class ComputedVersion {

    private final String version;
    private final String baseVersion;
    private final Stage stage;
    private final GitDescribe git;
    private final boolean publish;

    /**
     * @param version     full project version, including metadata
     * @param baseVersion version without build metadata
     * @param stage       stage used to produce {@link #baseVersion()}
     * @param git         describe data that contributed the distance
     * @param publish     {@code true} when this version was computed for publishing
     */
    public ComputedVersion(String version, String baseVersion, Stage stage, GitDescribe git, boolean publish) {
        this.version = version;
        this.baseVersion = baseVersion;
        this.stage = stage;
        this.git = git;
        this.publish = publish;
    }

    public String version() {
        return this.version;
    }

    public String baseVersion() {
        return this.baseVersion;
    }

    public Stage stage() {
        return this.stage;
    }

    public GitDescribe git() {
        return this.git;
    }

    public boolean publish() {
        return this.publish;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ComputedVersion that = (ComputedVersion) o;
        return this.publish == that.publish
                && Objects.equals(this.version, that.version)
                && Objects.equals(this.baseVersion, that.baseVersion)
                && this.stage == that.stage
                && Objects.equals(this.git, that.git);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.version, this.baseVersion, this.stage, this.git, this.publish);
    }

    @Override
    public String toString() {
        return this.version;
    }

}
