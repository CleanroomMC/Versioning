# Cleanroom Versioning

`git tag`-native SemVer for Gradle.
Git tags are the single source of truth for the number.
Commits between tags advance an ordered pre-release label instead of the number.

## Policy

Tags are strict SemVer, with or without a `v` prefix, and they carry every numeric component:

```text
0.1.0
v0.2.0
1.0.0
1.1.1
```

Both forms are read the same way and the prefix never reaches the computed version, a tag `v1.2.0` would produce version `1.2.0`.
Pick one form and stay with it even though the two are interchangeable, mixing them makes a tag list harder to read.

The highest numeric tag reachable from `HEAD` is the baseline. Only a tag moves the number. Everything else moves the label.

```text
exact tag        ->  <tag>
otherwise        ->  <number>-<label>.<counter>[.local][.dirty][+<key>.<value>...]

baseline         =   highest numeric tag reachable from HEAD, else 0.0.0
counter          =   commits since the baseline tag, or the whole history before the first tag
number           =   develop/<major>.<minor> branch  ->  that version, pinned
                     release branch                  ->  baseline with the patch advanced once
                     anything else                   ->  the line the branch was cut from
label            =   versioning.label, else "dev" on the release and development branches,
                     else the branch name
```

| State                                                  | Version                      |
|--------------------------------------------------------|------------------------------|
| on tag `1.1.1`                                         | `1.1.1`                      |
| `master`, three commits later, in Actions run 24       | `1.1.2-dev.3+run.24`         |
| `master`, nine commits later, in Actions run 25        | `1.1.2-dev.9+run.25`         |
| `master`, three commits later, not pushed              | `1.1.2-dev.3.local`          |
| `master`, three commits later, uncommitted changes     | `1.1.2-dev.3.local.dirty`    |
| `develop/1.4`, seven commits later, in run 26          | `1.4.0-dev.7+run.26`         |
| `develop/2.0`, seven commits later, in run 27          | `2.0.0-dev.7+run.27`         |
| `feature/foo` off `develop/1.4`, three commits, run 28 | `1.4.0-feature-foo.3+run.28` |
| `fix/crash` off `master`, one commit, in run 29        | `1.1.2-fix-crash.1+run.29`   |
| no tags yet, `master`, seven commits                   | `0.0.1-dev.7.local`          |

## Guarantees

**The number only moves when a tag moves it.** Ten commits and one commit past `1.1.1` both read `1.1.2`, they differ in the label. Merging a branch back does not bump anything, it only advances the counter. The patch advances once off the baseline because those builds lead to the next patch, and it stays there until that patch is tagged.

**Versions increase with every commit and never regress.** `1.1.1 < 1.1.2-dev.3 < 1.1.2-dev.9 < 1.1.2`. Under SemVer a pre-release sorts below the release it leads to, and Gradle's own comparator ranks `dev` the same way, so a development build is superseded by its release rather than outranking it.

**The counter is commits since the baseline tag.** A branch cut at `1.3.0` reads `dev.1` on its first commit and `dev.2` on its second, so the label says how far the line has come rather than how large the repository is. It only restarts when the baseline moves, which on a development branch means a tag was cut on top of work the branch already contains. Tag the version you merged rather than a patch on top of it and the branch is finished at that point anyway.

**Working branch ordering depends on the label.** For the same numeric version, `feature-foo` and `fix-crash` sort above `dev`, while `chore-cleanup` sorts below it. SemVer compares these labels in ASCII order before comparing the counter, so `1.4.0-feature-foo.3` sorts above `1.4.0-dev.9`. Working branches are never published.

**A version still does not identify a commit.** Two branches leading to the same number and holding the same commit count compute the same coordinate. What keeps a published coordinate apart is the `run` entry, which every build under GitHub Actions carries and no local build does. **Only builds carrying a run number may be published.** Anything built outside Actions is a local build and must not reach a repository.

Metadata sits after `+`, so SemVer ignores it for precedence: two runs of the same commit are distinct coordinates that rank equally. That is the point of putting it there. Ordering is the label's job and identity is metadata's, and mixing the two would let a commit hash decide which build is newer.

`.local` and `.dirty` are appended last so the orderable part of the label stays contiguous. That does mean a local build of a commit outranks the CI build of the same commit, which is harmless because a local build must never be published.

## Tagging

A tag sets the number outright, so all three components are cut by hand. `1.4.0` opens the next minor, `1.1.1` cuts a patch. There is no longer any component derived from the commit count, and no tag shape is discouraged.

