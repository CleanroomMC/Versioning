package com.cleanroommc.versioning.gradle;

import com.cleanroommc.versioning.SemanticVersion;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
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
        if (!git("rev-parse", "--verify", "HEAD^{commit}").success()) {
            throw new GradleException("Cleanroom Versioning requires at least one Git commit");
        }

        boolean dirty = !require("status", "--porcelain", "--untracked-files=normal").isEmpty();
        boolean shallow = Boolean.parseBoolean(require("rev-parse", "--is-shallow-repository"));
        // The raw tag name, not the parsed version, because only the raw name is a resolvable Git ref
        String tagName = latestTagName();
        SemanticVersion latestTag = tagName == null ? null : SemanticVersion.parse(tagName);
        // The whole history rather than the distance from the tag
        long distance = tagName == null
                ? parseCount(require("rev-list", "--count", "HEAD"))
                : parseCount(require("rev-list", "--count", tagName + "..HEAD"));
        boolean pushed = !git("for-each-ref", "--contains", "HEAD", "--count=1", "refs/remotes/").output().isEmpty();

        CommandResult branch = git("symbolic-ref", "--quiet", "--short", "HEAD");
        return new GitSnapshot(latestTag, distance, dirty, pushed, shallow,
                branch.success() ? branch.output() : "");
    }

    // Not git's own --sort=-v:refname: it compares refnames
    // A leading "v" outranks a digit and v1.2.3 would beat
    // 2.0.0 in a repository that mixes both tag forms
    private String latestTagName() {
        String name = null;
        SemanticVersion highest = null;
        for (String tag : require("tag", "--merged", "HEAD").split("\\R")) {
            if (!SemanticVersion.isValid(tag)) {
                continue;
            }
            SemanticVersion candidate = SemanticVersion.parse(tag);
            if (highest == null || candidate.compareTo(highest) > 0) {
                highest = candidate;
                name = tag;
            }
        }
        return name;
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
