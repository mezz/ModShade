package net.mezzdev.modshade.relocation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageRootScannerTest {
    @Test
    void infersThreeSegmentPackageRoot() {
        assertEquals(
                "net.mezzdev.fixture",
                PackageRootScanner.inferPackageRoot("net/mezzdev/fixture/library/FixtureLibrary.class").orElseThrow()
        );
    }

    @Test
    void ignoresModuleInfo() {
        assertTrue(PackageRootScanner.inferPackageRoot("module-info.class").isEmpty());
    }

    @Test
    void handlesMultiReleaseJarEntries() {
        assertEquals(
                "com.google.common",
                PackageRootScanner.inferPackageRoot("META-INF/versions/17/com/google/common/Foo.class").orElseThrow()
        );
    }

    @Test
    void ignoresInvalidMultiReleaseJarEntries() {
        assertTrue(PackageRootScanner.inferPackageRoot("META-INF/versions/17").isEmpty());
    }

    @Test
    void ignoresJavaKeywordPackageSegments() {
        assertTrue(PackageRootScanner.inferPackageRoot("com/class/example/Foo.class").isEmpty());
    }
}
