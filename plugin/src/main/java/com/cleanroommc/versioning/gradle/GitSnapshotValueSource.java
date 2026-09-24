package com.cleanroommc.versioning.gradle;

import com.cleanroommc.versioning.SemanticVersion;
import com.cleanroommc.versioning.Stage;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Reads the Git state the version is computed from.
 */
public abstract class GitSnapshotValueSource implements ValueSource<GitSnapshot, GitSnapshotValueSource.Parameters> {

    /**
     * Inputs for the snapshot.
     */
    public interface Parameters extends ValueSourceParameters {

        /**
         * @return the directory the Git commands run in
         */
        DirectoryProperty getRepositoryDirectory();

        /**
         * @return the tag selected by CI, empty for branch and local builds
         */
        Property<String> getReleaseTag();

    }

    /**
     * @return the injected process runner
     */
    @Inject
    protected abstract ExecOperations getExecOperations();

    @Override
    public GitSnapshot obtain() {
        CommandResult repository = git("rev-parse", "--is-inside-work-tree");
        if (!repository.success() || !"true".equals(repository.output())) {
            throw new GradleException("Cleanroom Versioning requires a Git repository");
        }
        CommandResult head = git("rev-parse", "--verify", "HEAD^{commit}");
        if (!head.success()) {
            throw new GradleException("Cleanroom Versioning requires at least one Git commit");
        }

        boolean dirty = !require("status", "--porcelain", "--untracked-files=normal").isEmpty();
        boolean shallow = Boolean.parseBoolean(require("rev-parse", "--is-shallow-repository"));
        String releaseTagName = getParameters().getReleaseTag().get();
        GitTag releaseTag = null;
        if (!releaseTagName.isEmpty()) {
            try {
                releaseTag = GitTag.parse(releaseTagName);
            } catch (IllegalArgumentException e) {
                throw new GradleException("GitHub tag ref must be numeric SemVer in the form <major>.<minor>.<patch> or v<major>.<minor>.<patch>"
                        + ", optionally followed by -alpha, -beta, -rc or -release (got '" + releaseTagName + "')", e);
            }
            CommandResult commit = git("rev-parse", "--verify", "refs/tags/" + releaseTagName + "^{commit}");
            if (!commit.success() || !commit.output().equals(head.output())) {
                throw new GradleException("GitHub tag '" + releaseTagName + "' must point to HEAD");
            }
        }
        String tagName = releaseTag == null ? null : releaseTagName;
        SemanticVersion latestTag = releaseTag == null ? null : releaseTag.version();
        List<GitTag> staged = new ArrayList<>();

        // Compare parsed numbers so a leading "v" cannot outrank a higher version.

        for (String name : require("tag", "--merged", "HEAD").split("\\R")) {
            GitTag tag;
            try {
                tag = GitTag.parse(name);
            } catch (IllegalArgumentException e) {
                continue;
            }
            SemanticVersion version = tag.version();
            if (releaseTag != null && version.compareTo(releaseTag.version()) > 0) {
                continue;
            }
            if (releaseTag == null && (latestTag == null || version.compareTo(latestTag) > 0
                    || (version.equals(latestTag)
                        && parseCount(require("rev-list", "--count", name + "..HEAD"))
                        < parseCount(require("rev-list", "--count", tagName + "..HEAD"))))) {
                latestTag = version;
                tagName = name;
            }
            if (tag.stage() != null) {
                staged.add(tag);
            }
        }
        Stage stage = releaseTag == null ? null : releaseTag.stage();
        if (stage == null) {
            // Each major version starts over, so a new line never carries the last line's pre-release stage
            SemanticVersion baseline = latestTag;
            stage = staged.stream()
                    .filter(tag -> tag.version().major() == baseline.major())
                    .max(Comparator.comparing(GitTag::version).thenComparing(GitTag::stage))
                    .map(GitTag::stage)
                    .orElse(baseline == null || baseline.major() == 0 ? Stage.BETA : Stage.RELEASE);
        }
        // The whole history rather than the distance from the tag
        long distance = tagName == null
                ? parseCount(require("rev-list", "--count", "HEAD"))
                : parseCount(require("rev-list", "--count", tagName + "..HEAD"));
        boolean pushed = !git("for-each-ref", "--contains", "HEAD", "--count=1", "refs/remotes/").output().isEmpty();

        CommandResult branch = git("symbolic-ref", "--quiet", "--short", "HEAD");
        return new GitSnapshot(latestTag, stage, distance, dirty, pushed, shallow,
                branch.success() ? branch.output() : "");
    }

    private String require(String... arguments) {
        CommandResult result = git(arguments);
        if (!result.success()) {
            throw new GradleException("Git command failed: git " + String.join(" ", arguments)
                    + (result.error().isEmpty() ? "" : "\n" + result.error()));
        }
        return result.output();
    }

    private CommandResult git(String... arguments) {
        List<String> command = new ArrayList<>(arguments.length + 1);
        command.add("git");
        command.addAll(Arrays.asList(arguments));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        ExecResult result;
        try {
            result = getExecOperations().exec(spec -> {
                spec.commandLine(command);
                spec.setWorkingDir(repositoryDirectory());
                spec.setStandardOutput(output);
                spec.setErrorOutput(error);
                spec.setIgnoreExitValue(true);
            });
        } catch (RuntimeException e) {
            throw new GradleException("Cleanroom Versioning requires Git to be installed and available on PATH", e);
        }
        return new CommandResult(
                result.getExitValue(),
                new String(output.toByteArray(), StandardCharsets.UTF_8).trim(),
                new String(error.toByteArray(), StandardCharsets.UTF_8).trim()
        );
    }

    private File repositoryDirectory() {
        return getParameters().getRepositoryDirectory().get().getAsFile();
    }

    private static long parseCount(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new GradleException("Git returned an invalid commit count: '" + value + "'", e);
        }
    }

    private record CommandResult(int exitCode, String output, String error) {

        boolean success() {
            return this.exitCode == 0;
        }

    }

}
