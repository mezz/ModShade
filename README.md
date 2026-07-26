# ModShade

ModShade is a Gradle plugin for Minecraft mods that need to shade plain Java
libraries into a mod jar. It relocates shaded packages to avoid conflicts with
other mods, and keeps those libraries out of published Maven dependency
metadata.

ModShade is not for embedding Fabric, Forge, NeoForge, or other Minecraft mods.
For those, use your loader's nested-jar support or declare a mod dependency.

## Requirements

ModShade requires Gradle 7.6.4 or newer.

## Quick start

```kotlin
plugins {
    java
    id("net.mezzdev.modshade") version "0.2.0"
}

dependencies {
    modShadeImplementation("net.mezzdev:deduplicating-runner:0.1.0")
}

java {
    withSourcesJar()
}

modShade {
    shadeJar()
    shadeSourcesJar()
}
```

Run:

```shell
./gradlew build
```

Outputs:

```text
build/libs/<archivesName>-<version>.jar
build/libs/<archivesName>-<version>-unshaded.jar
build/libs/<archivesName>-<version>-sources.jar
build/libs/<archivesName>-<version>-sources-unshaded.jar
```

The normal runtime and sources classifiers are produced by ModShade. The
`unshaded` jars are kept as diagnostics.

All ModShade dependency configurations mean "bundle this into the shaded jar
instead of publishing it as an external dependency." Pick the configuration that
matches the local classpaths that need the original, unrelocated library:

| Configuration | Closest Java configuration | Local compile classpath | Local runtime/test runtime classpath |
| --- | --- | --- | --- |
| `modShadeImplementation(...)` | `implementation(...)` | Yes | Yes |
| `modShadeCompileOnly(...)` | `compileOnly(...)` | Yes | No |
| `modShadeRuntimeOnly(...)` | `runtimeOnly(...)` | No | Yes |

Most mods should use `modShadeImplementation(...)`.

The dependency configurations use longer names to match Gradle's classpath
behavior. The extension block is still `modShade { ... }`.

Use `modShadeCompileOnly(...)` when the original, unrelocated library should not
be visible on local Java runtime/test runtime classpaths:

```kotlin
dependencies {
    modShadeCompileOnly("com.example:plain-library:1.0")
}
```

Use `modShadeRuntimeOnly(...)` for a library that is loaded reflectively or by a
service loader and is not referenced by your source code:

```kotlin
dependencies {
    modShadeRuntimeOnly("com.example:runtime-helper:1.0")
}
```

For a loader-specific or custom run configuration that does not use Java
`runtimeClasspath`, extend that configuration from the runtime-visible ModShade
buckets:

```kotlin
configurations.named("minecraftRuntimeClasspath") {
    extendsFrom(
        configurations.modShadeImplementation.get(),
        configurations.modShadeRuntimeOnly.get()
    )
}
```

## Plain libraries only

Use ModShade dependency configurations for plain Java libraries only. ModShade
fails the build if a shaded dependency contains common mod-loader metadata.

Use the loader-supported packaging model for mods or loader-aware dependencies:

