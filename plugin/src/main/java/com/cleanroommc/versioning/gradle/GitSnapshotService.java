package com.cleanroommc.versioning.gradle;

import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Holds one Git snapshot provider per repository, shared by every project that applies the plugin.
 * Build services are the only cross-project state Gradle allows once isolated projects is on.
 */
public abstract class GitSnapshotService implements BuildService<BuildServiceParameters.None> {

    static final String NAME = "cleanroomVersioningGitSnapshot";

    private final Map<String, Provider<GitSnapshot>> snapshots = new ConcurrentHashMap<>();

    Provider<GitSnapshot> snapshot(File projectDirectory, Supplier<Provider<GitSnapshot>> factory) {
        return this.snapshots.computeIfAbsent(repositoryRoot(projectDirectory), key -> factory.get());
    }

    /**
     * Keyed by repository rather than shared across the whole build:
     *
     * <p>
     * A submodule pulled in as a subproject has its own tags
     * and one provider for the build would hand it whichever repository happened to register first.
     * Git resolves the nearest ancestor holding a .git entry,
     * and a submodule carries one as a file, so this parts them the same way.
     */
    private static String repositoryRoot(File projectDirectory) {
        for (File directory = projectDirectory; directory != null; directory = directory.getParentFile()) {
            if (new File(directory, ".git").exists()) {
                return directory.getAbsolutePath();
            }
        }
        return projectDirectory.getAbsolutePath();
    }

}
