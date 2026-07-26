plugins {
    base
    `java-base`
}

val javaToolchains = extensions.getByType<JavaToolchainService>()
val java17Launcher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(17))
}

val pluginTest = tasks.register("pluginTest") {
    group = "verification"
    description = "Runs the ModShade plugin unit and TestKit suite."
    dependsOn(gradle.includedBuild("plugin").task(":test"))
}

val fabricLoom117RemapIntegrationTest = registerLoaderIntegrationTest(
    taskName = "fabricLoom117RemapIntegrationTest",
    projectDir = layout.projectDirectory.dir("test/FabricLoom117Remap"),
    configurationCache = true,
)

val fabricLoom117NonRemapIntegrationTest = registerLoaderIntegrationTest(
    taskName = "fabricLoom117NonRemapIntegrationTest",
    projectDir = layout.projectDirectory.dir("test/FabricLoom117NonRemap"),
    configurationCache = true,
)

val legacyFabricLoom115IntegrationTest = registerLoaderIntegrationTest(
    taskName = "legacyFabricLoom115IntegrationTest",
    projectDir = layout.projectDirectory.dir("test/LegacyFabricLoom115"),
    configurationCache = true,
)

val forgeGradle6IntegrationTest = registerLoaderIntegrationTest(
    taskName = "forgeGradle6IntegrationTest",
    projectDir = layout.projectDirectory.dir("test/ForgeGradle6"),
    configurationCache = false,
)

val retroFuturaGradle1IntegrationTest = registerLoaderIntegrationTest(
    taskName = "retroFuturaGradle1IntegrationTest",
    projectDir = layout.projectDirectory.dir("test/RetroFuturaGradle1"),
    configurationCache = false,
    javaLauncher = java17Launcher,
)

val modDevGradle2IntegrationTest = registerLoaderIntegrationTest(
    taskName = "modDevGradle2IntegrationTest",
    projectDir = layout.projectDirectory.dir("test/ModDevGradle2"),
    configurationCache = true,
)

val modDevGradle1IntegrationTest = registerLoaderIntegrationTest(
    taskName = "modDevGradle1IntegrationTest",
    projectDir = layout.projectDirectory.dir("test/ModDevGradle1"),
    configurationCache = true,
)

val modDevGradle2LegacyForgeIntegrationTest = registerLoaderIntegrationTest(
    taskName = "modDevGradle2LegacyForgeIntegrationTest",
    projectDir = layout.projectDirectory.dir("test/ModDevGradle2LegacyForge"),
    configurationCache = false,
)

val neoGradle7IntegrationTest = registerLoaderIntegrationTest(
    taskName = "neoGradle7IntegrationTest",
    projectDir = layout.projectDirectory.dir("test/NeoGradle7"),
    configurationCache = true,
)

val vanillaGradle03IntegrationTest = registerLoaderIntegrationTest(
    taskName = "vanillaGradle03IntegrationTest",
    projectDir = layout.projectDirectory.dir("test/VanillaGradle03"),
    configurationCache = true,
)

val vanillaGradle03Minecraft261Snapshot1IntegrationTest = registerLoaderIntegrationTest(
    taskName = "vanillaGradle03Minecraft261Snapshot1IntegrationTest",
    projectDir = layout.projectDirectory.dir("test/VanillaGradle03"),
    configurationCache = true,
    gradleArguments = listOf(
        "-PvanillaMinecraftVersion=26.1-snapshot-1",
        "-PmodJavaVersion=25",
    ),
)

val vanillaGradle02IntegrationTest = registerLoaderIntegrationTest(
    taskName = "vanillaGradle02IntegrationTest",
    projectDir = layout.projectDirectory.dir("test/VanillaGradle02"),
    configurationCache = true,
)

val vanillaGradle02Minecraft1144IntegrationTest = registerLoaderIntegrationTest(
    taskName = "vanillaGradle02Minecraft1144IntegrationTest",
    projectDir = layout.projectDirectory.dir("test/VanillaGradle02"),
    configurationCache = true,
    gradleArguments = listOf(
        "-PvanillaMinecraftVersion=1.14.4",
        "-PmodJavaVersion=17",
    ),
)

val vanillaGradle02Minecraft19w36aIntegrationTest = registerLoaderIntegrationTest(
    taskName = "vanillaGradle02Minecraft19w36aIntegrationTest",
    projectDir = layout.projectDirectory.dir("test/VanillaGradle02"),
    configurationCache = true,
    gradleArguments = listOf(
        "-PvanillaMinecraftVersion=19w36a",
        "-PmodJavaVersion=17",
    ),
)

val loaderIntegrationTests = listOf(
    fabricLoom117RemapIntegrationTest,
    fabricLoom117NonRemapIntegrationTest,
    legacyFabricLoom115IntegrationTest,
    retroFuturaGradle1IntegrationTest,
    forgeGradle6IntegrationTest,
    modDevGradle2IntegrationTest,
    modDevGradle1IntegrationTest,
    modDevGradle2LegacyForgeIntegrationTest,
    neoGradle7IntegrationTest,
    vanillaGradle03IntegrationTest,
    vanillaGradle03Minecraft261Snapshot1IntegrationTest,
    vanillaGradle02IntegrationTest,
    vanillaGradle02Minecraft1144IntegrationTest,
    vanillaGradle02Minecraft19w36aIntegrationTest,
)

loaderIntegrationTests.forEach { integrationTask ->
    integrationTask.configure {
        mustRunAfter(pluginTest)
    }
}

loaderIntegrationTests.zipWithNext().forEach { (previous, next) ->
    next.configure {
        mustRunAfter(previous)
    }
}

val integrationTest = tasks.register("integrationTest") {
    group = "verification"
    description = "Runs the real loader integration builds through their own Gradle wrappers."
    dependsOn(loaderIntegrationTests)
}

tasks.register("test") {
    group = "verification"
    description = "Runs plugin tests and real loader integration verification."
    dependsOn(pluginTest, integrationTest)
}

tasks.named("check") {
    dependsOn("test")
}

fun registerLoaderIntegrationTest(
    taskName: String,
    projectDir: Directory,
    configurationCache: Boolean,
    gradleArguments: List<String> = emptyList(),
    javaLauncher: Provider<JavaLauncher>? = null,
): TaskProvider<Exec> = tasks.register<Exec>(taskName) {
    group = "verification"
    description = "Runs ${projectDir.asFile.name} with its own Gradle wrapper."
    workingDir(projectDir)

    val executableName = if (isWindows()) "gradlew.bat" else "gradlew"
    val wrapper = projectDir.file(executableName).asFile.absolutePath

    val arguments = mutableListOf("verifyIntegration", "--no-daemon", "--stacktrace")
    if (configurationCache) {
        arguments += listOf("--configuration-cache", "--configuration-cache-problems=fail")
    } else {
        arguments += "--no-configuration-cache"
    }
    arguments += gradleArguments

    if (javaLauncher != null) {
        inputs.property(
            "wrapperJavaHome",
            javaLauncher.map { it.metadata.installationPath.asFile.absolutePath },
        )
        doFirst {
            environment("JAVA_HOME", javaLauncher.get().metadata.installationPath.asFile.absolutePath)
        }
    }

    commandLine(wrapper, *arguments.toTypedArray())
}

fun isWindows(): Boolean =
    System.getProperty("os.name").contains("windows", ignoreCase = true)
