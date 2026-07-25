# Contributing

## Validation

Run the full suite from the repository root:

```shell
./gradlew test
```

The root build is an orchestrator. It runs the included `plugin` build's
TestKit suite, then delegates to each loader test build through that build's
own Gradle wrapper.

Forge and RetroFuturaGradle fixtures are intentionally excluded from
configuration-cache validation because those loader plugins are not compatible
with it.

Run only the plugin suite:

```shell
./gradlew pluginTest
```

Run only the loader integrations:

```shell
./gradlew integrationTest
```

## Publishing

Keep Maven Central credentials and signing keys in your local Gradle user
properties, not in this repository:

```properties
# ~/.gradle/gradle.properties
sonatypeUsername=...
sonatypePassword=...
signingKey=...
signingPassword=...
```

CI can provide the same values with `ORG_GRADLE_PROJECT_sonatypeUsername`,
`ORG_GRADLE_PROJECT_sonatypePassword`, `ORG_GRADLE_PROJECT_signingKey`, and
`ORG_GRADLE_PROJECT_signingPassword`.

Run a local dry run before publishing:

```shell
./gradlew -p plugin publishDryRun
```

This writes the Maven repository layout to `plugin/build/repos/dry-run` without
contacting Sonatype. It can run without signing keys; when `signingKey` is
configured, the dry-run repository also includes signature artifacts.

Run publishing from the plugin build:

```shell
./gradlew -p plugin publishToSonatype closeAndReleaseSonatypeStagingRepository
```

## Project layout

The plugin implementation lives in `plugin/`. The root build includes it as a
composite build so tests and standalone loader examples use the plugin id
`net.mezzdev.modshade`.

The real loader examples are separate builds under `test/`.

Each loader build has its own `settings.gradle.kts`, `gradle.properties`, and
Gradle wrapper. This is intentional. Fabric/NeoForge and Forge do not support
the same Gradle/runtime matrix, so forcing them into one Gradle project would
make the tests less realistic and harder to run.

## Integration artifact checks

Each loader `verifyIntegration` task builds and inspects:

- the final runtime jar containing relocated `modShade(project(":Library"))`
  classes;
- a sources jar containing relocated library sources;
- an API jar that does not contain shaded implementation classes;
- the unshaded diagnostic jars produced by the loader/Java tasks.

The checks verify loader metadata is preserved, shaded library classes are
relocated, mod classes reference the relocated classes, original library classes
are absent, sources are relocated, API jars stay free of shaded classes, and
default metadata/signature excludes are applied.
