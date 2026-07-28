package net.mezzdev.modshade;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModShadeDependencyConfigurationFunctionalTest extends ModShadeFunctionalTestSupport {
    @Test
    void modShadeImplementationConfigurationMatchesImplementationClasspathWithoutPublishingDependencies() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        Files.writeString(tempDir.resolve("settings.gradle.kts"), "rootProject.name = \"simple-mod\"\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("build.gradle.kts"), """
                plugins {
                    java
                    `maven-publish`
                    id("net.mezzdev.modshade")
                }

                group = "com.example"
                version = "1.0"

                repositories {
                    maven {
                        url = uri("%s")
                    }
                }

                dependencies {
                    modShadeImplementation("net.mezzdev.fixture:library:1.0")
                }

                abstract class AssertImplementationModShadeClasspath : DefaultTask() {
                    @get:Input
                    abstract val modShadeImplementationCanBeResolved: org.gradle.api.provider.Property<Boolean>

                    @get:Input
                    abstract val modShadeCompileOnlyCanBeResolved: org.gradle.api.provider.Property<Boolean>

                    @get:Input
                    abstract val modShadeRuntimeOnlyCanBeResolved: org.gradle.api.provider.Property<Boolean>

                    @get:Input
                    abstract val modShadeClasspathCanBeResolved: org.gradle.api.provider.Property<Boolean>

                    @get:Classpath
                    abstract val compileClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val testCompileClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val runtimeClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val testRuntimeClasspath: ConfigurableFileCollection

                    @TaskAction
                    fun assertClasspath() {
                        check(!modShadeImplementationCanBeResolved.get())
                        check(!modShadeCompileOnlyCanBeResolved.get())
                        check(!modShadeRuntimeOnlyCanBeResolved.get())
                        check(modShadeClasspathCanBeResolved.get())
                        check(compileClasspath.files.any { it.name == "library-1.0.jar" })
                        check(testCompileClasspath.files.any { it.name == "library-1.0.jar" })
                        check(runtimeClasspath.files.any { it.name == "library-1.0.jar" })
                        check(testRuntimeClasspath.files.any { it.name == "library-1.0.jar" })
                    }
                }

                tasks.register<AssertImplementationModShadeClasspath>("assertImplementationModShadeClasspath") {
                    modShadeImplementationCanBeResolved.set(configurations.named("modShadeImplementation").map { it.isCanBeResolved })
                    modShadeCompileOnlyCanBeResolved.set(configurations.named("modShadeCompileOnly").map { it.isCanBeResolved })
                    modShadeRuntimeOnlyCanBeResolved.set(configurations.named("modShadeRuntimeOnly").map { it.isCanBeResolved })
                    modShadeClasspathCanBeResolved.set(configurations.named("modShadeClasspath").map { it.isCanBeResolved })
                    compileClasspath.from(configurations.named("compileClasspath"))
                    testCompileClasspath.from(configurations.named("testCompileClasspath"))
                    runtimeClasspath.from(configurations.named("runtimeClasspath"))
                    testRuntimeClasspath.from(configurations.named("testRuntimeClasspath"))
                }

                modShade {
                    shadeJar()
                }

                publishing {
                    publications {
                        create<MavenPublication>("mavenJava") {
                            from(components["java"])
                        }
                    }
                    repositories {
                        maven {
                            url = layout.buildDirectory.dir("published").get().asFile.toURI()
                        }
                    }
                }
                """.formatted(repo.toUri()), StandardCharsets.UTF_8);
        TestFixtures.writeJavaSource(
                tempDir.resolve("src/main/java/com/example/mod/ExampleMod.java"),
                """
                        package com.example.mod;

                        import net.mezzdev.fixture.library.FixtureLibrary;

                        public final class ExampleMod {
                            public String value() {
                                return FixtureLibrary.value();
                            }
                        }
                        """
        );
        Files.createDirectories(tempDir.resolve("src/main/resources"));
        Files.writeString(
                tempDir.resolve("src/main/resources/fabric.mod.json"),
                "{\"schemaVersion\":1,\"id\":\"simple_mod\",\"version\":\"1.0.0\"}",
                StandardCharsets.UTF_8
        );

        BuildResult result = gradle(
                "assertImplementationModShadeClasspath",
                "modShadeJar",
                "publish",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":assertImplementationModShadeClasspath")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":publish")).getOutcome());
        Path shadedJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(shadedJar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);

        Path pom = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.pom");
        String pomText = Files.readString(pom, StandardCharsets.UTF_8);
        assertFalse(pomText.contains("net.mezzdev.fixture"));
        assertFalse(pomText.contains("library"));
    }

    @Test
    void additionalRuntimeClasspathReceivesRuntimeVisibleModShadeConfigurations() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "implementation-library", "1.0");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "compile-library", "1.0");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "runtime-library", "1.0");
        Files.writeString(tempDir.resolve("settings.gradle.kts"), "rootProject.name = \"simple-mod\"\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("net.mezzdev.modshade")
                }

                repositories {
                    maven {
                        url = uri("%s")
                    }
                }

                configurations.create("additionalRuntimeClasspath") {
                    isCanBeResolved = true
                    isCanBeConsumed = false
                }

                dependencies {
                    modShadeImplementation("net.mezzdev.fixture:implementation-library:1.0")
                    modShadeCompileOnly("net.mezzdev.fixture:compile-library:1.0")
                    modShadeRuntimeOnly("net.mezzdev.fixture:runtime-library:1.0")
                }

                abstract class AssertAdditionalRuntimeClasspath : DefaultTask() {
                    @get:Classpath
                    abstract val additionalRuntimeClasspath: ConfigurableFileCollection

                    @TaskAction
                    fun assertClasspath() {
                        val names = additionalRuntimeClasspath.files.map { it.name }.toSet()
                        check("implementation-library-1.0.jar" in names)
                        check("runtime-library-1.0.jar" in names)
                        check("compile-library-1.0.jar" !in names)
                    }
                }

                tasks.register<AssertAdditionalRuntimeClasspath>("assertAdditionalRuntimeClasspath") {
                    additionalRuntimeClasspath.from(configurations.named("additionalRuntimeClasspath"))
                }
                """.formatted(repo.toUri()), StandardCharsets.UTF_8);

        BuildResult result = gradle(
                "assertAdditionalRuntimeClasspath",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":assertAdditionalRuntimeClasspath")).getOutcome());
    }

    @Test
    void modShadeCompileOnlyConfigurationShadesWithoutRuntimeClasspath() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        Files.writeString(tempDir.resolve("settings.gradle.kts"), "rootProject.name = \"simple-mod\"\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("build.gradle.kts"), """
                plugins {
                    java
                    `maven-publish`
                    id("net.mezzdev.modshade")
                }

                group = "com.example"
                version = "1.0"

                repositories {
                    maven {
                        url = uri("%s")
                    }
                }

                dependencies {
                    modShadeCompileOnly("net.mezzdev.fixture:library:1.0")
                }

                abstract class AssertCompileOnlyModShadeClasspath : DefaultTask() {
                    @get:Classpath
                    abstract val compileClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val testCompileClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val runtimeClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val testRuntimeClasspath: ConfigurableFileCollection

                    @TaskAction
                    fun assertClasspath() {
                        check(compileClasspath.files.any { it.name == "library-1.0.jar" })
                        check(testCompileClasspath.files.any { it.name == "library-1.0.jar" })
                        check(runtimeClasspath.files.none { it.name == "library-1.0.jar" })
                        check(testRuntimeClasspath.files.none { it.name == "library-1.0.jar" })
                    }
                }

                tasks.register<AssertCompileOnlyModShadeClasspath>("assertCompileOnlyModShadeClasspath") {
                    compileClasspath.from(configurations.named("compileClasspath"))
                    testCompileClasspath.from(configurations.named("testCompileClasspath"))
                    runtimeClasspath.from(configurations.named("runtimeClasspath"))
                    testRuntimeClasspath.from(configurations.named("testRuntimeClasspath"))
                }

                modShade {
                    shadeJar()
                }

                publishing {
                    publications {
                        create<MavenPublication>("mavenJava") {
                            from(components["java"])
                        }
                    }
                    repositories {
                        maven {
                            url = layout.buildDirectory.dir("published").get().asFile.toURI()
                        }
                    }
                }
                """.formatted(repo.toUri()), StandardCharsets.UTF_8);
        TestFixtures.writeJavaSource(
                tempDir.resolve("src/main/java/com/example/mod/ExampleMod.java"),
                """
                        package com.example.mod;

                        import net.mezzdev.fixture.library.FixtureLibrary;

                        public final class ExampleMod {
                            public String value() {
                                return FixtureLibrary.value();
                            }
                        }
                        """
        );
        Files.createDirectories(tempDir.resolve("src/main/resources"));
        Files.writeString(
                tempDir.resolve("src/main/resources/fabric.mod.json"),
                "{\"schemaVersion\":1,\"id\":\"simple_mod\",\"version\":\"1.0.0\"}",
                StandardCharsets.UTF_8
        );

        BuildResult result = gradle(
                "assertCompileOnlyModShadeClasspath",
                "modShadeJar",
                "publish",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":assertCompileOnlyModShadeClasspath")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":publish")).getOutcome());
        Path shadedJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(shadedJar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);

        Path pom = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.pom");
        String pomText = Files.readString(pom, StandardCharsets.UTF_8);
        assertFalse(pomText.contains("net.mezzdev.fixture"));
        assertFalse(pomText.contains("library"));
    }

    @Test
    void modShadeRuntimeOnlyConfigurationShadesWithoutCompileClasspath() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        Files.writeString(tempDir.resolve("settings.gradle.kts"), "rootProject.name = \"simple-mod\"\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("build.gradle.kts"), """
                plugins {
                    java
                    `maven-publish`
                    id("net.mezzdev.modshade")
                }

                group = "com.example"
                version = "1.0"

                repositories {
                    maven {
                        url = uri("%s")
                    }
                }

                dependencies {
                    modShadeRuntimeOnly("net.mezzdev.fixture:library:1.0")
                }

                abstract class AssertRuntimeOnlyModShadeClasspath : DefaultTask() {
                    @get:Classpath
                    abstract val compileClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val testCompileClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val runtimeClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val testRuntimeClasspath: ConfigurableFileCollection

                    @TaskAction
                    fun assertClasspath() {
                        check(compileClasspath.files.none { it.name == "library-1.0.jar" })
                        check(testCompileClasspath.files.none { it.name == "library-1.0.jar" })
                        check(runtimeClasspath.files.any { it.name == "library-1.0.jar" })
                        check(testRuntimeClasspath.files.any { it.name == "library-1.0.jar" })
                    }
                }

                tasks.register<AssertRuntimeOnlyModShadeClasspath>("assertRuntimeOnlyModShadeClasspath") {
                    compileClasspath.from(configurations.named("compileClasspath"))
                    testCompileClasspath.from(configurations.named("testCompileClasspath"))
                    runtimeClasspath.from(configurations.named("runtimeClasspath"))
                    testRuntimeClasspath.from(configurations.named("testRuntimeClasspath"))
                }

                modShade {
                    shadeJar()
                }

                publishing {
                    publications {
                        create<MavenPublication>("mavenJava") {
                            from(components["java"])
                        }
                    }
                    repositories {
                        maven {
                            url = layout.buildDirectory.dir("published").get().asFile.toURI()
                        }
                    }
                }
                """.formatted(repo.toUri()), StandardCharsets.UTF_8);
        TestFixtures.writeJavaSource(
                tempDir.resolve("src/main/java/com/example/mod/ExampleMod.java"),
                """
                        package com.example.mod;

                        public final class ExampleMod {
                            public String value() {
                                return "fixture";
                            }
                        }
                        """
        );
        Files.createDirectories(tempDir.resolve("src/main/resources"));
        Files.writeString(
                tempDir.resolve("src/main/resources/fabric.mod.json"),
                "{\"schemaVersion\":1,\"id\":\"simple_mod\",\"version\":\"1.0.0\"}",
                StandardCharsets.UTF_8
        );

        BuildResult result = gradle(
                "assertRuntimeOnlyModShadeClasspath",
                "modShadeJar",
                "publish",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":assertRuntimeOnlyModShadeClasspath")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":publish")).getOutcome());
        Path shadedJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_LIBRARY_CLASS);

        Path pom = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.pom");
        String pomText = Files.readString(pom, StandardCharsets.UTF_8);
        assertFalse(pomText.contains("net.mezzdev.fixture"));
        assertFalse(pomText.contains("library"));
    }
}
