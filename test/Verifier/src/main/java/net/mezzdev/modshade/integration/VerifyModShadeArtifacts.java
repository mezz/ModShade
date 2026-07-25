package net.mezzdev.modshade.integration;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Verifies the artifact contract produced by a loader integration build.
 */
public final class VerifyModShadeArtifacts {
    private VerifyModShadeArtifacts() {
    }

    public static void main(String[] args) throws IOException {
        Arguments arguments = Arguments.parse(args);

        for (String diagnosticJar : arguments.all("diagnostic-jar")) {
            requireFile(new File(diagnosticJar));
        }

        File runtimeJar = requireFile(new File(arguments.required("runtime-jar")));
        File sourcesJar = requireFile(new File(arguments.required("sources-jar")));
        File apiJar = requireFile(new File(arguments.required("api-jar")));

        String loaderMetadata = arguments.optional("loader-metadata");
        String modClass = arguments.required("mod-class");
        String modSource = arguments.required("mod-source");
        String apiClass = arguments.required("api-class");
        String relocatedLibraryClass = arguments.required("relocated-library-class");
        String relocatedLibraryInternalName = arguments.required("relocated-library-internal-name");
        String relocatedPackage = arguments.required("relocated-package");
        List<String> requiredRuntimeReferences = arguments.all("required-runtime-reference");
        List<String> forbiddenRuntimeReferences = arguments.all("forbidden-runtime-reference");
        List<String> requiredSourceTexts = arguments.all("required-source-text");
        List<String> forbiddenSourceTexts = arguments.all("forbidden-source-text");

        verifyRuntimeJar(
                runtimeJar,
                loaderMetadata,
                modClass,
                relocatedLibraryClass,
                relocatedLibraryInternalName,
                requiredRuntimeReferences,
                forbiddenRuntimeReferences
        );
        verifySourcesJar(sourcesJar, modSource, relocatedPackage, requiredSourceTexts, forbiddenSourceTexts);
        verifyApiJar(apiJar, apiClass, relocatedLibraryClass);
    }

    private static void verifyRuntimeJar(
            File jar,
            String loaderMetadata,
            String modClass,
            String relocatedLibraryClass,
            String relocatedLibraryInternalName,
            List<String> requiredRuntimeReferences,
            List<String> forbiddenRuntimeReferences
    ) throws IOException {
        if (loaderMetadata != null) {
            assertJarContains(jar, loaderMetadata);
        }
        assertJarContains(jar, modClass);
        assertJarContains(jar, relocatedLibraryClass);
        assertJarDoesNotContain(jar, "net/mezzdev/modshade/fixture/FixtureLibrary.class");
        assertJarDoesNotContain(jar, "META-INF/maven/net.mezzdev.modshade.integration/modshade-integration-library/pom.properties");
        assertJarDoesNotContain(jar, "META-INF/TEST.SF");
        assertClassContainsReferences(jar, modClass, List.of(relocatedLibraryInternalName));
        assertClassContainsReferences(jar, modClass, requiredRuntimeReferences);
        assertClassDoesNotContainReferences(jar, modClass, forbiddenRuntimeReferences);
    }

    private static void verifySourcesJar(
            File jar,
            String modSource,
            String relocatedPackage,
            List<String> requiredSourceTexts,
            List<String> forbiddenSourceTexts
    ) throws IOException {
        String relocatedSource = relocatedPackage.replace('.', '/') + "/FixtureLibrary.java";
        assertJarContains(jar, modSource);
        assertJarContains(jar, relocatedSource);
        assertJarEntryContains(jar, modSource, "import " + relocatedPackage + ".FixtureLibrary;");
        assertJarEntryDoesNotContain(jar, modSource, "import net.mezzdev.modshade.fixture.FixtureLibrary;");
        assertJarEntryContains(jar, relocatedSource, "package " + relocatedPackage + ";");
        assertJarDoesNotContain(jar, "net/mezzdev/modshade/fixture/FixtureLibrary.java");
        assertJarDoesNotContain(jar, "net/mezzdev/modshade/fixture/FixtureLibrary.class");
        for (String requiredSourceText : requiredSourceTexts) {
            assertJarEntryContains(jar, modSource, requiredSourceText);
        }
        for (String forbiddenSourceText : forbiddenSourceTexts) {
            assertJarEntryDoesNotContain(jar, modSource, forbiddenSourceText);
        }
    }

