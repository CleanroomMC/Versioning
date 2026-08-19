package com.cleanroommc.versioning;

import java.util.Objects;

/**
 * Inputs used to compute a project version.
 */
public final class VersionRequest {

    private final String numericVersion;
    private final Stage stage;
    private final GitDescribe git;
    private final boolean publish;
    private final String run;

    /**
     * @param numericVersion declared numeric version, e.g. {@code 0.6.10}
     * @param stage          release stage
     * @param git            parsed {@code git describe} output
     * @param publish        {@code true} to emit the base version and validate tag and distance
     * @param run            CI run identifier, or {@code null} for a local build
     */
    public VersionRequest(String numericVersion, Stage stage, GitDescribe git, boolean publish, String run) {
        if (numericVersion == null || numericVersion.trim().isEmpty() || "unspecified".equals(numericVersion)) {
            throw new VersioningException("version is required");
        }
        if (stage == null) {
            throw new VersioningException("stage is required");
        }
        this.numericVersion = numericVersion;
        this.stage = stage;
        this.git = git == null ? GitDescribe.missing() : git;
        this.publish = publish;
        this.run = run;
    }

    public String numericVersion() {
        return this.numericVersion;
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

    public String run() {
        return this.run;
    }

    /**
     * Version without build metadata, and the git tag required to publish: {@code 0.6.10}.
     */
    public String baseVersion() {
        return this.numericVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        VersionRequest that = (VersionRequest) o;
        return this.publish == that.publish &&
                this.numericVersion.equals(that.numericVersion) &&
                this.stage == that.stage &&
                this.git.equals(that.git) &&
                Objects.equals(this.run, that.run);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.numericVersion, this.stage, this.git, this.publish, this.run);
    }

    @Override
    public String toString() {
        return "VersionRequest[numericVersion=" + this.numericVersion
                + ", stage=" + this.stage
                + ", git=" + this.git
                + ", publish=" + this.publish
                + ", run=" + this.run
                + "]";
    }

}
