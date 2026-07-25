package net.mezzdev.modshade.relocation;

import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Infers coarse package roots from dependency jar class entries.
 */
public final class PackageRootScanner {
    private static final int DEFAULT_ROOT_DEPTH = 3;

    private PackageRootScanner() {
    }

    public static Set<String> inferPackageRoots(Collection<File> files) {
        Set<String> packageRoots = new TreeSet<>();
        for (File file : files) {
            if (!isJarFile(file)) {
                continue;
            }

            try (ZipFile zipFile = new ZipFile(file)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }

                    inferPackageRoot(entry.getName()).ifPresent(packageRoots::add);
                }
            } catch (IOException e) {
                throw new GradleException("Failed to inspect package roots in modShade dependency jar: " + file, e);
            }
        }
        return packageRoots;
    }

    static Optional<String> inferPackageRoot(String entryName) {
        Optional<String> normalizedClassEntry = normalizeClassEntry(entryName);
        if (normalizedClassEntry.isEmpty()) {
            return Optional.empty();
        }

        String classEntry = normalizedClassEntry.get();
        if (!classEntry.endsWith(".class")) {
            return Optional.empty();
        }
        if ("module-info.class".equals(classEntry)) {
            return Optional.empty();
        }

        int lastSlash = classEntry.lastIndexOf('/');
        if (lastSlash <= 0) {
            return Optional.empty();
        }

        String packagePath = classEntry.substring(0, lastSlash);
        String[] segments = packagePath.split("/");
        if (segments.length == 0) {
            return Optional.empty();
        }

        int depth = Math.min(DEFAULT_ROOT_DEPTH, segments.length);
        StringBuilder packageRoot = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            String segment = segments[i];
            if (!isJavaIdentifier(segment)) {
                return Optional.empty();
            }
            if (i > 0) {
                packageRoot.append('.');
            }
            packageRoot.append(segment);
        }

        return Optional.of(packageRoot.toString());
    }

    private static Optional<String> normalizeClassEntry(String entryName) {
        String multiReleasePrefix = "META-INF/versions/";
        if (!entryName.startsWith(multiReleasePrefix)) {
            return Optional.of(entryName);
        }

        String remainder = entryName.substring(multiReleasePrefix.length());
        int slash = remainder.indexOf('/');
        if (slash < 0) {
            return Optional.empty();
        }
        return Optional.of(remainder.substring(slash + 1));
    }

    private static boolean isJavaIdentifier(String segment) {
        if (segment.isEmpty() || !Character.isJavaIdentifierStart(segment.charAt(0))) {
            return false;
        }
        for (int i = 1; i < segment.length(); i++) {
            if (!Character.isJavaIdentifierPart(segment.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isJarFile(File file) {
        return file.isFile() && file.getName().endsWith(".jar");
    }
}
