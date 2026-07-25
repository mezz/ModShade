# ModShade

ModShade is a Gradle plugin for Minecraft mods that need to shade plain Java
libraries into a mod jar. It keeps the library code present at runtime without
asking players to install extra files, and relocates packages to avoid conflicts
with other mods.

It is not a tool for embedding other Minecraft mods.

## Requirements

ModShade requires Gradle 7.6.4 or newer. Builds using older Gradle versions are unsupported.

## Example build.gradle using ModShade

For supported loader builds, the usual setup is:

```kotlin
plugins {
    java
    id("net.mezzdev.modshade") version "..."
}

dependencies {
    modShade("net.mezzdev:deduplicating-runner:0.1.0")
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

The unshaded jars are kept as diagnostics. The normal runtime and sources
classifiers are produced by the ModShade tasks.

## Version and loader support

The Support column shows the ModShade configuration to use, or says when the
setup is not a ModShade target or is unsupported.

### NeoForge

| Tool / version | Minecraft versions | Archive task | Sources task | Support | Tested by |
| --- | --- | --- | --- | --- | --- |
| ModDevGradle 2.x | Minecraft 1.21 through 26.2 | `jar` | `sourcesJar` | `shadeJar()` & `shadeSourcesJar()` | [test/ModDevGradle2](test/ModDevGradle2/build.gradle.kts), verified with Minecraft 26.2 and ModDevGradle 2.0.142 |
| ModDevGradle 1.x | Minecraft 1.21 through 1.21.11 | `jar` | `sourcesJar` | `shadeJar()` & `shadeSourcesJar()` | [test/ModDevGradle1](test/ModDevGradle1/build.gradle.kts), verified with Minecraft 1.21.1 and ModDevGradle 1.0.24 |
| NeoGradle 7.x | Minecraft 1.20.2 through 26.2 | `jar` | `sourcesJar` | `shadeJar()` & `shadeSourcesJar()` | [test/NeoGradle7](test/NeoGradle7/build.gradle.kts), verified with Minecraft 1.21.1 and NeoGradle 7.1.38 |
| Custom archive tasks | Project-defined | Custom `AbstractArchiveTask` | Custom `AbstractArchiveTask` | [Use explicit archive tasks](#explicit-archive-tasks) | Covered by explicit-archive plugin tests |

### Fabric

| Tool / version | Minecraft versions | Archive task | Sources task | Support | Tested by |
| --- | --- | --- | --- | --- | --- |
| Fabric Loom 1.17.x, `net.fabricmc.fabric-loom` | Minecraft 26.1 through 26.2 | `jar` | `sourcesJar` | `shadeJar()` & `shadeSourcesJar()` | [test/FabricLoom117NonRemap](test/FabricLoom117NonRemap/build.gradle.kts), verified with Minecraft 26.2 and Loom 1.17.12 |
| Fabric Loom 1.17.x, `net.fabricmc.fabric-loom-remap` | Minecraft releases 1.14 through 1.21.11; snapshots from 18w43b until before 26.1 | `remapJar` | `remapSourcesJar` | `shadeJar()` & `shadeSourcesJar()` | [test/FabricLoom117Remap](test/FabricLoom117Remap/build.gradle.kts), verified with Minecraft 1.20.1 and Loom 1.17.12 |
| Fabric Loom 1.17.x intermediary API/common archive tasks | Minecraft releases 1.14 through 1.21.11; snapshots from 18w43b until before 26.1 | Custom `AbstractArchiveTask` | Custom `AbstractArchiveTask` | Not recommended; [Use explicit archive tasks](#explicit-archive-tasks) only when the jar intentionally contains implementation classes | [test/FabricLoom117Remap](test/FabricLoom117Remap/build.gradle.kts), verified with a shaded `api-intermediary` jar |
| Fabric Loom 1.15.3 + Legacy Looming 1.15.3 | Minecraft 1.3 through 1.13.2, plus 1.14 snapshots | `remapJar` | `remapSourcesJar` | `shadeJar()` & `shadeSourcesJar()` | [test/LegacyFabricLoom115](test/LegacyFabricLoom115/build.gradle.kts), verified with Minecraft 1.13.2 |
| Custom archive tasks | Project-defined | Custom `AbstractArchiveTask` | Custom `AbstractArchiveTask` | [Use explicit archive tasks](#explicit-archive-tasks) | Covered by explicit-archive plugin tests |

Fabric's documentation says Loom is version-independent, while official Fabric
support starts at Minecraft release 1.14 and snapshot 18w43b. Legacy Fabric
supports older Minecraft versions as a separate Fabric-based project. For
ModShade, the task split is what matters: Minecraft 26.1+ uses `jar`, and
earlier supported Fabric and Legacy Fabric builds use `remapJar`.

### Forge

| Tool / version | Minecraft versions | Archive task | Sources task | Support | Tested by |
| --- | --- | --- | --- | --- | --- |
| ForgeGradle 6.x | Minecraft 1.20.2 through 26.2 | `jar`, after `reobfJar` runs | `sourcesJar` | `shadeJar()` & `shadeSourcesJar()` | [test/ForgeGradle6](test/ForgeGradle6/build.gradle.kts), verified with Minecraft 1.20.2 and ForgeGradle 6.0.54 |
| ModDevGradle legacyforge 2.x | Minecraft 1.17 through 1.20.1 | archive-valued `reobfJar` | `sourcesJar` | `shadeJar()` & `shadeSourcesJar()` | [test/ModDevGradle2LegacyForge](test/ModDevGradle2LegacyForge/build.gradle.kts), verified with Minecraft 1.20.1 and ModDevGradle 2.0.142 |
| ForgeGradle 3 through 6 | Minecraft 1.13 through 1.20.1 | `jar`, after `reobfJar` runs | `sourcesJar` | `shadeJar()` & `shadeSourcesJar()` | [test/ForgeGradle5](test/ForgeGradle5/build.gradle.kts), verified with Minecraft 1.16.5 and ForgeGradle 5.1.77 |
| RetroFuturaGradle 1.x | Minecraft 1.12.2 | archive-valued `reobfJar` | `sourcesJar` | `shadeJar()` & `shadeSourcesJar()` | [test/RetroFuturaGradle1](test/RetroFuturaGradle1/build.gradle.kts), verified with RFG 1.4.9 |
| ForgeGradle 2.3 on Gradle 4.x | Minecraft 1.12.2 | `jar`, after `reobfJar` runs | `sourceJar` | Unsupported: ModShade requires Gradle 7.6.4 or newer | Not tested |
| ForgeGradle 2.1/2.2 on Gradle 2.x through 4.x | Minecraft 1.8 through 1.12.1 | `jar`, after `reobfJar` runs | `sourceJar` | Unsupported: ModShade requires Gradle 7.6.4 or newer | Not tested |
| ForgeGradle 1.x on Gradle 1.x through 2.x | Minecraft 1.7.2 through 1.7.10 | `jar`, after `reobf` runs | No default source archive task | Unsupported: ModShade requires Gradle 7.6.4 or newer | Not tested |
| MCP/Ant builds | Minecraft 1.6.4 and older | N/A | N/A | Unsupported | Not tested |
| Custom archive tasks | Project-defined | Custom `AbstractArchiveTask` | Custom `AbstractArchiveTask` | [Use explicit archive tasks](#explicit-archive-tasks) | Covered by explicit-archive plugin tests |

### Common, VanillaGradle, and plain Java

| Tool / version | Minecraft versions | Archive task | Sources task | Support | Tested by |
| --- | --- | --- | --- | --- | --- |
| VanillaGradle 0.3.x | Minecraft 26.1-snapshot-1 through 26.2 | `jar` | `sourcesJar` | `shadeJar()` & `shadeSourcesJar()` | [test/VanillaGradle03](test/VanillaGradle03/build.gradle.kts), verified with Minecraft 26.1-snapshot-1 and 26.2, and VanillaGradle 0.3.2 |
| VanillaGradle 0.2.x | Minecraft releases 1.14.4 through 1.21.11; snapshots from 19w36a until before 26.1-snapshot-1 | `jar` | `sourcesJar` | `shadeJar()` & `shadeSourcesJar()` | [test/VanillaGradle02](test/VanillaGradle02/build.gradle.kts), verified with Minecraft 19w36a, 1.14.4, and 1.21.11, and VanillaGradle 0.2.2 |
| Custom common remapping build | Project-defined | Custom `AbstractArchiveTask` | Custom `AbstractArchiveTask` | [Use explicit archive tasks](#explicit-archive-tasks) | Covered by explicit-archive plugin tests |
| Java plugin | Not Minecraft-specific | `jar` | `sourcesJar` | `shadeJar()` & `shadeSourcesJar()` | Plugin TestKit fixtures |

The VanillaGradle split follows VanillaGradle's own target boundaries:
0.2.x targets 19w36a+ plus the 1.14.4 release, and 0.3.x raises the minimum to
26.1-snapshot-1.

### Explicit archive tasks

For rows with custom archive tasks, pass the archive task explicitly.

```kotlin
import org.gradle.api.tasks.bundling.AbstractArchiveTask

