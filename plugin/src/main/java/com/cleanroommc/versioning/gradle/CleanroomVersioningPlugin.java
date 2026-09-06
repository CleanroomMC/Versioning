package com.cleanroommc.versioning.gradle;

import com.cleanroommc.versioning.SemanticVersion;
import com.cleanroommc.versioning.Stage;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sets {@code project.version} from Git tags, branch policy, and the build environment.
 */
public abstract class CleanroomVersioningPlugin implements Plugin<Project> {

    /**
     * The plugin identifier.
     */
    public static final String PLUGIN_ID = "com.cleanroommc.versioning";

    /**
     * The name of the {@link VersioningExtension}.
     */
    public static final String EXTENSION_NAME = "versioning";

    /**
     * The name of the task that prints the computed version.
     */
    public static final String PRINT_VERSION_TASK = "printVersion";

    @Override
    public void apply(Project project) {
        if (!"unspecified".equals(project.getVersion().toString())) {
            throw new GradleException("Cleanroom Versioning v3 derives project.version from Git, remove the declared 'version' property");
        }

        ProviderFactory providers = project.getProviders();
        VersioningExtension extension = project.getExtensions().create(EXTENSION_NAME, VersioningExtension.class);
        // java-gradle-plugin reads project.version while the plugins block is still running
        // which freezes the configuration before a build script can set it
        extension.getReleaseBranch().convention(providers.gradleProperty("versioning.releaseBranch").orElse("master"));
        extension.getDevelopmentPrefix().convention(providers.gradleProperty("versioning.developmentPrefix").orElse("develop"));
        // No default: an unset label is derived from the branch by the value source
        extension.getLabel().convention(providers.gradleProperty("versioning.label"));
        extension.getReleaseBranch().finalizeValueOnRead();
        extension.getDevelopmentPrefix().finalizeValueOnRead();
        extension.getLabel().finalizeValueOnRead();

        Provider<GitSnapshot> snapshot = project.getGradle().getSharedServices()
                .registerIfAbsent(GitSnapshotService.NAME, GitSnapshotService.class)
                .get()
                .snapshot(project.getProjectDir(), () -> providers.of(GitSnapshotValueSource.class, spec -> {
                    var parameters = spec.getParameters();
                    parameters.getRepositoryDirectory().set(project.getLayout().getProjectDirectory());
                }));

        // Merged rather than left to a convention
        Provider<Map<String, String>> metadata = providers.environmentVariable("GITHUB_RUN_NUMBER")
                .map(run -> Map.of("run", run))
                .orElse(Map.of())
                .zip(extension.getMetadata(), (run, configured) -> {
                    Map<String, String> merged = new LinkedHashMap<>(run);
                    merged.putAll(configured);
                    return merged;
                });

        extension.getStage().convention(providers.gradleProperty("versioning.stage").map(Stage::parse)
                .orElse(snapshot.map(state -> defaultStage(state.latestTag()))));
        extension.getStage().finalizeValueOnRead();

        extension.getVersion().set(providers.of(ComputedVersionValueSource.class, spec -> {
            var parameters = spec.getParameters();
            parameters.getGitSnapshot().set(snapshot);
            parameters.getRepositoryDirectory().set(project.getLayout().getProjectDirectory());
            parameters.getReleaseBranch().set(extension.getReleaseBranch());
            parameters.getDevelopmentPrefix().set(extension.getDevelopmentPrefix());
            parameters.getLabel().set(extension.getLabel());
            parameters.getMetadata().set(metadata);
            parameters.getStage().set(extension.getStage());
            parameters.getGithubActions().set(providers.environmentVariable("GITHUB_ACTIONS").orElse(""));
            parameters.getGithubRefType().set(providers.environmentVariable("GITHUB_REF_TYPE").orElse(""));
            parameters.getGithubRefName().set(providers.environmentVariable("GITHUB_REF_NAME").orElse(""));
            parameters.getGithubHeadRef().set(providers.environmentVariable("GITHUB_HEAD_REF").orElse(""));
        }));
        extension.getVersion().finalizeValueOnRead();
        extension.getVersion().disallowChanges();

        project.setVersion(extension);
        project.afterEvaluate(target -> {
            if (target.getVersion() != extension) {
                throw new GradleException("Cleanroom Versioning v3 derives project.version from Git, remove the declared 'version' property");
            }
        });
        project.getTasks().register(PRINT_VERSION_TASK, PrintVersionTask.class, task -> {
            task.setGroup("help");
            task.setDescription("Prints the computed project version.");
            task.getVersion().set(extension.getVersion());
        });
    }

    private static Stage defaultStage(SemanticVersion latestTag) {
        return latestTag == null || latestTag.major() == 0 ? Stage.BETA : Stage.RELEASE;
    }

}
