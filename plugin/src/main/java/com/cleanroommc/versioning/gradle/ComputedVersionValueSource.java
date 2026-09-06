package com.cleanroommc.versioning.gradle;

import com.cleanroommc.versioning.SemanticVersion;
import com.cleanroommc.versioning.Stage;
import com.cleanroommc.versioning.Versioning;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.OutputStream;

/**
 * Turns a {@link GitSnapshot} and the build environment into the project version.
 */
public abstract class ComputedVersionValueSource implements ValueSource<String, ComputedVersionValueSource.Parameters> {

    /**
     * Inputs for the computed version.
     */
    public interface Parameters extends ValueSourceParameters {

        /**
         * @return the Git state
         */
        Property<GitSnapshot> getGitSnapshot();

        /**
         * @return the directory the release branch is resolved in
         */
        DirectoryProperty getRepositoryDirectory();

        /**
         * @return the release branch name
         */
        Property<String> getReleaseBranch();

        /**
         * @return the development branch prefix
         */
        Property<String> getDevelopmentPrefix();

        /**
         * @return the pre-release label
         */
        Property<String> getLabel();

        /**
         * @return the additional pre-release identifiers
         */
        MapProperty<String, String> getMetadata();

        /**
         * @return the release stage, resolved here because every way of setting it converges on this value
         */
        Property<Stage> getStage();

        /**
         * @return the {@code GITHUB_ACTIONS} environment variable
         */
        Property<String> getGithubActions();

        /**
         * @return the {@code GITHUB_REF_TYPE} environment variable
         */
        Property<String> getGithubRefType();

        /**
         * @return the {@code GITHUB_REF_NAME} environment variable
         */
        Property<String> getGithubRefName();

        /**
         * @return the {@code GITHUB_HEAD_REF} environment variable
         */
        Property<String> getGithubHeadRef();

    }

    /**
     * @return the injected process runner
     */
    @Inject
    protected abstract ExecOperations getExecOperations();

    @Override
    public String obtain() {
        Parameters parameters = getParameters();
        GitSnapshot snapshot = parameters.getGitSnapshot().get();
        String releaseBranch = requireBranch("releaseBranch", parameters.getReleaseBranch().get());
        String developmentPrefix = requireBranch("developmentPrefix", parameters.getDevelopmentPrefix().get());
        parameters.getStage().get();
        boolean githubActions = "true".equalsIgnoreCase(parameters.getGithubActions().get());
        if (githubActions && snapshot.shallow()) {
            throw new GradleException("Cleanroom Versioning requires full Git history in CI, checkout with fetch-depth: 0 and fetch-tags: true");
        }

        boolean tagBuild = githubActions && "tag".equals(parameters.getGithubRefType().get());
        if (tagBuild) {
            validateRelease(snapshot, releaseBranch, parameters.getGithubRefName().get());
        }

        SemanticVersion target = tagBuild ? null : target(snapshot, githubActions, developmentPrefix,
                parameters.getGithubRefName().get(), parameters.getGithubHeadRef().get());
        return Versioning.compute(snapshot.gitState(target, githubActions), parameters.getLabel().get(),
                parameters.getMetadata().get());
    }

    // The head ref is the source branch of a pull request
    private static SemanticVersion target(GitSnapshot snapshot, boolean githubActions, String developmentPrefix,
                                          String refName, String headRef) {
        String branch = githubActions ? (headRef.isEmpty() ? refName : headRef) : snapshot.currentBranch();
        String prefix = developmentPrefix + "/";
        return branch.startsWith(prefix) ? SemanticVersion.parseTarget(branch.substring(prefix.length())) : null;
    }

    private void validateRelease(GitSnapshot snapshot, String releaseBranch, String refName) {
        SemanticVersion tag;
        try {
            tag = SemanticVersion.parse(refName);
        } catch (RuntimeException e) {
            throw new GradleException("GitHub tag ref must be numeric SemVer in the form <major>.<minor>.<patch> or v<major>.<minor>.<patch> (got '"
                    + refName + "')", e);
        }
        if (!tag.equals(snapshot.latestTag()) || !snapshot.exactTag()) {
            throw new GradleException("GitHub tag '" + refName + "' must be the highest reachable numeric tag at HEAD");
        }
        if (snapshot.dirty()) {
            throw new GradleException("Release build requires a clean Git worktree");
        }
        // Asked for here rather than carried on the snapshot: the snapshot is shared by every project in the
        // repository, and the release branch is a per-project setting. Only a tag build ever needs the answer.
        if (releaseLine(releaseBranch) == ReleaseLine.ELSEWHERE) {
            throw new GradleException("Release tag '" + refName + "' is not reachable from origin/" + releaseBranch);
        }
    }

    private ReleaseLine releaseLine(String releaseBranch) {
        File directory = getParameters().getRepositoryDirectory().get().getAsFile();
        int exitCode = getExecOperations().exec(spec -> {
            spec.commandLine("git", "merge-base", "--is-ancestor", "HEAD", "refs/remotes/origin/" + releaseBranch);
            spec.setWorkingDir(directory);
            spec.setStandardOutput(OutputStream.nullOutputStream());
            spec.setErrorOutput(OutputStream.nullOutputStream());
            spec.setIgnoreExitValue(true);
        }).getExitValue();
        return switch (exitCode) {
            case 0 -> ReleaseLine.CONTAINS_HEAD;
            case 1 -> ReleaseLine.ELSEWHERE;
            // Anything else is a missing ref, so the repository does not use the branch and there is nothing to check
            default -> ReleaseLine.ABSENT;
        };
    }

    private enum ReleaseLine {

        CONTAINS_HEAD,
        ELSEWHERE,
        ABSENT

    }

    private static String requireBranch(String name, String branch) {
        if (branch == null || branch.trim().isEmpty()) {
            throw new GradleException("versioning." + name + " must not be empty");
        }
        return branch.trim();
    }

}