modShade {
    shadeJar(tasks.named<AbstractArchiveTask>("<archive-task>"))
    shadeSourcesJar(tasks.named<AbstractArchiveTask>("<sources-archive-task>"))
}
```

For an additional classified artifact such as an API/intermediary jar, give the
ModShade tasks their own names and classifiers:

```kotlin
import org.gradle.api.tasks.bundling.AbstractArchiveTask

modShade {
    shadeJar("modShadeApiJar", tasks.named<AbstractArchiveTask>("<api-archive-task>")).configure {
        archiveClassifier.set("api")
    }
    shadeSourcesJar("modShadeApiSourcesJar", tasks.named<AbstractArchiveTask>("<api-sources-task>")).configure {
        archiveClassifier.set("api-sources")
    }
}
```

## ModShade defaults and how to override them

These are the helper behaviors that can otherwise feel implicit:

| Default feature | What it does | How to override it |
| --- | --- | --- |
| Runtime archive detection | `shadeJar()` chooses `remapJar`, archive-valued `reobfJar`, ForgeGradle `jar` after `reobfJar`, then plain `jar`. | Pass an archive task to `shadeJar(...)`, or register `ModShadeJar` directly. |
| Sources archive detection | `shadeSourcesJar()` chooses `remapSourcesJar`, then `sourcesJar`. Project dependencies declared with `modShade(project(...))` also contribute relocated sources. | Pass a sources archive task to `shadeSourcesJar(...)`, or register `ModShadeSourcesJar` directly. |
| Replacement artifact coordinates | Helpers rename the original archives to `unshaded`/`sources-unshaded`, produce the normal runtime/sources classifiers, and wire outputs into `assemble`. | Configure classifiers yourself, or directly register tasks for separate `-modshade.jar` artifacts. |
| ForgeGradle/RFG ordering | If `shadeJar()` uses `jar` and a non-archive `reobfJar` exists, the ModShade output depends on `reobfJar`. | Pass the exact final archive task and configure ordering yourself. |
| Relocation base | Inferred relocations go under sanitized `<project.group>.modshade`, or `modshade` if the group is missing or invalid. | Set `relocationBase`, for example `relocationBase.set("net.example.libs")`. |
| Relocation inference | Without explicit rules, ModShade scans dependency jars and infers package roots. Calling `relocate(...)` disables inference and uses only your explicit rules. | Add explicit `relocate(...)` rules when inference is not the exact shape you want. |
| Dependency content excludes | Shaded dependency contents exclude Maven metadata, signatures, and Minecraft loader metadata. | Add patterns with `exclude(...)`, or replace the list with `excludes.set(...)`. |
| Reproducible archives | ModShade archives use reproducible file order and do not preserve file timestamps. | Configure the registered archive task with standard Gradle archive properties. |

## Plain libraries only

Use `modShade` for plain Java libraries. Do not use it for Fabric, Forge, or
NeoForge mods. ModShade fails the build if a `modShade` dependency contains
common mod-loader metadata.

Use the loader-supported packaging model for mods or loader-aware dependencies:

- Fabric: use [Loom `include`](https://docs.fabricmc.net/develop/loom/#dependency-configurations)
  or [Fabric nested jars](https://docs.fabricmc.net/develop/loader/#nested-jars),
  or declare a mod dependency.
- Forge: use [ForgeGradle Jar-in-Jar](https://docs.minecraftforge.net/en/fg-6.x/dependencies/jarinjar/),
  or declare a mod dependency.
- NeoForge: use [ModDevGradle `jarJar`](https://docs.neoforged.net/toolchain/docs/plugins/mdg/#jar-in-jar)
  and the NeoForge guidance for
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

## Useful links

- [What are Mappings: An Explainer](https://neoforged.net/personal/sciwhiz12/what-are-mappings/)
- [Fabric FAQ: supported Minecraft versions](https://docs.fabricmc.net/players/faq#what-minecraft-versions-does-fabric-support)
- [Fabric Loom dependency configurations and `include`](https://docs.fabricmc.net/develop/loom/#dependency-configurations)
- [Legacy Fabric](https://legacyfabric.net/)
- [Legacy Fabric example mod](https://github.com/Legacy-Fabric/fabric-example-mod)
- [Fabric nested jars](https://docs.fabricmc.net/develop/loader/#nested-jars)
- [ForgeGradle Jar-in-Jar](https://docs.minecraftforge.net/en/fg-6.x/dependencies/jarinjar/)
- [NeoForge ModDevGradle Jar-in-Jar](https://docs.neoforged.net/toolchain/docs/plugins/mdg/#jar-in-jar)
- [ModDevGradle Legacy Forge Plugin](https://github.com/neoforged/ModDevGradle/blob/main/LEGACY.md)
- [NeoForge NeoGradle](https://github.com/neoforged/NeoGradle)
- [NeoForge non-Minecraft libraries](https://docs.neoforged.net/toolchain/docs/dependencies/nonmclibs/)
- [VanillaGradle](https://github.com/SpongePowered/VanillaGradle)
- [VanillaGradle tasks](https://github.com/SpongePowered/VanillaGradle/wiki/Tasks)

## Shading dependencies

The `modShade` configuration accepts the same dependency notations as normal
Gradle configurations:

```kotlin
dependencies {
    modShade("net.mezzdev:deduplicating-runner:$deduplicatingRunnerVersion")
    modShade(project(":plain-library"))
    modShade(files("libs/local-helper.jar"))
}
```

`modShade` extends Java `compileOnly`, so your mod can compile against these
classes without publishing them as Maven API/runtime dependencies.

Normal Gradle transitive-dependency rules apply. If you only want the direct
artifact, disable transitives in the dependency declaration:

```kotlin
dependencies {
    modShade("com.example:plain-library:1.0") {
        isTransitive = false
    }
}
```

## Advanced usage

Use task types directly if you do not want helper methods. This produces the
same final runtime and sources paths as `shadeJar()` and `shadeSourcesJar()`:

```kotlin
import net.mezzdev.modshade.task.ModShadeJar
import net.mezzdev.modshade.task.ModShadeSourcesJar
import org.gradle.api.tasks.bundling.AbstractArchiveTask

