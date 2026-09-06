package com.cleanroommc.versioning.gradle;

import org.gradle.api.GradleException;
import org.gradle.process.ExecOperations;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanroomVersioningPluginTest {

    private static final List<String> GITHUB_ENVIRONMENT = List.of(
            "GITHUB_ACTIONS", "GITHUB_REF_TYPE", "GITHUB_REF_NAME", "GITHUB_HEAD_REF", "GITHUB_RUN_NUMBER"
    );

    private static final String PRINT_STAGE = """
            tasks.register('printStage') {
                def stage = versioning.stage
                doLast {
                    logger.quiet(stage.get().id())
                }
            }
            """;

    @TempDir
    Path tempDir;

    Path projectDir;

    @BeforeEach
    void setup() throws IOException {
        this.projectDir = Files.createDirectory(this.tempDir.resolve("project"));
        Files.writeString(this.projectDir.resolve("settings.gradle"), "rootProject.name = 'versioning-test'\n");
        Files.writeString(this.projectDir.resolve("gradle.properties"), "versioning.stage = release\n");
        Files.writeString(this.projectDir.resolve(".gitignore"), ".gradle/\nbuild/\n");
        writeBuild("");
    }

    @Test
    void onTagKeepsTaggedVersionWithoutLocalMarker() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");

        assertVersion(run("-q", "printVersion"), "1.2.3");
    }

    @Test
    void masterLeadsToTheNextPatch() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");
        commit("ahead");

        assertVersion(run("-q", "printVersion"), "1.2.4-dev.2.local");
    }

    // The whole line between two tags stays on one number; only the label counter moves.
    @Test
    void theNumberDoesNotMoveWithCommitCount() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");
        var versions = new ArrayList<String>();
        for (int index = 1; index <= 3; index++) {
            commit("ahead " + index);
            versions.add(versionLine(run("-q", "printVersion")));
        }

        assertEquals(List.of("1.2.4-dev.2.local", "1.2.4-dev.3.local", "1.2.4-dev.4.local"), versions);
    }

    @Test
    void developmentBranchPinsItsTarget() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");
        git("switch", "-c", "develop/1.4");
        commit("development");

        assertVersion(run("-q", "printVersion"), "1.4.0-dev.2.local");

        commit("more development");
        assertVersion(run("-q", "printVersion"), "1.4.0-dev.3.local");
    }

    // Long horizon work and the next minor run side by side, each pinned to its own number.
    @Test
    void developmentBranchesRunInParallel() throws Exception {
        initGit();
        commit("initial");
        tag("1.3.2");
        git("switch", "-c", "develop/1.4");
        commit("minor work");
        assertVersion(run("-q", "printVersion"), "1.4.0-dev.2.local");

        git("switch", "master");
        git("switch", "-c", "develop/2.0");
        commit("major work");
        assertVersion(run("-q", "printVersion"), "2.0.0-dev.2.local");
    }

    @Test
    void developmentBranchAcceptsAnExplicitZeroPatch() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");
        git("switch", "-c", "develop/v1.4.0");
        commit("development");

        assertVersion(run("-q", "printVersion"), "1.4.0-dev.2.local");
    }

    @Test
    void developmentBranchRejectsAPatchTarget() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");
        git("switch", "-c", "develop/1.4.3");
        commit("development");

        var result = fail("printVersion");
        assertTrue(result.getOutput().contains("patch versions are cut on the release branch"), result.getOutput());
    }

    // After the release is tagged and merged back, the branch would compute below its own release.
    @Test
    void developmentBranchRejectsATargetThatHasShipped() throws Exception {
        initGit();
        commit("initial");
        tag("1.4.0");
        git("switch", "-c", "develop/1.4");
        commit("development");

        var result = fail("printVersion");
        assertTrue(result.getOutput().contains("which tag 1.4.0 has already reached"), result.getOutput());
    }

    // The merge moves the commit count, and under the old model that alone moved the version.
    @Test
    void backMergeDoesNotMoveTheReleaseNumber() throws Exception {
        initGit();
        commit("initial");
        tag("1.3.2");
        commit("hotfix");
        assertVersion(run("-q", "printVersion"), "1.3.3-dev.2.local");

        git("switch", "-c", "develop/1.4");
        commit("development");
        git("switch", "master");
        git("merge", "--no-ff", "develop/1.4", "-m", "release");

        assertVersion(run("-q", "printVersion"), "1.3.3-dev.4.local");
    }

    // A patch tag cut on top of merged development work becomes the new baseline of that development line. Counting
    // from it would drop the label below numbers the line has already published.
    @Test
    void theCounterSurvivesAPatchTagOvertakingTheLine() throws Exception {
        initGit();
        commit("initial");
        tag("1.3.0");
        git("switch", "-c", "develop/1.4");
        for (int index = 1; index <= 4; index++) {
            commit("development " + index);
        }
        assertVersion(run("-q", "printVersion"), "1.4.0-dev.5.local");

        git("switch", "master");
        git("merge", "--no-ff", "develop/1.4", "-m", "integrate");
        commit("hotfix");
        tag("1.3.1");
        git("switch", "develop/1.4");
        git("merge", "--no-ff", "master", "-m", "back-merge");

        assertVersion(run("-q", "printVersion"), "1.4.0-dev.8.local");
    }

    @Test
    void noTagUsesImplicitInitialBaseline() throws Exception {
        initGit();
        commit("initial");

        assertVersion(run("-q", "printVersion"), "0.0.1-dev.1.local");
    }

    @Test
    void highestReachableNumericTagWins() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");
        commit("ahead");
        tag("1.1.99");
        tag("latest");

        assertVersion(run("-q", "printVersion"), "1.2.4-dev.2.local");
    }

    @Test
    void dirtyWorktreeIsMarkedEvenOnATag() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");
        Files.writeString(this.projectDir.resolve("dirty.txt"), "dirty\n");

        assertVersion(run("-q", "printVersion"), "1.2.3-local.dirty");
    }

    @Test
    void pushedCommitDropsTheLocalMarker() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");
        commit("ahead");
        setRemoteBranch("master");

        assertVersion(run("-q", "printVersion"), "1.2.4-dev.2");
    }

    @Test
    void githubBuildCarriesTheRunNumber() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");
        commit("hotfix");

        assertVersion(run(githubBranch("master", "78"), "-q", "printVersion"), "1.2.4-dev.2+run.78");
    }

    @Test
    void githubDevelopmentBuildCarriesTheRunNumber() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");
        git("switch", "-c", "develop/1.4");
        commit("development");

        assertVersion(run(githubBranch("develop/1.4", "77"), "-q", "printVersion"), "1.4.0-dev.2+run.77");
    }

    // The head ref is the branch the pull request builds, whatever it targets.
    @Test
    void pullRequestUsesTheHeadBranch() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");
        git("switch", "-c", "develop/1.4");
        commit("development");

        assertVersion(run(githubPullRequest("develop/1.4", "79"), "-q", "printVersion"), "1.4.0-dev.2+run.79");

        git("switch", "-c", "feature/test");
        commit("feature");
        assertVersion(run(githubPullRequest("feature/test", "80"), "-q", "printVersion"), "1.2.4-dev.3+run.80");
    }

    @Test
    void unknownBranchLeadsToTheNextPatch() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");
        git("switch", "-c", "feature/test");
        commit("feature");

        assertVersion(run("-q", "printVersion"), "1.2.4-dev.2.local");
        assertVersion(run(githubBranch("feature/test", "83"), "-q", "printVersion"), "1.2.4-dev.2+run.83");
    }

    @Test
    void missingRemoteBranchRefsNoLongerFailTheBuild() throws Exception {
        initGit();
        commit("initial");
        git("switch", "-c", "feature/test");
        commit("feature");

        assertVersion(run(githubBranch("feature/test", "84"), "-q", "printVersion"), "0.0.1-dev.2+run.84");
    }

    @Test
    void labelComesFromTheExtension() throws Exception {
        writeBuild("""
                versioning {
                    label = 'nightly'
                }
                """);
        initGit();
        commit("initial");
        tag("1.2.3");
        commit("ahead");

        assertVersion(run("-q", "printVersion"), "1.2.4-nightly.2.local");
    }

    @Test
    void developmentPrefixComesFromTheExtension() throws Exception {
        writeBuild("""
                versioning {
                    developmentPrefix = 'next'
                }
                """);
        initGit();
        commit("initial");
        tag("1.2.3");
        git("switch", "-c", "next/1.4");
        commit("next");

        assertVersion(run("-q", "printVersion"), "1.4.0-dev.2.local");
    }

    // Metadata is appended in insertion order, behind the run number the plugin contributes under Actions.
    @Test
    void metadataAppendsAfterTheRunNumber() throws Exception {
        writeBuild("""
                versioning {
                    metadata.put('commit', 'a3f9c2')
                }
                """);
        initGit();
        commit("initial");
        tag("1.2.3");
        commit("ahead");

        assertVersion(run(githubBranch("master", "90"), "-q", "printVersion"), "1.2.4-dev.2+run.90.commit.a3f9c2");
        assertVersion(run("-q", "printVersion"), "1.2.4-dev.2.local+commit.a3f9c2");
    }

    // The run number is not part of the property, so assigning the map outright cannot drop it.
    @Test
    void assignedMetadataStillFollowsTheRunNumber() throws Exception {
        writeBuild("""
                versioning {
                    metadata = [commit: 'a3f9c2']
                }
                """);
        initGit();
        commit("initial");
        tag("1.2.3");
        commit("ahead");

        assertVersion(run(githubBranch("master", "91"), "-q", "printVersion"), "1.2.4-dev.2+run.91.commit.a3f9c2");
    }

    @Test
    void invalidMetadataFailsClearly() throws Exception {
        writeBuild("""
                versioning {
                    metadata.put('commit', 'a3f9 c2')
                }
                """);
        initGit();
        commit("initial");
        tag("1.2.3");
        commit("ahead");

        var result = fail("printVersion");
        assertTrue(result.getOutput().contains("metadata value of 'commit'"), result.getOutput());
    }

    // java-gradle-plugin reads project.version while the plugins block runs, too early for the extension to have been
    // configured, so a build that applies it has to configure through Gradle properties.
    @Test
    void configurationComesFromGradlePropertiesWithPublishingPlugins() throws Exception {
        writeBuild("""
                id 'java-gradle-plugin'
                id 'maven-publish'
                """, "");
        Files.writeString(this.projectDir.resolve("gradle.properties"), """
                versioning.stage = release
                versioning.label = nightly
                versioning.developmentPrefix = next
                """);
        initGit();
        commit("initial");
        tag("1.2.3");
        git("switch", "-c", "next/1.4");
        commit("next");

        assertVersion(run("-q", "printVersion"), "1.4.0-nightly.2.local");
    }

    @Test
    void githubTagBuildReleasesFromMaster() throws Exception {
        initGit();
        commit("initial");
        tag("1.3.2");
        git("switch", "-c", "develop/1.4");
        commit("development");
        git("switch", "master");
        git("merge", "--no-ff", "develop/1.4", "-m", "release");
        tag("1.4.0");
        setRemoteBranch("master");

        assertVersion(run(githubTag("1.4.0"), "-q", "printVersion"), "1.4.0");
    }

    @Test
    void githubTagBuildWithoutAReleaseRefSkipsReachability() throws Exception {
        initGit();
        commit("initial");
        tag("1.3.0");

        assertVersion(run(githubTag("1.3.0"), "-q", "printVersion"), "1.3.0");
    }

    @Test
    void githubTagBuildRejectsDirtyTree() throws Exception {
        initGit();
        commit("initial");
        tag("1.3.0");
        setRemoteBranch("master");
        Files.writeString(this.projectDir.resolve("dirty.txt"), "dirty\n");

        var result = fail(githubTag("1.3.0"), "printVersion");
        assertTrue(result.getOutput().contains("requires a clean Git worktree"), result.getOutput());
    }

    @Test
    void githubTagBuildRejectsCommitOffTheReleaseLine() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");
        setRemoteBranch("master");
        git("switch", "-c", "develop/1.4");
        commit("development");
        tag("1.4.0");

        var result = fail(githubTag("1.4.0"), "printVersion");
        assertTrue(result.getOutput().contains("is not reachable from origin/master"), result.getOutput());
    }

    @Test
    void githubTagBuildRejectsInvalidTag() throws Exception {
        initGit();
        commit("initial");
        tag("release-1.2.3");
        setRemoteBranch("master");

        var result = fail(githubTag("release-1.2.3"), "printVersion");
        assertTrue(result.getOutput().contains("must be numeric SemVer"), result.getOutput());
    }

    @Test
    void vPrefixedTagsAreAccepted() throws Exception {
        initGit();
        commit("initial");
        tag("v1.2.0");

        assertVersion(run("-q", "printVersion"), "1.2.0");

        commit("ahead");
        assertVersion(run("-q", "printVersion"), "1.2.1-dev.2.local");

        setRemoteBranch("master");
        tag("v1.2.1");
        assertVersion(run(githubTag("v1.2.1"), "-q", "printVersion"), "1.2.1");
    }

    // Git's own version sort ranks a leading "v" above a digit, so a mixed repository needs the numeric comparison.
    @Test
    void highestTagWinsAcrossMixedTagForms() throws Exception {
        initGit();
        commit("initial");
        tag("v1.2.0");
        tag("2.0.0");
        commit("ahead");

        assertVersion(run("-q", "printVersion"), "2.0.1-dev.2.local");
    }

    @Test
    void declaredProjectVersionFailsMigration() throws Exception {
        Files.writeString(this.projectDir.resolve("gradle.properties"),
                "version = 1.2.3\nversioning.stage = release\n");
        initGit();
        commit("initial");

        var result = fail("printVersion");
        assertTrue(result.getOutput().contains("remove the declared 'version' property"), result.getOutput());
    }

    @Test
    void versionDeclaredAfterPluginApplicationFailsMigration() throws Exception {
        writeBuild("version = '1.2.3'\n");
        initGit();
        commit("initial");

        var result = fail("printVersion");
        assertTrue(result.getOutput().contains("remove the declared 'version' property"), result.getOutput());
    }

    @Test
    void stageDefaultsToBetaBelowFirstMajor() throws Exception {
        writeBuild(PRINT_STAGE);
        Files.writeString(this.projectDir.resolve("gradle.properties"), "");
        initGit();
        commit("initial");
        tag("0.9.0");

        assertStage(run("-q", "printStage"), "beta");

        tag("1.0.0");
        assertStage(run("-q", "printStage"), "release");
    }

    @Test
    void stageCanBeSetInExtension() throws Exception {
        writeBuild(PRINT_STAGE + """
                versioning {
                    stage = 'alpha'
                }
                """);
        initGit();
        commit("initial");

        assertStage(run("-q", "printStage"), "alpha");
    }

    // The property is typed, so a consumer can switch on the value rather than compare strings.
    @Test
    void stageCanBeSetAndReadAsTheEnum() throws Exception {
        writeBuild("""
                import com.cleanroommc.versioning.Stage

                versioning {
                    stage = Stage.RC
                }

                tasks.register('printStage') {
                    def stage = versioning.stage
                    doLast {
                        logger.quiet(stage.get() == Stage.RC ? 'rc enum' : 'not rc')
                    }
                }
                """);
        initGit();
        commit("initial");

        assertStage(run("-q", "printStage"), "rc enum");
    }

    @Test
    void invalidStageFailsClearly() throws Exception {
        Files.writeString(this.projectDir.resolve("gradle.properties"), "versioning.stage = nightly\n");
        initGit();
        commit("initial");

        var result = fail("printVersion");
        assertTrue(result.getOutput().contains("versioning.stage must be one of"), result.getOutput());
    }

    @Test
    void invalidStageFromTheExtensionFailsClearly() throws Exception {
        writeBuild("""
                versioning {
                    stage = 'nightly'
                }
                """);
        initGit();
        commit("initial");

        var result = fail("printVersion");
        assertTrue(result.getOutput().contains("versioning.stage must be one of"), result.getOutput());
    }

    @Test
    void missingRepositoryAndCommitFailClearly() throws Exception {
        var missingRepository = fail("printVersion");
        assertTrue(missingRepository.getOutput().contains("requires a Git repository"), missingRepository.getOutput());

        initGit();
        var missingCommit = fail("printVersion");
        assertTrue(missingCommit.getOutput().contains("requires at least one Git commit"), missingCommit.getOutput());
    }

    @Test
    void missingGitFailsClearly() {
        var unavailableGit = (ExecOperations) Proxy.newProxyInstance(
                ExecOperations.class.getClassLoader(),
                new Class<?>[]{ExecOperations.class},
                (proxy, method, arguments) -> {
                    throw new RuntimeException("missing executable");
                }
        );
        var source = new GitSnapshotValueSource() {
            @Override
            public Parameters getParameters() {
                throw new AssertionError("parameters should not be read when Git cannot start");
            }

            @Override
            protected ExecOperations getExecOperations() {
                return unavailableGit;
            }
        };

        var exception = assertThrows(GradleException.class, source::obtain);
        assertTrue(exception.getMessage().contains("requires Git to be installed and available on PATH"));
    }

    @Test
    void configurationCacheInvalidatesAfterCommit() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");

        var first = run("--configuration-cache", "-q", "printVersion");
        assertVersion(first, "1.2.3");

        commit("ahead");
        var second = run("--configuration-cache", "-q", "printVersion");
        assertVersion(second, "1.2.4-dev.2.local");
        assertNotEquals(versionLine(first), versionLine(second));
    }

    @Test
    void configurationCacheReusesUnchangedGitSnapshot() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");

        run("--configuration-cache", "-q", "printVersion");
        var second = run("--configuration-cache", "printVersion");

        assertTrue(second.getOutput().contains("Reusing configuration cache."), second.getOutput());
        assertVersion(second, "1.2.3");
    }

    @Test
    void configurationCacheTracksTagBranchAndDirtyState() throws Exception {
        initGit();
        commit("initial");

        assertVersion(run("--configuration-cache", "-q", "printVersion"), "0.0.1-dev.1.local");

        tag("1.2.3");
        assertVersion(run("--configuration-cache", "-q", "printVersion"), "1.2.3");

        commit("ahead");
        assertVersion(run("--configuration-cache", "-q", "printVersion"), "1.2.4-dev.2.local");

        git("switch", "-c", "develop/1.4");
        assertVersion(run("--configuration-cache", "-q", "printVersion"), "1.4.0-dev.2.local");

        Files.writeString(this.projectDir.resolve("dirty.txt"), "dirty\n");
        assertVersion(run("--configuration-cache", "-q", "printVersion"), "1.4.0-dev.2.local.dirty");
    }

    @Test
    void shallowGithubCheckoutFailsClearly() throws Exception {
        initGit();
        commit("initial");
        tag("1.2.3");
        commit("ahead");
        Path source = this.projectDir;
        Path shallow = this.tempDir.resolve("shallow");
        git(this.tempDir, "clone", "--depth", "1", source.toUri().toString(), shallow.toString());
        this.projectDir = shallow;

        var result = fail(githubBranch("master", "85"), "printVersion");
        assertTrue(result.getOutput().contains("requires full Git history in CI"), result.getOutput());
    }

    @Test
    void multiProjectBuildUsesOneVersion() throws Exception {
        Files.writeString(this.projectDir.resolve("settings.gradle"),
                "rootProject.name = 'versioning-test'\ninclude 'child'\n");
        Path child = Files.createDirectories(this.projectDir.resolve("child"));
        Files.writeString(child.resolve("build.gradle"), "plugins { id 'com.cleanroommc.versioning' }\n");
        initGit();
        commit("initial");
        tag("1.2.3");

        var result = run("-q", "printVersion", ":child:printVersion");
        assertEquals(2, result.getOutput().lines().filter("1.2.3"::equals).count(), result.getOutput());
    }

    // The release branch is a per-project setting, so it cannot come from whichever project registered the shared
    // repository snapshot first.
    @Test
    void projectsInOneRepositoryCanNameDifferentReleaseBranches() throws Exception {
        Files.writeString(this.projectDir.resolve("settings.gradle"),
                "rootProject.name = 'versioning-test'\ninclude 'child'\n");
        writeBuild("""
                versioning {
                    releaseBranch = 'master'
                }
                """);
        Path child = Files.createDirectories(this.projectDir.resolve("child"));
        Files.writeString(child.resolve("build.gradle"), """
                plugins {
                    id 'com.cleanroommc.versioning'
                }

                versioning {
                    releaseBranch = 'stable'
                }
                """);
        initGit();
        commit("initial");
        git("switch", "-c", "stable");
        commit("stable work");
        tag("1.3.0");
        setRemoteBranch("stable");
        // origin/master exists and does not contain the tag, so inheriting it would fail the release
        git("update-ref", "refs/remotes/origin/master", "master");

        assertVersion(run(githubTag("1.3.0"), "-q", ":child:printVersion"), "1.3.0");
    }

    // A git submodule pulled in as an ordinary subproject is its own repository, so it has its own tags.
    @Test
    void aSubprojectInItsOwnRepositoryVersionsFromThatRepository() throws Exception {
        Files.writeString(this.projectDir.resolve("settings.gradle"),
                "rootProject.name = 'versioning-test'\ninclude 'vendored'\n");
        Files.writeString(this.projectDir.resolve(".gitignore"), ".gradle/\nbuild/\nvendored/\n");
        Path vendored = Files.createDirectories(this.projectDir.resolve("vendored"));
        Files.writeString(vendored.resolve("build.gradle"), "plugins { id 'com.cleanroommc.versioning' }\n");
        initGit();
        commit("initial");
        tag("1.2.3");

        Files.writeString(vendored.resolve(".gitignore"), ".gradle/\nbuild/\n");
        git(vendored, "init", "-b", "master");
        git(vendored, "config", "user.email", "test@example.com");
        git(vendored, "config", "user.name", "Test");
        git(vendored, "config", "commit.gpgsign", "false");
        git(vendored, "config", "tag.gpgsign", "false");
        git(vendored, "add", ".");
        git(vendored, "commit", "-m", "vendored");
        git(vendored, "tag", "5.6.7");

        var result = run("-q", "printVersion", ":vendored:printVersion");
        assertTrue(result.getOutput().lines().anyMatch("1.2.3"::equals), result.getOutput());
        assertTrue(result.getOutput().lines().anyMatch("5.6.7"::equals), result.getOutput());
    }

    private void writeBuild(String additionalBody) throws IOException {
        writeBuild("", additionalBody);
    }

    private void writeBuild(String additionalPlugins, String additionalBody) throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'com.cleanroommc.versioning'
                %s
                }
                %s
                """.formatted(additionalPlugins.indent(4), additionalBody));
    }

    private BuildResult run(String... args) {
        return runner(Map.of(), args).build();
    }

    private BuildResult run(Map<String, String> environment, String... args) {
        return runner(environment, args).build();
    }

    private BuildResult fail(String... args) {
        return runner(Map.of(), args).buildAndFail();
    }

    private BuildResult fail(Map<String, String> environment, String... args) {
        return runner(environment, args).buildAndFail();
    }

    private GradleRunner runner(Map<String, String> additions, String... args) {
        var arguments = new ArrayList<String>();
        arguments.addAll(List.of(args));
        arguments.add("--stacktrace");
        var environment = new HashMap<>(System.getenv());
        GITHUB_ENVIRONMENT.forEach(environment::remove);
        environment.putAll(additions);
        return GradleRunner.create()
                .withProjectDir(this.projectDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .withEnvironment(environment)
                .forwardOutput();
    }

    private void initGit() throws Exception {
        git("init", "-b", "master");
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "Test");
        git("config", "commit.gpgsign", "false");
        git("config", "tag.gpgsign", "false");
    }

    private void commit(String message) throws Exception {
        Files.writeString(this.projectDir.resolve("note.txt"), message + "\n");
        git("add", ".");
        git("commit", "-m", message);
    }

    private void tag(String name) throws Exception {
        git("tag", name);
    }

    private void setRemoteBranch(String branch) throws Exception {
        git("update-ref", "refs/remotes/origin/" + branch, "HEAD");
    }

    private void git(String... args) throws Exception {
        git(this.projectDir, args);
    }

    private void git(Path directory, String... args) throws Exception {
        gitOutput(directory, args);
    }

    private String gitOutput(Path directory, String... args) throws Exception {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        var finished = process.waitFor(30, TimeUnit.SECONDS);
        var output = new String(process.getInputStream().readAllBytes());
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("git timed out: " + command + "\n" + output);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("git failed (" + process.exitValue() + "): " + command + "\n" + output);
        }
        return output;
    }

    private static Map<String, String> githubBranch(String branch, String run) {
        return github("branch", branch, "", run);
    }

    private static Map<String, String> githubPullRequest(String head, String run) {
        return github("branch", "123/merge", head, run);
    }

    private static Map<String, String> githubTag(String tag) {
        return github("tag", tag, "", "1");
    }

    private static Map<String, String> github(String refType, String refName, String head, String run) {
        Map<String, String> environment = new HashMap<>();
        environment.put("GITHUB_ACTIONS", "true");
        environment.put("GITHUB_REF_TYPE", refType);
        environment.put("GITHUB_REF_NAME", refName);
        environment.put("GITHUB_HEAD_REF", head);
        environment.put("GITHUB_RUN_NUMBER", run);
        return environment;
    }

    private static void assertVersion(BuildResult result, String expected) {
        assertEquals(TaskOutcome.SUCCESS, result.task(":printVersion") == null
                ? result.task(":child:printVersion").getOutcome()
                : result.task(":printVersion").getOutcome());
        assertTrue(result.getOutput().lines().anyMatch(expected::equals), result.getOutput());
    }

    private static void assertStage(BuildResult result, String expected) {
        assertTrue(result.getOutput().lines().anyMatch(expected::equals), result.getOutput());
    }

    private static String versionLine(BuildResult result) {
        return result.getOutput().lines()
                .filter(line -> line.matches("[0-9]+\\.[0-9]+\\.[0-9]+.*"))
                .findFirst()
                .orElseThrow();
    }

}
