package com.example.modshade.integration.vanilla;

import net.mezzdev.modshade.fixture.FixtureLibrary;
import net.minecraft.SharedConstants;

/**
 * VanillaGradle integration module used to verify ModShade against a real
 * VanillaGradle build.
 */
public final class VanillaGradleIntegrationModule {
    public String value() {
        return FixtureLibrary.value() + minecraftReference();
    }

    public String minecraftReference() {
        return SharedConstants.getCurrentVersion().toString();
    }
}
