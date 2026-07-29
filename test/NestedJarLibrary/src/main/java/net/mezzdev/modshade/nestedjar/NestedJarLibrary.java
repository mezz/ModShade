package net.mezzdev.modshade.nestedjar;

/**
 * Marker class for loader-specific nested-jar integration tests.
 */
public final class NestedJarLibrary {
    private NestedJarLibrary() {
    }

    public static String marker() {
        return "nested-jar-library";
    }
}
