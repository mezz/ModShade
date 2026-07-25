package net.mezzdev.modshade.relocation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackageNameSanitizerTest {
    @Test
    void usesFallbackWhenNoValidSegmentsExist() {
        assertEquals("fallback", PackageNameSanitizer.sanitize("...", "fallback"));
    }

    @Test
    void usesFallbackForNullInput() {
        assertEquals("fallback", PackageNameSanitizer.sanitize(null, "fallback"));
    }
}
