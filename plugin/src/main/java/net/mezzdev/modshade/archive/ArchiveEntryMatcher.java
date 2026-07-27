package net.mezzdev.modshade.archive;

import com.github.jengelman.gradle.plugins.shadow.ShadowStats;
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext;
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext;
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator;
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator;

import org.apache.tools.ant.types.selectors.SelectorUtils;

import net.mezzdev.modshade.RelocationRule;

import java.util.List;

/**
 * Matches archive entries against ModShade exclude patterns.
 */
public final class ArchiveEntryMatcher {
    private static final String MULTI_RELEASE_PREFIX = "META-INF/versions/";
    private static final String SERVICES_PREFIX = "META-INF/services/";

    private ArchiveEntryMatcher() {
    }

    public static ArchiveRelocators relocators(List<RelocationRule> rules) {
        return new ArchiveRelocators(
                relocators(rules, false),
                relocators(rules, true)
        );
    }

    public static boolean matchesEntry(
            List<String> patterns,
            String entryName,
            ArchiveRelocators relocators
    ) {
        if (matchesAny(patterns, entryName)) {
            return true;
        }

        String unrelocatedEntryName = relocateEntryName(entryName, relocators.reverse());
        if (!entryName.equals(unrelocatedEntryName) && matchesAny(patterns, unrelocatedEntryName)) {
            return true;
        }

        String relocatedEntryName = relocateEntryName(entryName, relocators.forward());
        return !entryName.equals(relocatedEntryName) && matchesAny(patterns, relocatedEntryName);
    }

    public static String relocateEntryName(String entryName, ArchiveRelocators relocators) {
        return relocateEntryName(entryName, relocators.forward());
    }

    private static String relocateEntryName(String entryName, List<Relocator> relocators) {
        if (entryName.startsWith(SERVICES_PREFIX)) {
            return relocateServiceProviderEntry(entryName, relocators);
        }

        ArchivePath archivePath = splitMultiReleasePrefix(entryName);
        return archivePath.prefix() + relocatePath(archivePath.path(), relocators);
    }

    private static boolean matchesAny(List<String> patterns, String entryName) {
        for (String pattern : patterns) {
            if (SelectorUtils.matchPath(pattern, entryName, true)) {
                return true;
            }
        }
        return false;
    }

    private static ArchivePath splitMultiReleasePrefix(String entryName) {
        if (!entryName.startsWith(MULTI_RELEASE_PREFIX)) {
            return new ArchivePath("", entryName);
        }

        String remainder = entryName.substring(MULTI_RELEASE_PREFIX.length());
        int slash = remainder.indexOf('/');
        if (slash < 0) {
            return new ArchivePath("", entryName);
        }
        int prefixLength = MULTI_RELEASE_PREFIX.length() + slash + 1;
        return new ArchivePath(entryName.substring(0, prefixLength), entryName.substring(prefixLength));
    }

    private static String relocateServiceProviderEntry(String entryName, List<Relocator> relocators) {
        String serviceClassName = entryName.substring(SERVICES_PREFIX.length());
        for (Relocator relocator : relocators) {
            if (relocator.canRelocateClass(serviceClassName)) {
                return SERVICES_PREFIX + relocator.relocateClass(new RelocateClassContext(serviceClassName, new ShadowStats()));
            }
        }
        return entryName;
    }

    private static String relocatePath(String path, List<Relocator> relocators) {
        for (Relocator relocator : relocators) {
            if (relocator.canRelocatePath(path)) {
                return relocator.relocatePath(new RelocatePathContext(path, new ShadowStats()));
            }
        }
        return path;
    }

    private static List<Relocator> relocators(List<RelocationRule> rules, boolean reverse) {
        return rules.stream()
                .map(rule -> new SimpleRelocator(
                        reverse ? rule.toPackage() : rule.fromPackage(),
                        reverse ? rule.fromPackage() : rule.toPackage(),
                        List.of(),
                        List.of()
                ))
                .map(Relocator.class::cast)
                .toList();
    }

    public record ArchiveRelocators(List<Relocator> forward, List<Relocator> reverse) {
        public ArchiveRelocators {
            forward = List.copyOf(forward);
            reverse = List.copyOf(reverse);
        }
    }

    private record ArchivePath(String prefix, String path) {
    }
}
