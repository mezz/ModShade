package net.mezzdev.modshade;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TestFixtures {
    private TestFixtures() {
    }

    public static void publishLibrary(Path repo, String group, String artifact, String version) throws IOException {
        publishLibrary(repo, group, artifact, version, List.of());
    }

    public static void publishLibrary(Path repo, String group, String artifact, String version, List<String> extraTextEntries) throws IOException {
        Path workDir = Files.createTempDirectory("modshade-fixture-library");
        Path source = workDir.resolve("src/net/mezzdev/fixture/library/FixtureLibrary.java");
        writeJavaSource(source, """
                package net.mezzdev.fixture.library;

                public final class FixtureLibrary {
                    public static String value() {
                        return "fixture";
                    }
                }
                """);

        Path classesDir = workDir.resolve("classes");
        Files.createDirectories(classesDir);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK with the system Java compiler is required for these tests.");
        }
        int result = compiler.run(null, null, null, "-d", classesDir.toString(), source.toString());
        if (result != 0) {
            throw new IllegalStateException("Failed to compile fixture library source.");
        }

        Path moduleDir = repo.resolve(group.replace('.', '/')).resolve(artifact).resolve(version);
        Files.createDirectories(moduleDir);
        Path jar = moduleDir.resolve(artifact + "-" + version + ".jar");
        try (JarOutputStream jarOutput = new JarOutputStream(Files.newOutputStream(jar))) {
            addFileToJar(jarOutput, classesDir, classesDir.resolve("net/mezzdev/fixture/library/FixtureLibrary.class"));
            addTextEntry(jarOutput, "META-INF/maven/" + group + "/" + artifact + "/pom.properties", "version=" + version);
            addTextEntry(jarOutput, "META-INF/TEST.SF", "Signature-Version: 1.0");
            for (String extraTextEntry : extraTextEntries) {
                addTextEntry(jarOutput, extraTextEntry, "{}");
            }
        }

        Files.writeString(
                moduleDir.resolve(artifact + "-" + version + ".pom"),
                """
                        <project xmlns="http://maven.apache.org/POM/4.0.0"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>%s</groupId>
                            <artifactId>%s</artifactId>
                            <version>%s</version>
                        </project>
                        """.formatted(group, artifact, version),
                StandardCharsets.UTF_8
        );
    }

    public static void createJar(Path jar, List<String> entries) throws IOException {
        Files.createDirectories(jar.getParent());
        try (JarOutputStream jarOutput = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String entry : entries) {
                addTextEntry(jarOutput, entry, "{}");
            }
        }
    }

    public static void createJar(Path jar, Path root, List<String> fileEntries, Map<String, String> textEntries) throws IOException {
        Files.createDirectories(jar.getParent());
        try (JarOutputStream jarOutput = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String fileEntry : fileEntries) {
                addFileToJar(jarOutput, root, root.resolve(fileEntry));
            }
            for (Map.Entry<String, String> textEntry : textEntries.entrySet()) {
                addTextEntry(jarOutput, textEntry.getKey(), textEntry.getValue());
            }
        }
    }

    public static void compileJavaSources(Path classesDir, List<Path> sources, List<Path> classpath) throws IOException {
        Files.createDirectories(classesDir);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK with the system Java compiler is required for these tests.");
        }

        List<String> arguments = new ArrayList<>();
        arguments.add("-d");
        arguments.add(classesDir.toString());
        if (!classpath.isEmpty()) {
            arguments.add("-classpath");
            arguments.add(String.join(File.pathSeparator, classpath.stream()
                    .map(Path::toString)
                    .toList()));
        }
        arguments.addAll(sources.stream()
                .map(Path::toString)
                .toList());

        int result = compiler.run(null, null, null, arguments.toArray(String[]::new));
        if (result != 0) {
            throw new IllegalStateException("Failed to compile fixture sources.");
        }
    }

    public static void writeJavaSource(Path source, String sourceText) throws IOException {
        Files.createDirectories(source.getParent());
        Files.writeString(source, sourceText, StandardCharsets.UTF_8);
    }

    public static void copyDirectory(Path source, Path destination) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path sourcePath : paths.sorted(Comparator.naturalOrder()).toList()) {
                Path destinationPath = destination.resolve(materializedFixturePath(source.relativize(sourcePath)));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(destinationPath);
                } else {
                    Files.createDirectories(destinationPath.getParent());
                    Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static Path materializedFixturePath(Path relativePath) {
        Path fileName = relativePath.getFileName();
        if (fileName == null) {
            return relativePath;
        }

        String fileNameText = fileName.toString();
        if (!fileNameText.endsWith(".fixture")) {
            return relativePath;
        }

        String materializedFileName = fileNameText.substring(0, fileNameText.length() - ".fixture".length());
        Path parent = relativePath.getParent();
        if (parent == null) {
            return Path.of(materializedFileName);
        }
        return parent.resolve(materializedFileName);
    }

    public static void assertJarContains(Path jar, String entryName) throws IOException {
        try (ZipFile zipFile = new ZipFile(jar.toFile())) {
            assertNotNull(zipFile.getEntry(entryName), () -> jar + " should contain " + entryName);
        }
    }

    public static void assertJarDoesNotContain(Path jar, String entryName) throws IOException {
        try (ZipFile zipFile = new ZipFile(jar.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
			assertNull(entry, () -> jar + " should not contain " + entryName);
        }
    }

    public static void assertJarEntryCount(Path jar, String entryName, int expectedCount) throws IOException {
        int count = 0;
        try (ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    count++;
                }
            }
        }
        assertEquals(expectedCount, count, () -> jar + " should contain " + expectedCount + " entries named " + entryName);
    }

    public static void assertManifestAttribute(Path jar, String attributeName, String expectedValue) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            java.util.jar.Manifest manifest = jarFile.getManifest();
            assertNotNull(manifest, () -> jar + " should contain META-INF/MANIFEST.MF");
            assertEquals(
                    expectedValue,
                    manifest.getMainAttributes().getValue(attributeName),
                    () -> jar + " manifest attribute " + attributeName
            );
        }
    }

    public static void assertJarEntryContains(Path jar, String entryName, String expectedText) throws IOException {
        String entryText = readJarEntryText(jar, entryName);
		assertTrue(entryText.contains(expectedText), () -> entryName + " should contain " + expectedText);
    }

    public static void assertJarEntryDoesNotContain(Path jar, String entryName, String unexpectedText) throws IOException {
        String entryText = readJarEntryText(jar, entryName);
		assertFalse(entryText.contains(unexpectedText), () -> entryName + " should not contain " + unexpectedText);
    }

    public static void assertClassReferences(Path jar, String classEntryName, String internalClassName) throws IOException {
        Set<String> references = readClassReferences(jar, classEntryName);
		assertTrue(references.contains(internalClassName), () -> classEntryName + " should reference " + internalClassName);
    }

    public static void assertClassDoesNotReference(Path jar, String classEntryName, String internalClassName) throws IOException {
        Set<String> references = readClassReferences(jar, classEntryName);
		assertFalse(references.contains(internalClassName), () -> classEntryName + " should not reference " + internalClassName);
    }

    private static Set<String> readClassReferences(Path jar, String classEntryName) throws IOException {
        try (ZipFile zipFile = new ZipFile(jar.toFile())) {
            ZipEntry entry = zipFile.getEntry(classEntryName);
            assertNotNull(entry, () -> jar + " should contain " + classEntryName);
            return readClassReferences(zipFile.getInputStream(entry).readAllBytes());
        }
    }

    private static String readJarEntryText(Path jar, String entryName) throws IOException {
        try (ZipFile zipFile = new ZipFile(jar.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            assertNotNull(entry, () -> jar + " should contain " + entryName);
            return new String(zipFile.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Set<String> readClassReferences(byte[] classBytes) {
        Set<String> references = new LinkedHashSet<>();
        ClassReader reader = new ClassReader(classBytes);
        ClassVisitor collector = new ReferenceCollectingClassRemapper(
                new TraversalVisitor(),
                new ReferenceCollectingRemapper(references)
        );
        reader.accept(collector, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return references;
    }

    private static final class ReferenceCollectingClassRemapper extends ClassRemapper {
        private ReferenceCollectingClassRemapper(ClassVisitor classVisitor, Remapper remapper) {
            super(Opcodes.ASM9, classVisitor, remapper);
        }
    }

    private static final class TraversalVisitor extends ClassVisitor {
        private TraversalVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public FieldVisitor visitField(
                int access,
                String name,
                String descriptor,
                String signature,
                Object value
        ) {
            return new FieldVisitor(Opcodes.ASM9) {
            };
        }

        @Override
        public MethodVisitor visitMethod(
                int access,
                String name,
                String descriptor,
                String signature,
                String[] exceptions
        ) {
            return new MethodVisitor(Opcodes.ASM9) {
            };
        }
    }

    private static final class ReferenceCollectingRemapper extends Remapper {
        private final Set<String> references;

        private ReferenceCollectingRemapper(Set<String> references) {
            super(Opcodes.ASM9);
            this.references = references;
        }

        @Override
        public String map(String internalName) {
            addReference(internalName);
            return internalName;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            addReference(owner);
            addReference(name);
            return name;
        }

        @Override
        public String mapInvokeDynamicMethodName(
                String name,
                String descriptor,
                Handle bootstrapMethodHandle,
                Object... bootstrapMethodArguments
        ) {
            addReference(name);
            return name;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            addReference(owner);
            addReference(name);
            return name;
        }

        private void addReference(String reference) {
            if (reference != null) {
                references.add(reference);
            }
        }
    }

    private static void addFileToJar(JarOutputStream jarOutput, Path root, Path file) throws IOException {
        JarEntry entry = new JarEntry(root.relativize(file).toString().replace('\\', '/'));
        jarOutput.putNextEntry(entry);
        Files.copy(file, jarOutput);
        jarOutput.closeEntry();
    }

    private static void addTextEntry(JarOutputStream jarOutput, String name, String value) {
        try {
            JarEntry entry = new JarEntry(name);
            jarOutput.putNextEntry(entry);
            jarOutput.write(value.getBytes(StandardCharsets.UTF_8));
            jarOutput.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
