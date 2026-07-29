# ModShade

ModShade is a Gradle plugin for Minecraft mods that need to shade plain Java
libraries into a mod jar. It relocates shaded packages to avoid conflicts with
other mods, and keeps those libraries out of published Maven dependency
metadata.

ModShade is not for embedding Fabric, Forge, NeoForge, or other Minecraft mods.
For those, use your loader's nested-jar support or declare a mod dependency.

## Requirements

ModShade requires Gradle 8.3 or newer. Run Gradle with Java 17 or newer.
Runtime jar shading uses [Shadow](https://gradleup.com/shadow/), so future
Shadow upgrades can raise the Gradle requirement.

ModShade tasks support Gradle's configuration cache. Some loader plugins may
still disable it.

## Quick start

```kotlin
plugins {
    java
    id("net.mezzdev.modshade") version "0.4.0"
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
`unshaded` jars are the original loader output and are kept for debugging.

## Migrating common Shadow setups to ModShade

ModShade builds on Shadow for runtime jar shading, but moves the mod-specific
parts into the `modShade` extension and dependency configurations. These are
common Shadow patterns and their ModShade equivalents. The examples use Kotlin
DSL; add the usual type imports for `ShadowJar` and `AbstractArchiveTask` as
needed.

Shade a library dependency:

```kotlin
// Shadow
dependencies {
    implementation("net.mezzdev:deduplicating-runner:0.1.0")
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(project.configurations.runtimeClasspath.get())
}
```

```kotlin
// ModShade
dependencies {
    modShadeImplementation("net.mezzdev:deduplicating-runner:0.1.0")
}

modShade {
    shadeJar()
    shadeSourcesJar()
}
```

Shade into a loader-produced archive:

```kotlin
// Shadow
tasks.named<ShadowJar>("shadowJar") {
    val remapJar = tasks.named<AbstractArchiveTask>("remapJar")
    from(zipTree(remapJar.flatMap { it.archiveFile }))
    archiveClassifier.set("")
}
```

```kotlin
// ModShade
import org.gradle.api.tasks.bundling.AbstractArchiveTask

modShade {
    shadeJar(tasks.named<AbstractArchiveTask>("remapJar"))
}
```

For default Fabric, Forge, NeoForge, VanillaGradle, and Java plugin setups,
`shadeJar()` chooses the final runtime archive automatically. `ModShadeJar`
also has Shadow's regular `from(...)` API, but `fromArchive(...)` is the
ModShade API for selecting the mod archive that should receive shaded
dependencies.

Relocate packages:

```kotlin
// Shadow
tasks.named<ShadowJar>("shadowJar") {
    relocate("com.example.library", "net.example.mod.libs.example")
}
```

```kotlin
// ModShade
modShade {
    relocate("com.example.library", "net.example.mod.libs.example")
}
```

If explicit relocation rules are not needed, ModShade can infer dependency
package roots and relocate them under the project group, or under a configured
base package:

```kotlin
modShade {
    relocationBase.set("net.example.mod.libs")
}
```

Minimize shaded dependencies:

```kotlin
// Shadow
tasks.named<ShadowJar>("shadowJar") {
    minimize {
        exclude(dependency("com.example:reflective-library:.*"))
    }
}
```

```kotlin
// ModShade
val shadedJar = modShade.shadeJar()

shadedJar.configure {
    minimize {
        exclude(dependency("com.example:reflective-library:.*"))
    }
}
```

## Dependency configurations

Every ModShade dependency configuration means "bundle this library into the
shaded jar instead of publishing it as an external dependency." The suffix only
controls where the original, unrelocated library appears on local development
classpaths before the shaded jar exists.

| Configuration | Local compile classpath | Local runtime/test runtime classpath |
| --- | --- | --- |
| `modShadeImplementation(...)` | Yes | Yes |
| `modShadeCompileOnly(...)` | Yes | No |
| `modShadeRuntimeOnly(...)` | No | Yes |

Most mods should use `modShadeImplementation(...)`.

Use `modShadeCompileOnly(...)` when local runs and tests should not see the
original library. Use `modShadeRuntimeOnly(...)` for libraries loaded
reflectively or through a service loader.

ModDevGradle's `additionalRuntimeClasspath` is wired automatically, because MDG
dev runs load mods from class directories before `modShadeJar` has relocated
dependencies.

For other loader-specific or custom run configurations that do not use Java
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

Transitive dependencies are included by Gradle's normal resolution rules. Use
normal Gradle excludes or `isTransitive = false` when a dependency should not be
resolved for shading at all.

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

There is no ModShade opt-out for shading mod jars. If an artifact has stale
loader metadata but is actually intended to be a plain library, publish or
depend on a classifier/variant that does not contain that metadata.

## Minimization

Use `minimize()` only when jar size matters. It removes dependency classes that
are not statically reachable from your mod classes:

```kotlin
val shadedJar = modShade.shadeJar()
shadedJar.configure {
    minimize()
}
```

Keep dependencies that are loaded reflectively or are otherwise invisible to
static bytecode analysis:

```kotlin
val shadedJar = modShade.shadeJar()
shadedJar.configure {
    minimize {
        exclude(dependency("com.example:reflective-library:.*"))
        exclude(project(":runtime-helper"))
    }
}
```

ModShade delegates runtime jar minimization to
[Shadow's minimizer](https://gradleup.com/shadow/configuration/minimizing/).
Dependency keep patterns use `group:name:version`; each part is a regular
expression. Keeping a dependency or project also keeps its resolved dependency
subtree. `minimize()` cannot detect arbitrary `Class.forName(...)` strings or
loader-specific reflection.

`minimize()` trims classes. It does not prove arbitrary dependency resources are
unused, so resources remain unless you remove them with ModShade `exclude(...)`.

## Loader support

For supported rows, use the quick-start `shadeJar()` and `shadeSourcesJar()`
setup unless the row says to use explicit archive tasks.

### NeoForge

| Tool / version | Minecraft versions | Final archives | Status |
| --- | --- | --- | --- |
| ModDevGradle 2.x | Minecraft 1.21 through 26.2 | `jar` with configured `jarJar` inputs, `sourcesJar` | Supported — [test/ModDevGradle2](test/ModDevGradle2/build.gradle.kts) verifies Minecraft 26.2 and ModDevGradle 2.0.142. |
| ModDevGradle 1.x | Minecraft 1.21 through 1.21.11 | `jar` with configured `jarJar` inputs, `sourcesJar` | Supported — [test/ModDevGradle1](test/ModDevGradle1/build.gradle.kts) verifies Minecraft 1.21.1 and ModDevGradle 1.0.24. |
| NeoGradle 7.x | Minecraft 1.20.2 through 26.2 | `jar`, configured `jarJar`, `sourcesJar` | Supported — [test/NeoGradle7](test/NeoGradle7/build.gradle.kts) verifies Minecraft 1.21.1 and NeoGradle 7.1.38. |
| Custom archive tasks | Project-defined | Project-defined | Use [explicit archive tasks](#explicit-archive-tasks). |

### Fabric

| Tool / version | Minecraft versions | Final archives | Status |
| --- | --- | --- | --- |
| Fabric Loom 1.17.x, `net.fabricmc.fabric-loom` | Minecraft 26.1 through 26.2 | `jar` with configured `include` nested jars, `sourcesJar` | Supported — [test/FabricLoom117NonRemap](test/FabricLoom117NonRemap/build.gradle.kts) verifies Minecraft 26.2 and Loom 1.17.12. |
| Fabric Loom 1.17.x, `net.fabricmc.fabric-loom-remap` | Minecraft releases 1.14 through 1.21.11; snapshots from 18w43b until before 26.1 | `remapJar` with configured `include` nested jars, `remapSourcesJar` | Supported — [test/FabricLoom117Remap](test/FabricLoom117Remap/build.gradle.kts) verifies Minecraft 1.20.1 and Loom 1.17.12. |
| Fabric Loom 1.17.x intermediary API/common archives | Minecraft releases 1.14 through 1.21.11; snapshots from 18w43b until before 26.1 | Project-defined | Not recommended for API-only jars. Use [explicit archive tasks](#explicit-archive-tasks) only if the jar intentionally contains implementation classes. Covered by [test/FabricLoom117Remap](test/FabricLoom117Remap/build.gradle.kts). |
| Fabric Loom 1.15.3 + Legacy Looming 1.15.3 | Minecraft 1.3 through 1.13.2, plus 1.14 snapshots | `remapJar` with configured `include` nested jars, `remapSourcesJar` | Supported — [test/LegacyFabricLoom115](test/LegacyFabricLoom115/build.gradle.kts) verifies Minecraft 1.13.2. |
| Custom archive tasks | Project-defined | Project-defined | Use [explicit archive tasks](#explicit-archive-tasks). |

Fabric support starts at Minecraft release 1.14 and snapshot 18w43b. Older
versions use [Legacy Fabric](https://legacyfabric.net/). For ModShade, the
important difference is the archive task: Minecraft 26.1+ publishes `jar`, while
earlier Fabric and Legacy Fabric builds publish `remapJar`.

### Forge

| Tool / version | Minecraft versions | Final archives | Status |
| --- | --- | --- | --- |
| ForgeGradle 6.x | Minecraft 1.20.2 through 26.2 | configured `jarJar` after `reobfJarJar`, otherwise `jar` after `reobfJar`, `sourcesJar` | Supported — [test/ForgeGradle6](test/ForgeGradle6/build.gradle.kts) verifies Minecraft 1.20.2 and ForgeGradle 6.0.54. |
| ModDevGradle legacyforge 2.x | Minecraft 1.17 through 1.20.1 | archive-valued `reobfJar` with configured `jarJar` inputs, `sourcesJar` | Supported — [test/ModDevGradle2LegacyForge](test/ModDevGradle2LegacyForge/build.gradle.kts) verifies Minecraft 1.20.1 and ModDevGradle 2.0.142. |
| ForgeGradle 3 through 5 | Minecraft 1.13 through 1.20.1 | `jar` after `reobfJar`, `sourcesJar` | Unsupported on Gradle versions older than 8.3. Unknown on Gradle 8.3 or newer. |
| RetroFuturaGradle 1.x | Minecraft 1.12.2 | archive-valued `reobfJar`, `sourcesJar` | Supported — [test/RetroFuturaGradle1](test/RetroFuturaGradle1/build.gradle.kts) verifies RFG 1.4.9. |
| ForgeGradle 2.3 on Gradle 4.x | Minecraft 1.12.2 | `jar` after `reobfJar`, `sourceJar` | Unsupported because ModShade requires Gradle 8.3 or newer. |
| ForgeGradle 2.1/2.2 on Gradle 2.x through 4.x | Minecraft 1.8 through 1.12.1 | `jar` after `reobfJar`, `sourceJar` | Unsupported because ModShade requires Gradle 8.3 or newer. |
| ForgeGradle 1.x on Gradle 1.x through 2.x | Minecraft 1.7.2 through 1.7.10 | `jar` after `reobf` | Unsupported because ModShade requires Gradle 8.3 or newer. |
| MCP/Ant builds | Minecraft 1.6.4 and older | N/A | Unsupported. |
| Custom archive tasks | Project-defined | Project-defined | Use [explicit archive tasks](#explicit-archive-tasks). |

### VanillaGradle, common modules, and plain Java

| Tool / version | Minecraft versions | Final archives | Status |
| --- | --- | --- | --- |
| VanillaGradle 0.3.x | Minecraft 26.1-snapshot-1 through 26.2 | `jar`, `sourcesJar` | Supported — [test/VanillaGradle03](test/VanillaGradle03/build.gradle.kts) verifies Minecraft 26.1-snapshot-1 and 26.2 with VanillaGradle 0.3.2. |
| VanillaGradle 0.2.x | Minecraft releases 1.14.4 through 1.21.11; snapshots from 19w36a until before 26.1-snapshot-1 | `jar`, `sourcesJar` | Supported — [test/VanillaGradle02](test/VanillaGradle02/build.gradle.kts) verifies Minecraft 19w36a, 1.14.4, and 1.21.11 with VanillaGradle 0.2.2. |
| Java plugin | Not Minecraft-specific | `jar`, `sourcesJar` | Supported by plugin TestKit tests. |
| Custom common remapping build | Project-defined | Project-defined | Use [explicit archive tasks](#explicit-archive-tasks). |

## Archive selection and remapping

ModShade does not remap or reobfuscate Minecraft classes. It shades into the
archive your loader build already produced.

`shadeJar()` chooses the first available final runtime archive:

1. `remapJar`
2. archive-valued `reobfJar`
3. `jarJar`, when a `jarJar` configuration has declared dependencies
4. `jar` after a non-archive `reobfJar`
5. `jar`

This lets NeoGradle and ForgeGradle builds combine ModShade relocation with
archive-valued jar-in-jar packaging: `jar` builds the plain mod, `jarJar` adds
nested mod jars, and `modShadeJar` relocates plain ModShade dependencies into
that jarJar output. For ForgeGradle, ModShade also waits for a matching
`reobfJarJar` task when ForgeGradle registers one. When this path is used,
ModShade moves the intermediate plain `jar` to a `plain-unshaded` classifier if
it would otherwise collide with the final shaded jar or the unshaded jarJar
archive.

ModDevGradle's `jarJar` task is not a final archive task. It generates
jar-in-jar metadata and nested jar inputs that ModDevGradle adds to `jar` (or to
the archive-valued `reobfJar` for legacy Forge). ModShade therefore shades the
ModDevGradle final archive after those inputs are present.

Fabric Loom's equivalent is the `include` configuration. Loom nests those jars
into `jar` or `remapJar`; ModShade shades that final archive after Loom's
nesting step. If `jarJar` exists but has no declared dependencies, ModShade
ignores it and uses the next available final archive.

`shadeSourcesJar()` chooses `remapSourcesJar`, then `sourcesJar`. For local
project dependencies declared with a ModShade dependency configuration, it also
adds and relocates that project's main sources.

For background on named, intermediary, and obfuscated Minecraft names, see
[What are Mappings: An Explainer](https://neoforged.net/personal/sciwhiz12/what-are-mappings/).

## Publishing shaded artifacts

Keep the providers returned by `shadeJar()` and `shadeSourcesJar()`. File
upload plugins usually want the shaded runtime jar directly:

```kotlin
val shadedJar = modShade.shadeJar()

publishMods {
    file.set(shadedJar.flatMap { it.archiveFile })
}
```

For Maven publishing, publish the normal Java component. When `shadeJar()` is
used, ModShade replaces the Java component's normal runtime variant with the
shaded runtime variant. When `shadeSourcesJar()` is used, it similarly replaces
the normal sources variant with relocated shaded sources. This keeps other
variants or artifacts added to the Java component by loader plugins available
for publication:

```kotlin
import org.gradle.api.publish.maven.MavenPublication

modShade.shadeJar()
modShade.shadeSourcesJar()

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(tasks.named("apiJar")) // Optional API jar; do not shade it.
        }
    }
}
```

ModShade dependencies are already inside the shaded jars. Do not also publish
them as external Maven dependencies.

`from(components["java"])` publishes the shaded runtime jar as the runtime
variant while preserving the Java component for Gradle metadata and for other
plugins that add their own publication variants.

The older `from(components["modShade"])` component is still available for
compatibility, but it only contains ModShade's own shaded runtime and sources
variants. Prefer `components["java"]` so loader-plugin publication additions are
not dropped.

The integration builds publish to local test repositories and verify both the
published jars and Gradle/Maven metadata. The checks assert that the shaded
runtime and relocated sources are the published artifacts, normal
`runtimeElements`/`sourcesElements` are skipped, and ModShade or nested-jar
implementation dependencies are not leaked as Maven/Gradle metadata
dependencies.

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

Default excludes remove Maven metadata and invalid signatures from shaded
dependency contents. Add project-specific excludes with `exclude(...)`:

```kotlin
modShade {
    exclude("assets/unwanted-library-data/**")
    exclude("META-INF/unwanted-library-file.txt")
}
```

`exclude(...)` is shorthand for `excludes.add(...)`: it keeps the current list
and appends one pattern. `excludes.set(...)` replaces the entire list, including
defaults and any patterns added earlier. If you use `set`, include every pattern
you still want. If you call `exclude(...)` after `excludes.set(...)`, it appends
to that replacement list.

The default excludes are intentionally narrow:

| Pattern | Why it is excluded by default |
| --- | --- |
| `META-INF/maven/**` | Maven coordinates and `pom.properties` describe the original library artifact. After shading, that metadata is stale and can confuse scanners or runtime introspection. |
| `META-INF/*.SF` | JAR signature metadata is invalid after unpacking, relocating, and repacking classes. |
| `META-INF/*.DSA` | Same as `.SF`: signature block data no longer matches the shaded jar. |
| `META-INF/*.RSA` | Same as `.SF`: signature block data no longer matches the shaded jar. |

To replace the defaults completely:

```kotlin
modShade {
    excludes.set(emptyList())
}
```

Or remove one default while keeping the rest:

```kotlin
import net.mezzdev.modshade.ModShadeExtension

modShade {
    excludes.set(ModShadeExtension.DEFAULT_EXCLUDES.filterNot { it == "META-INF/maven/**" })
}
```

## Inspecting ModShade configuration

Use `modShadeReport` to see resolved dependency jars, detected mod jars,
relocation rules, exclude patterns, registered output tasks, and the
dependency resources that remain after excludes:

```shell
./gradlew modShadeReport
```

The report is written to `build/reports/modshade/modShadeReport.txt`.

## Contributing

Project development and validation instructions are in [CONTRIBUTING.md](CONTRIBUTING.md).
