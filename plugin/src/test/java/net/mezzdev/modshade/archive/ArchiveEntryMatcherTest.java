package net.mezzdev.modshade.archive;

import net.mezzdev.modshade.RelocationRule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveEntryMatcherTest {
    @Test
    void matchesAntStyleArchiveEntryGlobs() {
        assertTrue(matches(List.of("META-INF/maven/**"), "META-INF/maven/org.example/library/pom.properties"));
        assertTrue(matches(List.of("META-INF/*.SF"), "META-INF/TEST.SF"));
        assertTrue(matches(List.of("assets/fixture-library/hidden-?.txt"), "assets/fixture-library/hidden-a.txt"));

        assertFalse(matches(List.of("META-INF/*.SF"), "META-INF/signatures/TEST.SF"));
        assertFalse(matches(List.of("assets/fixture-library/hidden-?.txt"), "assets/fixture-library/hidden-long.txt"));
    }

    @Test
    void matchesRelocatedAndUnrelocatedArchiveEntries() {
        ArchiveEntryMatcher.ArchiveRelocators relocators = relocators();
        List<String> patterns = List.of("org/example/**");

        assertTrue(ArchiveEntryMatcher.matchesEntry(patterns, "org/example/data.json", relocators));
        assertTrue(ArchiveEntryMatcher.matchesEntry(patterns, "com/example/modshade/org/example/data.json", relocators));
    }

    @Test
    void matchesRelocatedServiceProviderEntries() {
        List<String> patterns = List.of("META-INF/services/org.example.Service");

        assertTrue(ArchiveEntryMatcher.matchesEntry(patterns, "META-INF/services/org.example.Service", relocators()));
        assertTrue(ArchiveEntryMatcher.matchesEntry(patterns, "META-INF/services/com.example.modshade.org.example.Service", relocators()));
    }

    @Test
    void matchesRelocatedMultiReleaseEntries() {
        List<String> patterns = List.of("META-INF/versions/*/org/example/**");

        assertTrue(ArchiveEntryMatcher.matchesEntry(patterns, "META-INF/versions/9/org/example/Foo.class", relocators()));
        assertTrue(ArchiveEntryMatcher.matchesEntry(patterns, "META-INF/versions/9/com/example/modshade/org/example/Foo.class", relocators()));
    }

    private static boolean matches(List<String> patterns, String entryName) {
        return ArchiveEntryMatcher.matchesEntry(patterns, entryName, ArchiveEntryMatcher.relocators(List.of()));
    }

    private static ArchiveEntryMatcher.ArchiveRelocators relocators() {
        return ArchiveEntryMatcher.relocators(List.of(new RelocationRule("org.example", "com.example.modshade.org.example")));
    }
}