tasks.jar {
    archiveClassifier.set("unshaded")
}

tasks.named<AbstractArchiveTask>("sourcesJar") {
    archiveClassifier.set("sources-unshaded")
}

tasks.register<ModShadeJar>("modShadeJar") {
    fromArchive(tasks.named<AbstractArchiveTask>("jar"))
    archiveClassifier.set("")
}

tasks.register<ModShadeSourcesJar>("modShadeSourcesJar") {
    fromArchive(tasks.named<AbstractArchiveTask>("sourcesJar"))
    archiveClassifier.set("sources")
}

tasks.assemble {
    dependsOn("modShadeJar", "modShadeSourcesJar")
}
```

If you omit the classifier override, `ModShadeJar` uses the normal Gradle
`-modshade` classifier convention.

## Minecraft remapping, reobfuscation, and artifact setup

ModShade does not remap or reobfuscate Minecraft classes. It shades into the
archive your loader build already made: Fabric's remapped jar, Forge's
reobfuscated jar, or the plain `jar` when that is the publishable output.

For the supported setups in the table above, use the basic `shadeJar()` and
`shadeSourcesJar()` setup from the example.

`shadeJar()` detects the common final-runtime archive shapes:

- `remapJar`, used by Fabric Loom remapping builds;
- archive-valued `reobfJar`, used by RetroFuturaGradle and similar builds;
- `jar` after a non-archive `reobfJar`, used by ForgeGradle builds that mutate
  the standard jar;
- plain `jar`, used by non-remapping Fabric Loom, NeoForge ModDevGradle,
  VanillaGradle/common modules, and ordinary Java modules.

`shadeSourcesJar()` detects `remapSourcesJar` first, then `sourcesJar`. For
local project dependencies declared with `modShade(project(":plain-library"))`,
it also adds and relocates that project's main sources.

If the helpers cannot infer your final archive task, pass it explicitly:

```kotlin
import org.gradle.api.tasks.bundling.AbstractArchiveTask

