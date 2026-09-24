package com.cleanroommc.versioning.gradle;

import com.cleanroommc.versioning.Stage;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Configures the version label and exposes the computed version.
 */
public abstract class VersioningExtension {

    static final String STAGE_DEPRECATION = "Setting versioning.stage from Gradle is deprecated and will be removed in the next major version."
            + " Put the stage on the Git tag instead, such as 1.2.0-beta";

    private final Property<Stage> stage;

    /**
     * @param objects the injected object factory
     */
    @Inject
    public VersioningExtension(ObjectFactory objects) {
        this.stage = objects.property(Stage.class);
    }

    /**
     * The branch a release tag has to be reachable from, {@code master} by default.
     *
     * @return the release branch name
     */
    public abstract Property<String> getReleaseBranch();

    /**
     * The path prefix marking a development branch, {@code develop} by default. A branch named
     * {@code <prefix>/<major>.<minor>} stays on that version and only advances the label.
     *
     * @return the development branch prefix
     */
    public abstract Property<String> getDevelopmentPrefix();

    /**
     * The pre-release label commits are counted under. Unset it is {@code dev} on the release branch and on a
     * development branch, and the branch name elsewhere, so {@code feature/foo} counts under {@code feature-foo}.
     *
     * @return the label
     */
    public abstract Property<String> getLabel();

    /**
     * Additional pre-release identifiers, rendered as {@code .<key>.<value>} after the label in insertion order.
     * Under GitHub Actions the run number is emitted as {@code run} ahead of these.
     *
     * @return the metadata
     */
    public abstract MapProperty<String, String> getMetadata();

    /**
     * The release stage, read from Git tags. A CI tag build uses its explicit suffix or inherits from the highest
     * reachable staged version at or below its version. Other builds use the highest reachable staged version.
     * Only staged versions in the same major version count. Without one it is {@link Stage#BETA} below
     * {@code 1.0.0} and {@link Stage#RELEASE} from there on. Setting it, or the {@code versioning.stage} Gradle
     * property, still overrides Git but is deprecated.
     *
     * @return the stage
     */
    public Property<Stage> getStage() {
        return this.stage;
    }

    /**
     * @param value the stage
     * @deprecated put the stage on the Git tag instead
     */
    @Deprecated(since = "3.3.0", forRemoval = true)
    public void setStage(Stage value) {
        Logging.getLogger(VersioningExtension.class).warn(STAGE_DEPRECATION);
        this.stage.set(value);
    }

    /**
     * Sets the stage from its name, case insensitively.
     *
     * @param value the stage name
     * @throws IllegalArgumentException if the text names no stage
     * @deprecated put the stage on the Git tag instead
     */
    @Deprecated(since = "3.3.0", forRemoval = true)
    public void setStage(String value) {
        Logging.getLogger(VersioningExtension.class).warn(STAGE_DEPRECATION);
        this.stage.set(Stage.parse(value));
    }

    /**
     * The version assigned to {@code project.version}. The plugin wires and locks this.
     *
     * @return the computed version
     */
    public abstract Property<String> getVersion();

    @Override
    public String toString() {
        return getVersion().get();
    }

}
