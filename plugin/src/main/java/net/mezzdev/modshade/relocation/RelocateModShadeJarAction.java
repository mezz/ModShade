package net.mezzdev.modshade.relocation;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import net.mezzdev.modshade.RelocationRule;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
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
 * Relocates bytecode and package-owned resources after the Jar task writes the
 * final ModShade runtime archive.
 */
public final class RelocateModShadeJarAction implements Action<Task>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final String MULTI_RELEASE_PREFIX = "META-INF/versions/";
    private static final String SERVICES_PREFIX = "META-INF/services/";

    private final FileCollection dependencyFiles;
    private final Property<String> relocationBase;
    private final List<RelocationRule> relocationRules;

    public RelocateModShadeJarAction(
            FileCollection dependencyFiles,
            Property<String> relocationBase,
            List<RelocationRule> relocationRules
    ) {
        this.dependencyFiles = dependencyFiles;
        this.relocationBase = relocationBase;
        this.relocationRules = relocationRules;
    }

    @Override
    public void execute(Task task) {
        AbstractArchiveTask archiveTask = (AbstractArchiveTask) task;
        List<InternalRelocationRule> rules = ModShadeRelocationPlanner.planRelocations(
                        relocationBase.get(),
                        relocationRules,
                        dependencyFiles.getFiles()
                )
                .stream()
                .sorted(Comparator.comparingInt((RelocationRule rule) -> rule.fromPackage().length()).reversed())
                .map(InternalRelocationRule::new)
                .toList();
        if (rules.isEmpty()) {
            return;
        }

        Path archive = archiveTask.getArchiveFile().get().getAsFile().toPath();
        Path tempArchive;
        try {
            tempArchive = Files.createTempFile(archive.getParent(), archive.getFileName().toString(), ".relocated");
        } catch (IOException e) {
            throw new GradleException("Failed to create temporary ModShade relocation jar for " + archive, e);
        }

        boolean relocated = false;
        try {
            relocateArchive(archive, tempArchive, rules);
            Files.move(tempArchive, archive, StandardCopyOption.REPLACE_EXISTING);
            relocated = true;
        } catch (IOException e) {
            throw new GradleException("Failed to relocate ModShade jar " + archive, e);
        } finally {
            if (!relocated) {
                try {
                    Files.deleteIfExists(tempArchive);
                } catch (IOException e) {
                    // Keep the original relocation failure as the actionable
                    // Gradle error.
                }
            }
        }
    }

    private static void relocateArchive(Path sourceArchive, Path destinationArchive, List<InternalRelocationRule> rules) throws IOException {
        Set<String> writtenEntries = new LinkedHashSet<>();
        PackagePrefixRemapper remapper = new PackagePrefixRemapper(rules);
        try (
                JarFile input = new JarFile(sourceArchive.toFile());
                JarOutputStream output = new JarOutputStream(Files.newOutputStream(destinationArchive))
        ) {
            Enumeration<JarEntry> entries = input.entries();
            while (entries.hasMoreElements()) {
                JarEntry inputEntry = entries.nextElement();
                if (inputEntry.isDirectory()) {
                    continue;
                }

                String outputEntryName = relocateEntryName(inputEntry.getName(), rules);
                byte[] contents;
                try (InputStream inputStream = input.getInputStream(inputEntry)) {
                    contents = inputStream.readAllBytes();
                }
                if (inputEntry.getName().endsWith(".class")) {
                    contents = relocateClass(contents, remapper);
                } else if (inputEntry.getName().startsWith(SERVICES_PREFIX)) {
                    contents = relocateServiceFile(contents, rules);
                }

                if (!writtenEntries.add(outputEntryName)) {
                    throw new GradleException("Relocation would write duplicate jar entry " + outputEntryName + " in " + sourceArchive);
                }

                JarEntry outputEntry = new JarEntry(outputEntryName);
                outputEntry.setTime(0L);
                output.putNextEntry(outputEntry);
                output.write(contents);
                output.closeEntry();
            }
        }
    }

    private static byte[] relocateClass(byte[] contents, Remapper remapper) {
        ClassReader classReader = new ClassReader(contents);
        ClassWriter classWriter = new ClassWriter(0);
        ClassVisitor classVisitor = new ClassRemapper(classWriter, remapper);
        classReader.accept(classVisitor, 0);
        return classWriter.toByteArray();
    }

    private static String relocateEntryName(String entryName, List<InternalRelocationRule> rules) {
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

    private static byte[] relocateServiceFile(byte[] contents, List<InternalRelocationRule> rules) {
        String text = new String(contents, StandardCharsets.UTF_8);
        return relocateDottedText(text, rules).getBytes(StandardCharsets.UTF_8);
    }

    private static String relocateDottedText(String text, List<InternalRelocationRule> rules) {
        String relocated = text;
        for (InternalRelocationRule rule : rules) {
            relocated = relocated.replace(rule.fromPackageName + ".", rule.toPackageName + ".");
        }
        return relocated;
    }

    private static String relocateDottedName(String dottedName, List<InternalRelocationRule> rules) {
        return relocateInternalName(dottedName.replace('.', '/'), rules).replace('/', '.');
    }

    private static String relocateInternalName(String internalName, List<InternalRelocationRule> rules) {
        for (InternalRelocationRule rule : rules) {
            if (internalName.equals(rule.fromInternalName)) {
                return rule.toInternalName;
            }
            if (internalName.startsWith(rule.fromInternalName + "/")) {
                return rule.toInternalName + internalName.substring(rule.fromInternalName.length());
            }
        }
        return internalName;
    }

    private static final class PackagePrefixRemapper extends Remapper {
        private final List<InternalRelocationRule> rules;

        private PackagePrefixRemapper(List<InternalRelocationRule> rules) {
            this.rules = rules;
        }

        @Override
        public String map(String internalName) {
            return relocateInternalName(internalName, rules);
        }

        @Override
        public String mapPackageName(String name) {
            return relocateInternalName(name, rules);
        }
    }

    private static final class InternalRelocationRule {
        private final String fromPackageName;
        private final String toPackageName;
        private final String fromInternalName;
        private final String toInternalName;

        private InternalRelocationRule(RelocationRule rule) {
            this.fromPackageName = rule.fromPackage();
            this.toPackageName = rule.toPackage();
            this.fromInternalName = rule.fromPackage().replace('.', '/');
            this.toInternalName = rule.toPackage().replace('.', '/');
        }
    }
}
