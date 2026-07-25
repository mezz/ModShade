package net.mezzdev.modshade.fixture;

/**
 * Plain library class that the real loader integration mods shade with
 * ModShade.
 */
public final class FixtureLibrary {
    private FixtureLibrary() {
    }

    public static String value() {
        return "fixture";
    }
}
