package net.mezzdev.modshade.shadow;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import net.mezzdev.modshade.RelocationRule;
import net.mezzdev.modshade.archive.ArchiveEntryMatcher;
import net.mezzdev.modshade.archive.ArchiveEntryMatcher.ArchiveRelocators;
import net.mezzdev.modshade.relocation.ModShadeRelocationPlanner;
import net.mezzdev.modshade.task.ModShadeJar;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Removes ModShade dependency-only excluded entries after Shadow has written
 * the final runtime jar.
 */
public final class RemoveDependencyExcludedEntriesAction implements Action<Task>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Provider<RegularFile> sourceArchiveFile;
    private final FileCollection dependencyFiles;
    private final Property<String> relocationBase;
    private final Provider<List<String>> relocationRules;

    public RemoveDependencyExcludedEntriesAction(
            Provider<RegularFile> sourceArchiveFile,
            FileCollection dependencyFiles,
            Property<String> relocationBase,
            Provider<List<String>> relocationRules
    ) {
        this.sourceArchiveFile = sourceArchiveFile;
        this.dependencyFiles = dependencyFiles;
        this.relocationBase = relocationBase;
        this.relocationRules = relocationRules;
    }

    @Override
    public void execute(Task task) {
        List<String> excludePatterns = ((ModShadeJar) task).getDependencyExcludes().get();
        if (excludePatterns.isEmpty() || !sourceArchiveFile.isPresent()) {
            return;
        }

        AbstractArchiveTask archiveTask = (AbstractArchiveTask) task;
        Path archive = archiveTask.getArchiveFile().get().getAsFile().toPath();
        Path sourceArchive = sourceArchiveFile.get().getAsFile().toPath();
        List<RelocationRule> finalRules = finalRules();
        ArchiveRelocators relocators = ArchiveEntryMatcher.relocators(finalRules);
        try {
            removeExcludedEntries(archive, sourceArchive, excludePatterns, relocators);
        } catch (IOException e) {
            throw new GradleException("Failed to remove excluded ModShade dependency entries from " + archive, e);
        }
    }

    private List<RelocationRule> finalRules() {
        return ModShadeRelocationPlanner.planRelocations(
                        relocationBase.get(),
                        relocationRules.get().stream()
                                .map(ModShadeRelocationPlanner::parseRule)
                                .toList(),
                        dependencyFiles.getFiles()
                )
                .stream()
                .sorted(Comparator.comparingInt((RelocationRule rule) -> rule.fromPackage().length()).reversed())
                .toList();
    }

    private static void removeExcludedEntries(
            Path archive,
            Path sourceArchive,
            List<String> excludePatterns,
            ArchiveRelocators relocators
    ) throws IOException {
        Set<String> sourceEntries = readSourceEntries(sourceArchive, relocators);
        Path tempArchive = Files.createTempFile(archive.getParent(), archive.getFileName().toString(), ".filtered");
        boolean completed = false;
        try {
            try (
                    JarFile input = new JarFile(archive.toFile());
                    JarOutputStream output = new JarOutputStream(Files.newOutputStream(tempArchive))
            ) {
                Enumeration<JarEntry> entries = input.entries();
                while (entries.hasMoreElements()) {
                    JarEntry inputEntry = entries.nextElement();
                    if (inputEntry.isDirectory() || shouldRemove(inputEntry.getName(), sourceEntries, excludePatterns, relocators)) {
                        continue;
                    }

                    JarEntry outputEntry = new JarEntry(inputEntry.getName());
                    outputEntry.setTime(0L);
                    output.putNextEntry(outputEntry);
                    try (InputStream inputStream = input.getInputStream(inputEntry)) {
                        inputStream.transferTo(output);
                    }
                    output.closeEntry();
                }
            }
            Files.move(tempArchive, archive, StandardCopyOption.REPLACE_EXISTING);
            completed = true;
        } finally {
            if (!completed) {
                Files.deleteIfExists(tempArchive);
            }
        }
    }

    private static Set<String> readSourceEntries(Path sourceArchive, ArchiveRelocators relocators) throws IOException {
        Set<String> sourceEntries = new LinkedHashSet<>();
        try (JarFile jarFile = new JarFile(sourceArchive.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    sourceEntries.add(entry.getName());
                    sourceEntries.add(ArchiveEntryMatcher.relocateEntryName(entry.getName(), relocators));
                }
            }
        }
        return sourceEntries;
    }

    private static boolean shouldRemove(
            String entryName,
            Set<String> sourceEntries,
            List<String> excludes,
            ArchiveRelocators relocators
    ) {
        if (sourceEntries.contains(entryName)) {
            return false;
        }

        return ArchiveEntryMatcher.matchesEntry(excludes, entryName, relocators);
    }
}
