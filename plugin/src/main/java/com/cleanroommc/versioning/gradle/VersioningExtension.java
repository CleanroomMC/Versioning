package com.cleanroommc.versioning.gradle;

import com.cleanroommc.versioning.Stage;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Configures the version label and exposes the computed version.
 */
public abstract class VersioningExtension {

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
     * The pre-release label commits are counted under, {@code dev} by default.
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
     * The release stage. Unset, it comes from the {@code versioning.stage} Gradle property, and failing that it is
     * {@link Stage#BETA} while the version line is below {@code 1.0.0} and {@link Stage#RELEASE} from there on.
     *
     * @return the stage
     */
    public Property<Stage> getStage() {
        return this.stage;
    }

    /**
     * @param value the stage
     */
    public void setStage(Stage value) {
        this.stage.set(value);
    }

    /**
     * Sets the stage from its name, case insensitively.
     *
     * @param value the stage name
     * @throws IllegalArgumentException if the text names no stage
     */
    public void setStage(String value) {
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
