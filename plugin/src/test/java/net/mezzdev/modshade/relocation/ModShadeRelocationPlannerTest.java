package net.mezzdev.modshade.relocation;

import net.mezzdev.modshade.RelocationRule;
import net.mezzdev.modshade.TestFixtures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModShadeRelocationPlannerTest {
    @TempDir
    Path tempDir;

    @Test
    void infersRelocationsWhenNoExplicitRulesAreConfigured() throws IOException {
        Path jar = tempDir.resolve("dependency.jar");
        TestFixtures.createJar(jar, List.of("net/mezzdev/fixture/library/FixtureLibrary.class"));

        List<RelocationRule> rules = ModShadeRelocationPlanner.planRelocations(
                "com.example.libs",
                List.of(),
                List.of(jar.toFile())
        );

        assertEquals(List.of("net.mezzdev.fixture -> com.example.libs.net.mezzdev.fixture"), rules.stream()
                .map(ModShadeRelocationPlanner::formatRule)
                .toList());
    }

    @Test
    void explicitRelocationsDisableInferredRelocations() throws IOException {
        Path jar = tempDir.resolve("dependency.jar");
        TestFixtures.createJar(jar, List.of("net/mezzdev/fixture/library/FixtureLibrary.class"));

        List<RelocationRule> rules = ModShadeRelocationPlanner.planRelocations(
                "com.example.libs",
                List.of(new RelocationRule("com.acme", "com.example.vendor.acme")),
                List.of(jar.toFile())
        );

        assertEquals(List.of("com.acme -> com.example.vendor.acme"), rules.stream()
                .map(ModShadeRelocationPlanner::formatRule)
                .toList());
    }

    @Test
    void parsesFormattedRelocationRule() {
        RelocationRule rule = ModShadeRelocationPlanner.parseRule("net.foo -> com.example.net.foo");

        assertEquals("net.foo", rule.fromPackage());
        assertEquals("com.example.net.foo", rule.toPackage());
    }
}
