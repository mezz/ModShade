package net.mezzdev.modshade.validation;

import net.mezzdev.modshade.TestFixtures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftModJarDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsFabricModMetadata() throws IOException {
        Path jar = tempDir.resolve("fabric-mod.jar");
        TestFixtures.createJar(jar, List.of("fabric.mod.json"));

        Optional<DetectedModJar> detected = MinecraftModJarDetector.inspect(jar.toFile());

        assertTrue(detected.isPresent());
        assertEquals(List.of("fabric.mod.json"), detected.get().markers());
        assertTrue(detected.get().loaders().contains("Fabric"));
    }

    @Test
    void detectsForgeModMetadata() throws IOException {
        Path jar = tempDir.resolve("forge-mod.jar");
        TestFixtures.createJar(jar, List.of("META-INF/mods.toml"));

        Optional<DetectedModJar> detected = MinecraftModJarDetector.inspect(jar.toFile());

        assertTrue(detected.isPresent());
        assertEquals(List.of("META-INF/mods.toml"), detected.get().markers());
        assertTrue(detected.get().loaders().contains("Forge"));
    }

    @Test
    void detectsNeoForgeModMetadata() throws IOException {
        Path jar = tempDir.resolve("neoforge-mod.jar");
        TestFixtures.createJar(jar, List.of("META-INF/neoforge.mods.toml"));

        Optional<DetectedModJar> detected = MinecraftModJarDetector.inspect(jar.toFile());

        assertTrue(detected.isPresent());
        assertEquals(List.of("META-INF/neoforge.mods.toml"), detected.get().markers());
        assertTrue(detected.get().loaders().contains("NeoForge"));
    }

    @Test
    void detectsQuiltModMetadata() throws IOException {
        Path jar = tempDir.resolve("quilt-mod.jar");
        TestFixtures.createJar(jar, List.of("quilt.mod.json"));

        Optional<DetectedModJar> detected = MinecraftModJarDetector.inspect(jar.toFile());

        assertTrue(detected.isPresent());
        assertEquals(List.of("quilt.mod.json"), detected.get().markers());
        assertTrue(detected.get().loaders().contains("Quilt"));
    }

    @Test
    void detectsLegacyForgeModMetadata() throws IOException {
        Path jar = tempDir.resolve("legacy-forge-mod.jar");
        TestFixtures.createJar(jar, List.of("mcmod.info"));

        Optional<DetectedModJar> detected = MinecraftModJarDetector.inspect(jar.toFile());

        assertTrue(detected.isPresent());
        assertEquals(List.of("mcmod.info"), detected.get().markers());
        assertTrue(detected.get().loaders().contains("Forge"));
    }
}
