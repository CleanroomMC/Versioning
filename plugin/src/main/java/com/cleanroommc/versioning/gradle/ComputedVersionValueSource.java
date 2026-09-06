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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turns a {@link GitSnapshot} and the build environment into the project version.
 */
public abstract class ComputedVersionValueSource implements ValueSource<String, ComputedVersionValueSource.Parameters> {

    private static final String DEFAULT_LABEL = "dev";

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
         * @return the pre-release label, unset when it is derived from the branch
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

        String branch = tagBuild ? "" : branch(snapshot, githubActions, parameters.getGithubRefName().get(),
                parameters.getGithubHeadRef().get());
        SemanticVersion target = target(branch, developmentPrefix);
        boolean ownLine = target != null || branch.isEmpty() || branch.equals(releaseBranch);
        if (!ownLine) {
            target = inheritedTarget(developmentPrefix, releaseBranch, snapshot.latestTag());
        }
        String label = parameters.getLabel().getOrElse("");
        if (label.isEmpty()) {
            label = ownLine ? DEFAULT_LABEL : label(branch);
        }
        return Versioning.compute(snapshot.gitState(target, githubActions), label, parameters.getMetadata().get());
    }

    // The head ref is the source branch of a pull request
    private static String branch(GitSnapshot snapshot, boolean githubActions, String refName, String headRef) {
        return githubActions ? (headRef.isEmpty() ? refName : headRef) : snapshot.currentBranch();
    }

    private static SemanticVersion target(String branch, String developmentPrefix) {
        String prefix = developmentPrefix + "/";
        return branch.startsWith(prefix) ? SemanticVersion.parseTarget(branch.substring(prefix.length())) : null;
    }

    private SemanticVersion inheritedTarget(String developmentPrefix, String releaseBranch, SemanticVersion baseline) {
        SemanticVersion target = null;
        // Ties belong to the release line, so a development branch has to be strictly closer to win
        long closest = distance("origin/" + releaseBranch);
        String prefix = "origin/" + developmentPrefix + "/";
        // The pattern has no trailing slash: git matches a literal ref pattern whole or up to a slash
        String[] references = gitOutput("for-each-ref", "--format=%(refname:short)",
                "refs/remotes/origin/" + developmentPrefix).split("\\R");
        for (String reference : references) {
            if (!reference.startsWith(prefix)) {
                continue;
            }
            SemanticVersion candidate;
            try {
                candidate = SemanticVersion.parseTarget(reference.substring(prefix.length()));
            } catch (RuntimeException e) {
                // A sibling branch nobody can build is not this build's problem
                continue;
            }
            long distance = distance(reference);
            if (distance < closest || (distance == closest && target != null && candidate.compareTo(target) > 0)) {
                closest = distance;
                target = candidate;
            }
        }
        // An inherited pin is a guess, so a stale one is dropped
        return target != null && baseline != null && target.compareTo(baseline) <= 0 ? null : target;
    }

    private long distance(String reference) {
        String count = gitOutput("rev-list", "--count", reference + "..HEAD");
        try {
            return Long.parseLong(count);
        } catch (NumberFormatException e) {
            // A missing ref is not a candidate
            return Long.MAX_VALUE;
        }
    }

    private static String label(String branch) {
        String label = branch.replaceAll("[^0-9A-Za-z-]+", "-").replaceAll("^-+|-+$", "");
        // A label may not be numeric, and a branch named for a number alone carries nothing worth keeping
        return label.isEmpty() || label.chars().allMatch(Character::isDigit) ? DEFAULT_LABEL : label;
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
        int exitCode = git(OutputStream.nullOutputStream(), "merge-base", "--is-ancestor", "HEAD",
                "refs/remotes/origin/" + releaseBranch);
        return switch (exitCode) {
            case 0 -> ReleaseLine.CONTAINS_HEAD;
            case 1 -> ReleaseLine.ELSEWHERE;
            // Anything else is a missing ref, so the repository does not use the branch and there is nothing to check
            default -> ReleaseLine.ABSENT;
        };
    }

    private String gitOutput(String... arguments) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        return git(output, arguments) == 0 ? output.toString(StandardCharsets.UTF_8).trim() : "";
    }

    private int git(OutputStream output, String... arguments) {
        File directory = getParameters().getRepositoryDirectory().get().getAsFile();
        List<String> command = new ArrayList<>(arguments.length + 1);
        command.add("git");
        command.addAll(Arrays.asList(arguments));
        return getExecOperations().exec(spec -> {
            spec.commandLine(command);
            spec.setWorkingDir(directory);
            spec.setStandardOutput(output);
            spec.setErrorOutput(OutputStream.nullOutputStream());
            spec.setIgnoreExitValue(true);
        }).getExitValue();
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
