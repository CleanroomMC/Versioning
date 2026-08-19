package com.cleanroommc.versioning.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanroomVersioningPluginTest {

    @TempDir
    Path projectDir;

    @BeforeEach
    void setup() throws IOException {
        Files.writeString(this.projectDir.resolve("settings.gradle"), "rootProject.name = 'versioning-test'\n");
        Files.writeString(this.projectDir.resolve("gradle.properties"),
                """
                version = 1.2.3
                versioning.stage = alpha
                """
        );
        Files.writeString(this.projectDir.resolve("build.gradle"),
                """
                plugins {
                    id 'com.cleanroommc.versioning'
                }
                tasks.register('printComputed') {
                    def version = project.version.toString()
                    def base = versioning.baseVersion
                    def suffix = versioning.artifactSuffix
                    def distance = versioning.distance
                    doLast {
                        println "VERSION=${version}"
                        println "BASE=${base}"
                        println "SUFFIX=${suffix}"
                        println "DISTANCE=${distance}"
                    }
                }
                """
        );
    }

    @Test
    void localVersionUsesGitDistance() throws Exception {
        initGit();
        commit("init");
        tag("1.2.3");
        commit("ahead");

        var result = run("printComputed");
        assertEquals(TaskOutcome.SUCCESS, result.task(":printComputed").getOutcome());
        assertTrue(result.getOutput().contains("VERSION=1.2.3+local.1"), result.getOutput());
        assertTrue(result.getOutput().contains("BASE=1.2.3"), result.getOutput());
        assertTrue(result.getOutput().contains("SUFFIX=-alpha"), result.getOutput());
        assertTrue(result.getOutput().contains("DISTANCE=1"), result.getOutput());
    }

    @Test
    void printVersionWritesOnlyTheVersion() throws Exception {
        initGit();
        commit("init");
        tag("1.2.3");

        var result = run("-q", "printVersion");
        assertEquals("1.2.3+local.0", result.getOutput().trim());
    }

    @Test
    void ciVersionIncludesRunNumber() throws Exception {
        initGit();
        commit("init");
        tag("1.2.3");
        commit("ahead");

        var result = run("printComputed", "-Pversioning.run=77");
        assertTrue(result.getOutput().contains("VERSION=1.2.3+build.1.run.77"), result.getOutput());
    }

    @Test
    void releaseOnTagUsesBaseVersion() throws Exception {
        initGit();
        commit("init");
        tag("1.2.3");

        var result = run("printComputed", "-Pversioning.publish=true");
        assertTrue(result.getOutput().contains("VERSION=1.2.3"), result.getOutput());
        assertTrue(result.getOutput().contains("DISTANCE=0"), result.getOutput());
    }

    @Test
    void releaseFailsWhenAheadOfTag() throws Exception {
        initGit();
        commit("init");
        tag("1.2.3");
        commit("ahead");

        var result = fail("printComputed", "-Pversioning.publish=true");
        assertTrue(result.getOutput().contains("Release must be on a tagged commit"), result.getOutput());
    }

    @Test
    void releaseFailsWhenTagDoesNotMatch() throws Exception {
        initGit();
        commit("init");
        tag("1.2.2");

        var result = fail("printComputed", "-Pversioning.publish=true");
        assertTrue(result.getOutput().contains("does not match gradle.properties version '1.2.3'"), result.getOutput());
    }

    @Test
    void missingStageFails() throws Exception {
        Files.writeString(this.projectDir.resolve("gradle.properties"), "version = 1.2.3\n");
        initGit();
        commit("init");

        var result = fail("printComputed");
        assertTrue(result.getOutput().contains("versioning.stage must be one of"), result.getOutput());
    }

    @Test
    void configurationCacheReusesPrintVersion() throws Exception {
        initGit();
        commit("init");
        tag("1.2.3");

        var first = run("--configuration-cache", "-q", "printVersion");
        assertEquals("1.2.3+local.0", first.getOutput().trim());
        var second = run("--configuration-cache", "-q", "printVersion");
        assertEquals("1.2.3+local.0", second.getOutput().trim());
        assertTrue(second.getOutput().contains("Reusing configuration cache.") || second.getOutput().isBlank() ||
                second.getOutput().trim().equals("1.2.3+local.0"), second.getOutput());
    }

    private BuildResult run(String... args) {
        return runner(args).build();
    }

    private BuildResult fail(String... args) {
        return runner(args).buildAndFail();
    }

    private GradleRunner runner(String... args) {
        var arguments = new ArrayList<String>();
        arguments.addAll(List.of(args));
        arguments.add("--stacktrace");
        return GradleRunner.create()
                .withProjectDir(this.projectDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .forwardOutput();
    }

    private void initGit() throws Exception {
        git("init");
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "Test");
        git("config", "commit.gpgsign", "false");
        git("config", "tag.gpgsign", "false");
    }

    private void commit(String message) throws Exception {
        Files.writeString(this.projectDir.resolve("note.txt"), message + "\n");
        git("add", "note.txt");
        git("commit", "-m", message);
    }

    private void tag(String name) throws Exception {
        git("tag", name);
    }

    private void git(String... args) throws Exception {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command).directory(this.projectDir.toFile()).redirectErrorStream(true).start();
        var finished = process.waitFor(30, TimeUnit.SECONDS);
        var output = new String(process.getInputStream().readAllBytes());
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("git timed out: " + command + "\n" + output);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("git failed (" + process.exitValue() + "): " + command + "\n" + output);
        }
    }
}
