package net.mezzdev.modshade.validation;

import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Detects Minecraft mod jars by looking for known loader metadata.
 */
public final class MinecraftModJarDetector {
    private static final Map<String, String> MOD_MARKERS = createModMarkers();

    private MinecraftModJarDetector() {
    }

    public static List<DetectedModJar> findModJars(Collection<File> files) {
        List<DetectedModJar> detected = new ArrayList<>();
        for (File file : files) {
            inspect(file).ifPresent(detected::add);
        }
        return detected;
    }

    public static Optional<DetectedModJar> inspect(File file) {
        if (!isJarFile(file)) {
            return Optional.empty();
        }

        List<String> markers = new ArrayList<>();
        Set<String> loaders = new TreeSet<>();
        try (ZipFile zipFile = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                Optional.ofNullable(MOD_MARKERS.get(entry.getName())).ifPresent(loader -> {
                    markers.add(entry.getName());
                    loaders.add(loader);
                });
            }
        } catch (IOException e) {
            throw new GradleException("Failed to inspect modShade dependency jar: " + file, e);
        }

        if (markers.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DetectedModJar(file, markers, loaders));
    }

    public static String buildFailureMessage(List<DetectedModJar> detectedModJars) {
        StringBuilder message = new StringBuilder();
        message.append("ModShade is for shading plain implementation-only libraries into Minecraft mods, ");
        message.append("not for embedding Minecraft mods inside other mods.\n\n");
        message.append("The modShade configuration contains Minecraft mod jar(s):\n");
        for (DetectedModJar detected : detectedModJars) {
            message.append(" - ");
            message.append(detected.file().getName());
            message.append(" (");
            message.append(detected.getLoaderSummary());
            message.append("; metadata: ");
            message.append(String.join(", ", detected.markers()));
            message.append(")\n");
        }
        message.append("\nUse the loader-supported packaging model instead:\n");
        message.append(" - Fabric: use Loom include(...) or nested jars, or declare a normal mod dependency.\n");
        message.append(" - Forge/NeoForge: use JarJar/jar-in-jar metadata, or declare a normal mod dependency.\n");
        message.append(" - Plain libraries: depend on a non-mod library artifact with no loader metadata.\n");
        return message.toString();
    }

    private static boolean isJarFile(File file) {
        return file.isFile() && file.getName().endsWith(".jar");
    }

    private static Map<String, String> createModMarkers() {
        Map<String, String> markers = new LinkedHashMap<>();
        markers.put("fabric.mod.json", "Fabric");
        markers.put("quilt.mod.json", "Quilt");
        markers.put("META-INF/mods.toml", "Forge");
        markers.put("META-INF/neoforge.mods.toml", "NeoForge");
        markers.put("mcmod.info", "Forge");
        return markers;
    }
}
