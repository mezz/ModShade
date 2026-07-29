package net.mezzdev.modshade.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.mezzdev.modshade.RelocationRule;
import net.mezzdev.modshade.archive.ArchiveEntryMatcher;
import net.mezzdev.modshade.archive.ArchiveEntryMatcher.ArchiveRelocators;
import net.mezzdev.modshade.relocation.ModShadeRelocationPlanner;
import net.mezzdev.modshade.validation.DetectedModJar;
import net.mezzdev.modshade.validation.MinecraftModJarDetector;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Writes a diagnostic report for the current ModShade configuration.
 */
@CacheableTask
public abstract class ModShadeReportTask extends DefaultTask {
    @Classpath
    public abstract ConfigurableFileCollection getDependencyFiles();

    @Input
    public abstract Property<String> getRelocationBase();

    @Input
    public abstract ListProperty<String> getExplicitRelocations();

    @Input
    public abstract ListProperty<String> getExcludes();

    @Input
    public abstract ListProperty<String> getModShadeJarTasks();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void writeReport() {
        String report = buildReport();
        Path reportFile = getReportFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(reportFile.getParent());
            Files.writeString(reportFile, report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write ModShade report: " + reportFile, e);
        }
        getLogger().lifecycle(report);
    }

    private String buildReport() {
        Set<File> dependencyFiles = new TreeSet<>(getDependencyFiles().getFiles());
        List<DetectedModJar> detectedModJars = MinecraftModJarDetector.findModJars(dependencyFiles);
        List<RelocationRule> explicitRules = getExplicitRelocations().get().stream()
                .map(ModShadeRelocationPlanner::parseRule)
                .toList();
        List<RelocationRule> finalRules = ModShadeRelocationPlanner.planRelocations(
                getRelocationBase().get(),
                explicitRules,
                dependencyFiles
        );

        StringBuilder report = new StringBuilder();
        report.append("ModShade report\n");
        appendConfiguration(report, getRelocationBase().get());
        appendValues(report, "Output tasks", getModShadeJarTasks().get());
        appendValues(report, "Dependencies", dependencyFiles.stream().map(File::getName).toList());
        appendValues(report, "Detected mod jars", detectedModJars.stream().map(DetectedModJar::file).map(File::getName).toList());
        appendValues(report, "Explicit relocations", getExplicitRelocations().get());
        appendValues(report, "Final relocations", finalRules.stream().map(ModShadeRelocationPlanner::formatRule).toList());
        appendValues(report, "Excludes", getExcludes().get());
        appendDependencyResourcesIncluded(report, dependencyFiles, finalRules);
        return report.toString();
    }

    private static void appendConfiguration(StringBuilder report, String relocationBase) {
        report.append("Configuration:\n");
        report.append(" - Mod jar shading: disallowed\n");
        report.append(" - Relocation base: ").append(relocationBase).append('\n');
    }

    private void appendDependencyResourcesIncluded(
            StringBuilder report,
            Set<File> dependencyFiles,
            List<RelocationRule> relocationRules
    ) {
        report.append("Dependency resources included:\n");
        if (dependencyFiles.isEmpty()) {
            report.append(" - <none>\n");
            return;
        }

        List<String> excludes = getExcludes().get();
        ArchiveRelocators relocators = ArchiveEntryMatcher.relocators(relocationRules);
        boolean hasIncludedResources = false;
        for (File dependencyFile : dependencyFiles) {
            List<String> includedResources = dependencyResourcesIncluded(dependencyFile, excludes, relocators);
            if (!includedResources.isEmpty()) {
                hasIncludedResources = true;
                report.append("Resources included from ").append(dependencyFile.getName()).append(":\n");
                appendValues(report, includedResources);
            }
        }
        if (!hasIncludedResources) {
            report.append(" - <none>\n");
        }
    }

    private static List<String> dependencyResourcesIncluded(
            File dependencyFile,
            List<String> excludes,
            ArchiveRelocators relocators
    ) {
        if (!dependencyFile.isFile()) {
            return List.of();
        }

        List<String> resources = new ArrayList<>();
        try (JarFile jarFile = new JarFile(dependencyFile)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (!entry.isDirectory() && isReportableResource(entryName) && shouldIncludeResource(entryName, excludes, relocators)) {
                    resources.add(entryName);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to inspect dependency resources in " + dependencyFile, e);
        }
        return resources.stream()
                .sorted()
                .toList();
    }

    private static boolean isReportableResource(String entryName) {
        return !entryName.endsWith(".class") && !"META-INF/MANIFEST.MF".equals(entryName);
    }

    private static boolean shouldIncludeResource(
            String entryName,
            List<String> excludes,
            ArchiveRelocators relocators
    ) {
        return !ArchiveEntryMatcher.matchesEntry(excludes, entryName, relocators);
    }

    private static void appendValues(StringBuilder report, String heading, Iterable<?> values) {
        report.append(heading).append(":\n");
        appendValues(report, values);
    }

    private static void appendValues(StringBuilder report, Iterable<?> values) {
        boolean hasValues = false;
        for (Object value : values) {
            hasValues = true;
            report.append(" - ").append(value).append('\n');
        }
        if (!hasValues) {
            report.append(" - <none>\n");
        }
    }
}