- Fabric: use [Loom `include`](https://docs.fabricmc.net/develop/loom/#dependency-configurations),
  [Fabric nested jars](https://docs.fabricmc.net/develop/loader/#nested-jars),
  or declare a mod dependency.
- Forge: use [ForgeGradle Jar-in-Jar](https://docs.minecraftforge.net/en/fg-6.x/dependencies/jarinjar/)
  or declare a mod dependency.
- NeoForge: use [ModDevGradle `jarJar`](https://docs.neoforged.net/toolchain/docs/plugins/mdg/#jar-in-jar),
  follow NeoForge's guidance for
  [non-Minecraft dependencies](https://docs.neoforged.net/toolchain/docs/dependencies/nonmclibs/),
  or declare a mod dependency.

Only opt out for unusual legacy artifacts with stale loader metadata that are
intentionally being treated as plain libraries:

```kotlin
modShade {
    failOnModJars.set(false)
}
```

This opt-out does not make shading other mods a correct distribution strategy.

## Loader support

For supported rows, use the quick-start `shadeJar()` and `shadeSourcesJar()`
setup unless the row says to use explicit archive tasks.

### NeoForge

| Tool / version | Minecraft versions | Final archives | Status |
| --- | --- | --- | --- |
| ModDevGradle 2.x | Minecraft 1.21 through 26.2 | `jar`, `sourcesJar` | Supported. Tested by [test/ModDevGradle2](test/ModDevGradle2/build.gradle.kts) with Minecraft 26.2 and ModDevGradle 2.0.142. |
| ModDevGradle 1.x | Minecraft 1.21 through 1.21.11 | `jar`, `sourcesJar` | Supported. Tested by [test/ModDevGradle1](test/ModDevGradle1/build.gradle.kts) with Minecraft 1.21.1 and ModDevGradle 1.0.24. |
| NeoGradle 7.x | Minecraft 1.20.2 through 26.2 | `jar`, `sourcesJar` | Supported. Tested by [test/NeoGradle7](test/NeoGradle7/build.gradle.kts) with Minecraft 1.21.1 and NeoGradle 7.1.38. |
| Custom archive tasks | Project-defined | Project-defined | Use [explicit archive tasks](#explicit-archive-tasks). |

### Fabric

| Tool / version | Minecraft versions | Final archives | Status |
| --- | --- | --- | --- |
| Fabric Loom 1.17.x, `net.fabricmc.fabric-loom` | Minecraft 26.1 through 26.2 | `jar`, `sourcesJar` | Supported. Tested by [test/FabricLoom117NonRemap](test/FabricLoom117NonRemap/build.gradle.kts) with Minecraft 26.2 and Loom 1.17.12. |
| Fabric Loom 1.17.x, `net.fabricmc.fabric-loom-remap` | Minecraft releases 1.14 through 1.21.11; snapshots from 18w43b until before 26.1 | `remapJar`, `remapSourcesJar` | Supported. Tested by [test/FabricLoom117Remap](test/FabricLoom117Remap/build.gradle.kts) with Minecraft 1.20.1 and Loom 1.17.12. |
| Fabric Loom 1.17.x intermediary API/common archives | Minecraft releases 1.14 through 1.21.11; snapshots from 18w43b until before 26.1 | Project-defined | Not recommended for API-only jars. If the jar intentionally contains implementation classes, use [explicit archive tasks](#explicit-archive-tasks). Tested by [test/FabricLoom117Remap](test/FabricLoom117Remap/build.gradle.kts). |
| Fabric Loom 1.15.3 + Legacy Looming 1.15.3 | Minecraft 1.3 through 1.13.2, plus 1.14 snapshots | `remapJar`, `remapSourcesJar` | Supported. Tested by [test/LegacyFabricLoom115](test/LegacyFabricLoom115/build.gradle.kts) with Minecraft 1.13.2. |
| Custom archive tasks | Project-defined | Project-defined | Use [explicit archive tasks](#explicit-archive-tasks). |

Fabric support starts at Minecraft release 1.14 and snapshot 18w43b. Older
versions use [Legacy Fabric](https://legacyfabric.net/). For ModShade, the
important difference is the archive task: Minecraft 26.1+ publishes `jar`, while
earlier Fabric and Legacy Fabric builds publish `remapJar`.

### Forge

| Tool / version | Minecraft versions | Final archives | Status |
| --- | --- | --- | --- |
| ForgeGradle 6.x | Minecraft 1.20.2 through 26.2 | `jar` after `reobfJar`, `sourcesJar` | Supported. Tested by [test/ForgeGradle6](test/ForgeGradle6/build.gradle.kts) with Minecraft 1.20.2 and ForgeGradle 6.0.54. |
| ModDevGradle legacyforge 2.x | Minecraft 1.17 through 1.20.1 | archive-valued `reobfJar`, `sourcesJar` | Supported. Tested by [test/ModDevGradle2LegacyForge](test/ModDevGradle2LegacyForge/build.gradle.kts) with Minecraft 1.20.1 and ModDevGradle 2.0.142. |
| ForgeGradle 3 through 6 | Minecraft 1.13 through 1.20.1 | `jar` after `reobfJar`, `sourcesJar` | Supported. Tested by [test/ForgeGradle5](test/ForgeGradle5/build.gradle.kts) with Minecraft 1.16.5 and ForgeGradle 5.1.77. |
| RetroFuturaGradle 1.x | Minecraft 1.12.2 | archive-valued `reobfJar`, `sourcesJar` | Supported. Tested by [test/RetroFuturaGradle1](test/RetroFuturaGradle1/build.gradle.kts) with RFG 1.4.9. |
| ForgeGradle 2.3 on Gradle 4.x | Minecraft 1.12.2 | `jar` after `reobfJar`, `sourceJar` | Unsupported because ModShade requires Gradle 7.6.4 or newer. |
| ForgeGradle 2.1/2.2 on Gradle 2.x through 4.x | Minecraft 1.8 through 1.12.1 | `jar` after `reobfJar`, `sourceJar` | Unsupported because ModShade requires Gradle 7.6.4 or newer. |
| ForgeGradle 1.x on Gradle 1.x through 2.x | Minecraft 1.7.2 through 1.7.10 | `jar` after `reobf` | Unsupported because ModShade requires Gradle 7.6.4 or newer. |
| MCP/Ant builds | Minecraft 1.6.4 and older | N/A | Unsupported. |
| Custom archive tasks | Project-defined | Project-defined | Use [explicit archive tasks](#explicit-archive-tasks). |

### VanillaGradle, common modules, and plain Java

| Tool / version | Minecraft versions | Final archives | Status |
| --- | --- | --- | --- |
| VanillaGradle 0.3.x | Minecraft 26.1-snapshot-1 through 26.2 | `jar`, `sourcesJar` | Supported. Tested by [test/VanillaGradle03](test/VanillaGradle03/build.gradle.kts) with Minecraft 26.1-snapshot-1 and 26.2, and VanillaGradle 0.3.2. |
| VanillaGradle 0.2.x | Minecraft releases 1.14.4 through 1.21.11; snapshots from 19w36a until before 26.1-snapshot-1 | `jar`, `sourcesJar` | Supported. Tested by [test/VanillaGradle02](test/VanillaGradle02/build.gradle.kts) with Minecraft 19w36a, 1.14.4, and 1.21.11, and VanillaGradle 0.2.2. |
| Java plugin | Not Minecraft-specific | `jar`, `sourcesJar` | Supported by plugin TestKit tests. |
| Custom common remapping build | Project-defined | Project-defined | Use [explicit archive tasks](#explicit-archive-tasks). |

## Archive selection and remapping

ModShade does not remap or reobfuscate Minecraft classes. It shades into the
archive your loader build already produced.

`shadeJar()` chooses the first available final runtime archive:

1. `remapJar`
2. archive-valued `reobfJar`
3. `jar` after a non-archive `reobfJar`
4. `jar`

`shadeSourcesJar()` chooses `remapSourcesJar`, then `sourcesJar`. For local
project dependencies declared with a ModShade dependency configuration, it also
adds and relocates that project's main sources.

For background on named, intermediary, and obfuscated Minecraft names, see
[What are Mappings: An Explainer](https://neoforged.net/personal/sciwhiz12/what-are-mappings/).

## Publishing shaded artifacts

Keep the providers returned by the helpers and wire those into publishing
plugins:

```kotlin
import org.gradle.api.publish.maven.MavenPublication

val shadedJar = modShade.shadeJar()
val shadedSourcesJar = modShade.shadeSourcesJar()

publishMods {
    file.set(shadedJar.flatMap { it.archiveFile })
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(shadedJar)
            artifact(shadedSourcesJar)
            artifact(tasks.named("apiJar")) // Optional API jar; do not shade it.
        }
    }
}
```

`shadeJar()` changes the source runtime archive classifier to `unshaded`.
`shadeSourcesJar()` changes the source sources archive classifier to
`sources-unshaded`. If your build already publishes or uploads `tasks.jar`,
`tasks.remapJar`, `tasks.reobfJar`, or `tasks.sourcesJar`, update that wiring to
use the ModShade task providers instead.

## Explicit archive tasks

Pass archive tasks explicitly when your build uses custom final artifact tasks:

```kotlin
import org.gradle.api.tasks.bundling.AbstractArchiveTask

val shadedJar = modShade.shadeJar(tasks.named<AbstractArchiveTask>("<runtime-archive-task>"))
val shadedSourcesJar = modShade.shadeSourcesJar(tasks.named<AbstractArchiveTask>("<sources-archive-task>"))
```

For additional classified artifacts, name the ModShade tasks yourself:

```kotlin
import org.gradle.api.tasks.bundling.AbstractArchiveTask

val shadedApiJar = modShade.shadeJar(
    "modShadeApiJar",
    tasks.named<AbstractArchiveTask>("<api-archive-task>")
)
shadedApiJar.configure {
    archiveClassifier.set("api")
}
```

Only shade an API/intermediary jar if it intentionally contains implementation
classes. Keep API-only jars unshaded.

## Relocation

By default, ModShade scans shaded dependency jars and relocates package roots
under `<project.group>.modshade`. With `group = "net.example.mod"`, a library
class under `com.foo.lib` is relocated under:

```text
net.example.mod.modshade.com.foo.lib
```

Override the base package:

```kotlin
modShade {
    relocationBase.set("net.example.mod.libs")
}
```

Use explicit rules when inference is too broad, too narrow, or you need stable
compatibility paths:

```kotlin
modShade {
    relocate("com.foo.lib", "net.example.mod.libs.foo")
}
```

Calling `relocate(...)` disables inferred relocation rules and uses only the
rules you declared.

## Excludes

Default excludes remove Maven metadata, invalid signatures, and mod-loader
metadata from shaded dependency contents. Add project-specific excludes with
`exclude(...)`:

```kotlin
modShade {
    exclude("META-INF/services/**")
    exclude("assets/unwanted-library-data/**")
}
```

`exclude(...)` adds to the defaults. `excludes.set(...)` replaces the whole
exclude list.

## Inspecting ModShade configuration

Use `modShadeReport` to see resolved dependency jars, detected mod jars,
relocation rules, exclude patterns, and registered output tasks:

```shell
./gradlew modShadeReport
```

The report is written to `build/reports/modshade/modShadeReport.txt`.

## Contributing

Project development and validation instructions are in [CONTRIBUTING.md](CONTRIBUTING.md).
