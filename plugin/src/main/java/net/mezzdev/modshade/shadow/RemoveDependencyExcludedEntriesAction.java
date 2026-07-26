package net.mezzdev.modshade.shadow;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import net.mezzdev.modshade.RelocationRule;
import net.mezzdev.modshade.relocation.ModShadeRelocationPlanner;

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
import java.util.regex.Pattern;
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

    private static final String MULTI_RELEASE_PREFIX = "META-INF/versions/";
    private static final String SERVICES_PREFIX = "META-INF/services/";

    private final Provider<RegularFile> sourceArchiveFile;
    private final ListProperty<String> excludes;
    private final FileCollection dependencyFiles;
    private final Property<String> relocationBase;
    private final Provider<List<String>> relocationRules;

    public RemoveDependencyExcludedEntriesAction(
            Provider<RegularFile> sourceArchiveFile,
            ListProperty<String> excludes,
            FileCollection dependencyFiles,
            Property<String> relocationBase,
            Provider<List<String>> relocationRules
    ) {
        this.sourceArchiveFile = sourceArchiveFile;
        this.excludes = excludes;
        this.dependencyFiles = dependencyFiles;
        this.relocationBase = relocationBase;
        this.relocationRules = relocationRules;
    }

    @Override
    public void execute(Task task) {
        List<String> excludePatterns = excludes.get();
        if (excludePatterns.isEmpty() || !sourceArchiveFile.isPresent()) {
            return;
        }

        AbstractArchiveTask archiveTask = (AbstractArchiveTask) task;
        Path archive = archiveTask.getArchiveFile().get().getAsFile().toPath();
        Path sourceArchive = sourceArchiveFile.get().getAsFile().toPath();
        List<RelocationRule> finalRules = finalRules();
        try {
            removeExcludedEntries(archive, sourceArchive, excludePatterns, finalRules);
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
            List<RelocationRule> relocationRules
    ) throws IOException {
        Set<String> sourceEntries = readSourceEntries(sourceArchive, relocationRules);
        List<Pattern> excludes = excludePatterns.stream()
                .map(RemoveDependencyExcludedEntriesAction::compileGlob)
                .toList();
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
                    if (inputEntry.isDirectory() || shouldRemove(inputEntry.getName(), sourceEntries, excludes, relocationRules)) {
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

    private static Set<String> readSourceEntries(Path sourceArchive, List<RelocationRule> relocationRules) throws IOException {
        Set<String> sourceEntries = new LinkedHashSet<>();
        try (JarFile jarFile = new JarFile(sourceArchive.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    sourceEntries.add(entry.getName());
                    sourceEntries.add(relocateEntryName(entry.getName(), relocationRules));
                }
            }
        }
        return sourceEntries;
    }

    private static boolean shouldRemove(
            String entryName,
            Set<String> sourceEntries,
            List<Pattern> excludes,
            List<RelocationRule> relocationRules
    ) {
        if (sourceEntries.contains(entryName)) {
            return false;
        }

        if (matchesAny(excludes, entryName)) {
            return true;
        }

        String unrelocatedEntryName = unrelocateEntryName(entryName, relocationRules);
        return !entryName.equals(unrelocatedEntryName) && matchesAny(excludes, unrelocatedEntryName);
    }

    private static boolean matchesAny(List<Pattern> patterns, String entryName) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(entryName).matches()) {
                return true;
            }
        }
        return false;
    }

    private static String relocateEntryName(String entryName, List<RelocationRule> rules) {
        if (entryName.startsWith(SERVICES_PREFIX)) {
            return SERVICES_PREFIX + relocateDottedName(entryName.substring(SERVICES_PREFIX.length()), rules);
        }

        String normalizedEntryName = normalizeMultiReleaseEntryName(entryName);
        String relocatedEntryName = relocateInternalName(normalizedEntryName, rules);
        if (normalizedEntryName.equals(relocatedEntryName)) {
            return entryName;
        }
        return entryName.substring(0, entryName.length() - normalizedEntryName.length()) + relocatedEntryName;
    }

    private static String unrelocateEntryName(String entryName, List<RelocationRule> rules) {
        if (entryName.startsWith(SERVICES_PREFIX)) {
            return SERVICES_PREFIX + unrelocateDottedName(entryName.substring(SERVICES_PREFIX.length()), rules);
        }

        String normalizedEntryName = normalizeMultiReleaseEntryName(entryName);
        String unrelocatedEntryName = unrelocateInternalName(normalizedEntryName, rules);
        if (normalizedEntryName.equals(unrelocatedEntryName)) {
            return entryName;
        }
        return entryName.substring(0, entryName.length() - normalizedEntryName.length()) + unrelocatedEntryName;
    }

    private static String normalizeMultiReleaseEntryName(String entryName) {
        if (!entryName.startsWith(MULTI_RELEASE_PREFIX)) {
            return entryName;
        }

        String remainder = entryName.substring(MULTI_RELEASE_PREFIX.length());
        int slash = remainder.indexOf('/');
        if (slash < 0) {
            return entryName;
        }
        return remainder.substring(slash + 1);
    }

    private static String relocateDottedName(String dottedName, List<RelocationRule> rules) {
        return relocateInternalName(dottedName.replace('.', '/'), rules).replace('/', '.');
    }

    private static String unrelocateDottedName(String dottedName, List<RelocationRule> rules) {
        return unrelocateInternalName(dottedName.replace('.', '/'), rules).replace('/', '.');
    }

    private static String relocateInternalName(String internalName, List<RelocationRule> rules) {
        for (RelocationRule rule : rules) {
            String fromInternalName = rule.fromPackage().replace('.', '/');
            if (internalName.equals(fromInternalName)) {
                return rule.toPackage().replace('.', '/');
            }
            if (internalName.startsWith(fromInternalName + "/")) {
                return rule.toPackage().replace('.', '/') + internalName.substring(fromInternalName.length());
            }
        }
        return internalName;
    }

    private static String unrelocateInternalName(String internalName, List<RelocationRule> rules) {
        for (RelocationRule rule : rules) {
            String toInternalName = rule.toPackage().replace('.', '/');
            if (internalName.equals(toInternalName)) {
                return rule.fromPackage().replace('.', '/');
            }
            if (internalName.startsWith(toInternalName + "/")) {
                return rule.fromPackage().replace('.', '/') + internalName.substring(toInternalName.length());
            }
        }
        return internalName;
    }

    private static Pattern compileGlob(String pattern) {
        StringBuilder regex = new StringBuilder();
        regex.append('^');
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append("[^/]");
            } else {
                if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                    regex.append('\\');
                }
                regex.append(c);
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }
}