Before the first tag the baseline is `0.0.0` and `master` starts at `0.0.1`.

Rewritten history and deleted tags need no special handling. Every build recomputes from whatever tag is reachable now.

## Branches

`master` is the release line. Every commit on it leads to the next patch, which makes it the place fixes and hotfixes land.

A branch named `develop/<major>.<minor>` is a development line pinned to the version it leads to. It stays on that number for its whole life and only the label counter moves. Any number of them can run at once, so a long horizon major can be developed on `develop/2.0` while the next minor is built on `develop/1.4`, with `master` still shipping `1.3.x` fixes in between.

`develop/1.4` and `develop/1.4.0` both mean `1.4.0`, and a `v` prefix is accepted. A non-zero patch such as `develop/1.4.3` fails the build, patch versions are cut on the release branch rather than developed towards.

Every other branch, `feature/foo` and `fix/crash` included, is a working branch. It follows the line it was cut from and counts its commits under a label of its own, so `feature/foo` cut from `develop/1.4` reads `1.4.0-feature-foo.3` while the same branch cut from `master` reads `1.3.1-feature-foo.3`. The label is the branch name with everything outside `[0-9A-Za-z-]` replaced by a hyphen, so `fix/crash_on_load` counts under `fix-crash-on-load`. A name that sanitises to nothing, or to digits alone, falls back to `dev`. Setting `versioning.label` replaces the derived name.

Git records no parent branch, so the line is worked out from the remote refs. Whichever of `origin/<releaseBranch>` and `origin/<developmentPrefix>/*` the branch holds the fewest commits beyond is the line it came from, and the release branch takes a tie. Where the release branch wins, or nothing is there to compare against, the release rule applies as before. The answer is a guess and merges in both directions can move it, so it is only ever advisory: an inherited number that a tag has already reached is dropped and the branch falls back to the release rule, rather than failing the build the way `develop/1.4` itself would. A sibling ref that cannot be read as a target, `origin/develop/foo` or `origin/develop/1.4.3`, is skipped. Only the branch you are standing on has to be named correctly.

A development branch has to lead somewhere the tags have not already reached. Once `1.4.0` is tagged and merged back, `develop/1.4` would compute `1.4.0-dev.N`, which sits below the release it just shipped, so the build fails and asks for the branch to be retargeted or deleted. That is the point at which the branch has done its job.

The intended release flow is:

1. Merge `develop/1.4` into `master`.
2. Create the `1.4.0` tag on `master`.
3. Let the tag workflow publish the release.
4. Delete `develop/1.4`, or rename it to the next line it leads to.

### Single branch

A repository with only `master` is supported and needs no extra configuration. Every commit leads to the next patch, and the number moves when a tag moves it.

## Gradle plugin

From the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/com.cleanroommc.versioning):

```groovy
plugins {
    id 'com.cleanroommc.versioning' version '3.0.0'
}
```