modShade {
    shadeJar(tasks.named<AbstractArchiveTask>("<final-runtime-archive>"))
    shadeSourcesJar(tasks.named<AbstractArchiveTask>("<final-sources-archive>"))
}
```

When publishing to Maven, publish the ModShade runtime jar and sources jar, not
the diagnostic unshaded jars. Keep API jars separate and unshaded:

```kotlin
import org.gradle.api.publish.maven.MavenPublication

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks.named("modShadeJar"))
            artifact(tasks.named("modShadeSourcesJar"))
            artifact(tasks.named("apiJar")) // Optional API jar; do not shade it.
        }
    }
}
```

For background on named, intermediary, and obfuscated Minecraft names, see
[What are Mappings: An Explainer](https://neoforged.net/personal/sciwhiz12/what-are-mappings/).

## Relocation

By default, inferred dependency package roots are relocated under
`<project.group>.modshade`. With `group = "net.mezzdev.config"`, a library class
under `net.foo.lib` is relocated under:

```text
net.mezzdev.config.modshade.net.foo.lib
```

Set `relocationBase` to move all inferred package relocations under a different
base:

```kotlin
modShade {
    relocationBase.set("net.mezzdev.config.libs")
}
```

Use explicit `relocate(...)` rules when inference is too broad, too narrow, or
you need stable compatibility paths:

```kotlin
modShade {
    relocate("net.foo.lib", "net.mezzdev.config.modshade.foo")
}
```

Explicit rules disable inference. If you call `relocate(...)`, ModShade uses
only the rules you declared.

## Excludes

Default excludes remove dependency metadata, invalid signatures, and mod-loader
metadata from shaded dependency contents:

```text
META-INF/maven/**
META-INF/*.SF
META-INF/*.DSA
META-INF/*.RSA
fabric.mod.json
quilt.mod.json
META-INF/mods.toml
META-INF/neoforge.mods.toml
mcmod.info
```

Add project-specific excludes with `exclude(...)`:

```kotlin
modShade {
    exclude("META-INF/services/**")
    exclude("assets/unwanted-library-data/**")
}
```

`exclude(...)` adds to the defaults. `excludes.set(...)` replaces the whole
exclude list and should only be used when you intentionally want to own every
exclude pattern.

## Inspecting ModShade configuration

Use `modShadeReport` to see what ModShade will do:

```shell
./gradlew modShadeReport
```

The report is written to `build/reports/modshade/modShadeReport.txt` and lists
resolved dependency jars, detected mod jars, relocation rules, exclude patterns,
and registered ModShade output tasks.

## Contributing

Project development and validation instructions are in [CONTRIBUTING.md](CONTRIBUTING.md).

The real loader examples live under `test/` as standalone Gradle builds with
their own wrappers. This lets Fabric, NeoForge, and Forge use the Gradle/runtime
versions they actually support while the root `./gradlew test` command still
orchestrates the full verification suite.