    private static void verifyApiJar(File jar, String apiClass, String relocatedLibraryClass) throws IOException {
        assertJarContains(jar, apiClass);
        assertJarDoesNotContain(jar, relocatedLibraryClass);
        assertJarDoesNotContain(jar, "net/mezzdev/modshade/fixture/FixtureLibrary.class");
    }

    private static File requireFile(File file) {
        if (!file.isFile()) {
            throw new IllegalStateException("Expected file to exist: " + file);
        }
        return file;
    }

    private static void assertJarContains(File jar, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            if (zip.getEntry(entryName) == null) {
                throw new IllegalStateException("Expected " + jar + " to contain " + entryName);
            }
        }
    }

    private static void assertJarDoesNotContain(File jar, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            if (zip.getEntry(entryName) != null) {
                throw new IllegalStateException("Expected " + jar + " not to contain " + entryName);
            }
        }
    }

    private static void assertJarEntryContains(File jar, String entryName, String expectedText) throws IOException {
        String text = jarEntryText(jar, entryName);
        if (!text.contains(expectedText)) {
            throw new IllegalStateException("Expected " + entryName + " in " + jar + " to contain " + expectedText);
        }
    }

    private static void assertJarEntryDoesNotContain(File jar, String entryName, String unexpectedText) throws IOException {
        String text = jarEntryText(jar, entryName);
        if (text.contains(unexpectedText)) {
            throw new IllegalStateException("Expected " + entryName + " in " + jar + " not to contain " + unexpectedText);
        }
    }

    private static String jarEntryText(File jar, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            var entry = zip.getEntry(entryName);
            if (entry == null) {
                throw new IllegalStateException("Expected " + jar + " to contain " + entryName);
            }
            return new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private static void assertClassContainsReferences(
            File jar,
            String classEntryName,
            List<String> expectedReferences
    ) throws IOException {
        if (expectedReferences.isEmpty()) {
            return;
        }

        Set<String> references = readClassReferences(jar, classEntryName);
        for (String expectedReference : expectedReferences) {
            if (!references.contains(expectedReference)) {
                throw new IllegalStateException("Expected " + classEntryName + " in " + jar + " to reference " + expectedReference);
            }
        }
    }

    private static void assertClassDoesNotContainReferences(
            File jar,
            String classEntryName,
            List<String> unexpectedReferences
    ) throws IOException {
        if (unexpectedReferences.isEmpty()) {
            return;
        }

        Set<String> references = readClassReferences(jar, classEntryName);
        for (String unexpectedReference : unexpectedReferences) {
            if (references.contains(unexpectedReference)) {
                throw new IllegalStateException("Expected " + classEntryName + " in " + jar + " not to reference " + unexpectedReference);
            }
        }
    }

    private static Set<String> readClassReferences(File jar, String classEntryName) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = Objects.requireNonNull(zip.getEntry(classEntryName), () -> jar + " should contain " + classEntryName);
            return readClassReferences(zip.getInputStream(entry).readAllBytes());
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

    private record Arguments(Map<String, List<String>> values) {
        private static Arguments parse(String[] args) {
            Map<String, List<String>> values = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i++) {
                String key = args[i];
                if (!key.startsWith("--")) {
                    throw new IllegalArgumentException("Expected option name, got: " + key);
                }
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for option: " + key);
                }
                values.computeIfAbsent(key.substring(2), ignored -> new ArrayList<>()).add(args[++i]);
            }
            return new Arguments(values);
        }

        private String required(String key) {
            List<String> matches = values.get(key);
            if (matches == null || matches.isEmpty()) {
                throw new IllegalArgumentException("Missing required option: --" + key);
            }
            if (matches.size() > 1) {
                throw new IllegalArgumentException("Expected one value for --" + key + ", got " + matches.size());
            }
            return matches.get(0);
        }

        private String optional(String key) {
            List<String> matches = values.get(key);
            if (matches == null || matches.isEmpty()) {
                return null;
            }
            if (matches.size() > 1) {
                throw new IllegalArgumentException("Expected at most one value for --" + key + ", got " + matches.size());
            }
            return matches.get(0);
        }

        private List<String> all(String key) {
            return values.getOrDefault(key, List.of());
        }
    }
}
