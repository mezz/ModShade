package net.mezzdev.modshade.shadow;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Restores loader nested-jar entries after Shadow has processed the source archive.
 */
public final class PreserveNestedJarEntriesAction implements Action<Task>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Provider<RegularFile> sourceArchiveFile;

    public PreserveNestedJarEntriesAction(Provider<RegularFile> sourceArchiveFile) {
        this.sourceArchiveFile = sourceArchiveFile;
    }

    @Override
    public void execute(Task task) {
        if (!sourceArchiveFile.isPresent()) {
            return;
        }

        AbstractArchiveTask archiveTask = (AbstractArchiveTask) task;
        Path archive = archiveTask.getArchiveFile().get().getAsFile().toPath();
        Path sourceArchive = sourceArchiveFile.get().getAsFile().toPath();
        try {
            preserveNestedJarEntries(archive, sourceArchive);
        } catch (IOException e) {
            throw new GradleException("Failed to preserve nested jar entries from " + sourceArchive + " in " + archive, e);
        }
    }

    private static void preserveNestedJarEntries(Path archive, Path sourceArchive) throws IOException {
        Path tempArchive = Files.createTempFile(archive.getParent(), archive.getFileName().toString(), ".nested-jars");
        boolean completed = false;
        try {
            Set<String> writtenEntries = new LinkedHashSet<>();
            try (
                    JarFile input = new JarFile(archive.toFile());
                    JarFile sourceInput = new JarFile(sourceArchive.toFile());
                    JarOutputStream output = new JarOutputStream(Files.newOutputStream(tempArchive))
            ) {
                copyExistingEntries(input, output, writtenEntries);
                copyNestedJarEntries(sourceInput, output, writtenEntries);
            }
            Files.move(tempArchive, archive, StandardCopyOption.REPLACE_EXISTING);
            completed = true;
        } finally {
            if (!completed) {
                Files.deleteIfExists(tempArchive);
            }
        }
    }

    private static void copyExistingEntries(
            JarFile input,
            JarOutputStream output,
            Set<String> writtenEntries
    ) throws IOException {
        Enumeration<JarEntry> entries = input.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory() || isNestedJarEntry(entry.getName()) || !writtenEntries.add(entry.getName())) {
                continue;
            }

            copyEntry(input, entry, output);
        }
    }

    private static void copyNestedJarEntries(
            JarFile sourceInput,
            JarOutputStream output,
            Set<String> writtenEntries
    ) throws IOException {
        Enumeration<JarEntry> entries = sourceInput.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory() || !isNestedJarEntry(entry.getName()) || !writtenEntries.add(entry.getName())) {
                continue;
            }

            copyEntry(sourceInput, entry, output);
        }
    }

    private static void copyEntry(JarFile input, JarEntry inputEntry, JarOutputStream output) throws IOException {
        JarEntry outputEntry = new JarEntry(inputEntry.getName());
        outputEntry.setTime(0L);
        output.putNextEntry(outputEntry);
        try (InputStream inputStream = input.getInputStream(inputEntry)) {
            inputStream.transferTo(output);
        }
        output.closeEntry();
    }

    private static boolean isNestedJarEntry(String entryName) {
        return entryName.endsWith(".jar")
                && (entryName.startsWith("META-INF/jarjar/") || entryName.startsWith("META-INF/jars/"));
    }
}
