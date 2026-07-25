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
import net.mezzdev.modshade.relocation.ModShadeRelocationPlanner;
import net.mezzdev.modshade.validation.DetectedModJar;
import net.mezzdev.modshade.validation.MinecraftModJarDetector;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
    public abstract Property<Boolean> getFailOnModJars();

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
        appendValues(report, "Output tasks", getModShadeJarTasks().get());
        appendValues(report, "Dependencies", dependencyFiles.stream().map(File::getName).toList());
        appendValues(report, "Detected mod jars", detectedModJars.stream().map(DetectedModJar::file).map(File::getName).toList());
        report.append("Relocation base: ").append(getRelocationBase().get()).append('\n');
        appendValues(report, "Explicit relocations", getExplicitRelocations().get());
        appendValues(report, "Final relocations", finalRules.stream().map(ModShadeRelocationPlanner::formatRule).toList());
        appendValues(report, "Excludes", getExcludes().get());
        report.append("Fail on mod jars: ").append(getFailOnModJars().get()).append('\n');
        return report.toString();
    }

    private static void appendValues(StringBuilder report, String heading, Iterable<?> values) {
        report.append(heading).append(":\n");
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
