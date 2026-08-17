package com.cleanroommc.versioning.gradle;

import com.cleanroommc.versioning.ComputedVersion;
import com.cleanroommc.versioning.Versioning;
import com.cleanroommc.versioning.VersioningException;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Sets {@code project.version} from {@code version}, {@code versioning.stage},
 * {@code git describe}, and optional {@code versioning.publish}/{@code versioning.run} properties.
 */
public abstract class CleanroomVersioningPlugin implements Plugin<Project> {

    public static final String PLUGIN_ID = "com.cleanroommc.versioning";
    public static final String EXTENSION_NAME = "versioning";
    public static final String PRINT_VERSION_TASK = "printVersion";

    @Override
    public void apply(Project project) {
        ComputedVersion computed = compute(project);
        project.setVersion(computed.version());
        if (project.getExtensions().findByName(EXTENSION_NAME) == null) {
            project.getExtensions().create(EXTENSION_NAME, VersioningExtension.class, computed);
        }
        if (project.getTasks().findByName(PRINT_VERSION_TASK) == null) {
            var printed = computed.version();
            project.getTasks().register(PRINT_VERSION_TASK, task -> {
                task.setGroup("help");
                task.setDescription("Prints the computed project version.");
                task.doLast(t -> System.out.println(printed));
            });
        }
    }

    public static ComputedVersion compute(Project project) {
        var providers = project.getProviders();
        var stage = providers.gradleProperty("versioning.stage");
        if (!stage.isPresent()) {
            throw new GradleException("versioning.stage must be one of: [alpha, beta, rc, release] (got 'null')");
        }
        var gitInfo = providers.exec(spec -> {
            spec.commandLine("git", "describe", "--tags", "--long", "--always");
            spec.setWorkingDir(project.getLayout().getProjectDirectory());
            spec.setIgnoreExitValue(true);
        }).getStandardOutput().getAsText();
        try {
            return Versioning.compute(
                    project.getVersion().toString(),
                    stage.get(),
                    gitInfo.get(),
                    providers.gradleProperty("versioning.publish").isPresent(),
                    providers.gradleProperty("versioning.run").getOrNull()
            );
        } catch (VersioningException e) {
            throw new GradleException(e.getMessage(), e);
        }
    }
}
