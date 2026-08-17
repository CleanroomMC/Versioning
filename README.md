# Cleanroom Versioning

Declared SemVer + stage, with git distance as build metadata.

| Mode    | Trigger                  | Example                                                                |
|---------|--------------------------|------------------------------------------------------------------------|
| Local   | neither flag             | `0.6.10-alpha+local.48`                                                |
| CI      | `-Pversioning.run=N`     | `0.6.10-alpha+build.48.run.N`                                          |
| Release | `-Pversioning.publish`   | `0.6.10-alpha`, only if the tag equals that string and distance is `0` |

Stage `release` drops the prerelease suffix (`0.6.10`). Tags embed the stage (`0.6.10-alpha`, no `v` prefix).

## Gradle plugin

From the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/com.cleanroommc.versioning)
(plugin id `com.cleanroommc.versioning`, artifact `com.cleanroommc:versioning-gradle`):

```groovy
plugins {
    id 'com.cleanroommc.versioning' version '1.0.0'
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
versioning.version      // 0.6.10-alpha+local.48
versioning.baseVersion  // 0.6.10-alpha
versioning.stage        // alpha
versioning.distance     // 48
versioning.tag          // 0.6.10-alpha
```

## API

`com.cleanroommc:versioning` targets Java 8 and has no Gradle dependency.
It is published to [maven.cleanroommc.com](https://maven.cleanroommc.com).

```java
ComputedVersion computed = Versioning.compute("0.6.10", "alpha", "0.6.10-alpha-48-gf5e9227e", false, null);
computed.version();     // 0.6.10-alpha+local.48
computed.baseVersion(); // 0.6.10-alpha
```