Or from [CleanroomMC Maven](https://maven.cleanroommc.com):

```groovy
// settings.gradle
pluginManagement {
    repositories {
        maven {
            url = 'https://maven.cleanroommc.com'
        }
        gradlePluginPortal()
    }
}
```

Do not declare `version` in `gradle.properties` or the build script. The plugin assigns `project.version` from Git and fails the build if a version is already set.

Stage is an independent marker for upload sites and archive names, one of `alpha`, `beta`, `rc`, `release`. Anything else fails the build. It is optional. Left unset it is `beta` while the version line is below `1.0.0` and `release` from there on. It never reaches the version, the plugin only resolves it so a bad name fails the build early.

It is a `Property<Stage>`, and the plugin jar carries the enum, so a build script can name it either way and branch on it without comparing strings:

```groovy
import com.cleanroommc.versioning.Stage

versioning {
    stage = Stage.ALPHA  // 'alpha' works too, case insensitively
}

publishing.repositories.maven {
    url = versioning.stage.map { it == Stage.RELEASE ? releasesUrl : snapshotsUrl }
}
```

```groovy
versioning {
    stage = 'alpha'
    label = 'nightly'
    developmentPrefix = 'next'
    releaseBranch = 'main'
}
```

The same four settings are available as Gradle properties:

```properties
# gradle.properties
versioning.stage = alpha
versioning.label = nightly
versioning.developmentPrefix = next
versioning.releaseBranch = main
```

> [!IMPORTANT]
> Use the Gradle properties when the build also applies `java-gradle-plugin`. That plugin reads `project.version` while the `plugins` block is still running, which is before a build script can configure the extension, so the extension values would arrive too late.

`metadata` adds build metadata of your own after `+`, in the order you put them:

```groovy
versioning {
    metadata.put('commit', providers.exec { commandLine 'git', 'rev-parse', '--short', 'HEAD' }
            .standardOutput.asText.map { it.trim() })
}
```

Under GitHub Actions the run number is emitted as `run` ahead of anything configured here, so the example above produces `1.1.2-dev.3+run.24.commit.a3f9c2`. Keys and values have to be SemVer identifiers, alphanumerics and hyphens. A leading zero is allowed there, because an abbreviated commit hash is occasionally all digits. The label may not carry one, and may not be a bare number.

The extension exposes:

```groovy
versioning.version           // Provider<String>, also project.version
versioning.stage             // Property<Stage>, settable from a Stage or its name
versioning.label             // Property<String>, settable
versioning.metadata          // MapProperty<String, String>, settable, emitted after +
versioning.developmentPrefix // Property<String>, settable
versioning.releaseBranch     // Property<String>, settable
```

`./gradlew -q :printVersion` prints the computed version for scripts and workflows.

## GitHub Actions

Builds under GitHub Actions are treated as pushed and carry the run number, except on a tag. Builds outside Actions carry no run number and must not be published. A pull request is versioned as the branch it comes from, not the one it targets.

A merge into the release branch is worth skipping. It sits between the merge and the release tag, so it computes the next patch of the previous tag, a number that will never ship. Development branches pin their number, so their merges are correct and should still build:

```yaml
jobs:
  guard:
    runs-on: ubuntu-latest
    outputs:
      skip: ${{ steps.check.outputs.skip }}
    steps:
      - uses: actions/checkout@v7
        with:
          fetch-depth: 2
      - id: check
        run: |
          parents=$(git rev-list --parents -n 1 HEAD | wc -w)
          if [ "$parents" -gt 2 ] && [ "$GITHUB_REF" = 'refs/heads/master' ]; then
            echo "skip=true" >> "$GITHUB_OUTPUT"
          else
            echo "skip=false" >> "$GITHUB_OUTPUT"
          fi

  build:
    needs: guard
    if: needs.guard.outputs.skip != 'true'
```

Working branches build and are never published. The gate below already excludes them, since it tests for `refs/heads/develop/`.

A workflow that publishes development builds has to check that it is holding a development version. Branching at a release tag, `develop/1.4` created on `1.3.0` for instance, computes the bare `1.3.0`, and publishing that from a branch push would overwrite the release. A bare version is only ever produced on an exact tag, so gating on a pre-release is enough:

```yaml
- name: Print version
  id: version
  run: |
    version=$(./gradlew -q --console=plain :printVersion)
    echo "$version"
    echo "value=$version" >> "$GITHUB_OUTPUT"

- name: Publish development build
  if: ${{ startsWith(github.ref, 'refs/heads/develop/') && contains(steps.version.outputs.value, '-') }}
  run: ./gradlew publish
```

Checkouts need full history and tags. The full fetch also brings every `origin/*` branch ref, which is what a working branch's line is worked out from:

```yaml
- uses: actions/checkout@v7
  with:
    fetch-depth: 0
    fetch-tags: true
```

Tag triggers need both forms if the repository uses both:

```yaml
on:
  push:
    tags:
      - '[0-9]+.[0-9]+.[0-9]+'
      - 'v[0-9]+.[0-9]+.[0-9]+'
```

A tag build is validated before it publishes. The tag must be the highest numeric tag reachable from `HEAD`, the worktree must be clean, and the commit must be reachable from `origin/<releaseBranch>`. When that remote ref does not exist the reachability check is skipped, so a repository that does not use it can still release.

## Core API

`com.cleanroommc:versioning` targets Java 21 and has no Gradle dependency.

```java
// latest tag, branch target, commits since the tag, dirty, pushed
GitState master = new GitState(SemanticVersion.parse("1.1.1"), null, 3, false, true);

Versioning.compute(master, "dev", Map.of());              // 1.1.2-dev.3
Versioning.compute(master, "dev", Map.of("run", "24"));   // 1.1.2-dev.3+run.24

GitState development = new GitState(SemanticVersion.parse("1.1.1"), SemanticVersion.parseTarget("1.4"), 7, false, true);

Versioning.compute(development, "dev", Map.of());         // 1.4.0-dev.7
```
