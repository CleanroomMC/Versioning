# Cleanroom Versioning

Declared SemVer + stage, with git distance as build metadata.

The maven version and git tag are the numeric version.
Stage comes only from `versioning.stage`; it is not part of the GAV or the tag.

## v1 → v2

v1 encoded the stage in the maven version and the git tag (`0.6.10-alpha`).
Every consumer had to write `0.6.10-alpha` instead of `0.6.10`.

v2 leaves the coordinate and tag as the numeric version so dependents can depend on `com.cleanroommc:foo:0.6.10`.
`versioning.stage` is a marker for people and for user-facing upload sites (Modrinth, CurseForge):
the release channel those sites should publish as, and `versioning.artifactSuffix` (`-alpha`)
for archive names if a project wants it on the file people download.
`baseVersion()` is always numeric and a publish requires tag `0.6.10`, not `0.6.10-alpha`.

| Mode    | Trigger                | Example                                                       |
|---------|------------------------|---------------------------------------------------------------|
| Local   | neither flag           | `0.6.10+local.48`                                             |
| CI      | `-Pversioning.run=N`   | `0.6.10+build.48.run.N`                                       |
| Release | `-Pversioning.publish` | `0.6.10`, only if the tag equals `0.6.10` and distance is `0` |

Tags have no `v` prefix and match the Maven version (`0.6.10`). Publishing the same
number again to the same Maven repo overwrites that GAV — bump the numeric version
to ship new bits.

## Gradle plugin

From the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/com.cleanroommc.versioning)
(plugin id `com.cleanroommc.versioning`, artifact `com.cleanroommc:versioning-gradle`):

```groovy
plugins {
    id 'com.cleanroommc.versioning' version '2.0.0'
}
```

Or from [CleanroomMC's own maven](https://maven.cleanroommc.com):

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

```properties
# gradle.properties
version = 0.6.10
# one of: alpha, beta, rc, release
versioning.stage = alpha
```

The plugin overwrites `project.version`. A `printVersion` task prints the computed value (used by CI via `./gradlew -q printVersion`).

```groovy
versioning.version         // 0.6.10+local.48
versioning.baseVersion     // 0.6.10
versioning.artifactSuffix  // -alpha
versioning.stage           // alpha
versioning.distance        // 48
versioning.tag             // 0.6.10
```

## API

`com.cleanroommc:versioning` targets Java 8 and has no Gradle dependency.
It is published to [maven.cleanroommc.com](https://maven.cleanroommc.com).

```java
ComputedVersion computed = Versioning.compute("0.6.10", "alpha", "0.6.10-48-gf5e9227e", false, null);
computed.version();         // 0.6.10+local.48
computed.baseVersion();     // 0.6.10
computed.artifactSuffix();  // -alpha
```
