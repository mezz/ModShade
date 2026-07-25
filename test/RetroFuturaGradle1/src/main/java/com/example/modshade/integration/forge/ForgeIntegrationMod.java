package com.example.modshade.integration.forge;

import net.mezzdev.modshade.fixture.FixtureLibrary;
import net.minecraft.init.Items;

/**
 * Forge 1.12.2 integration mod used to verify ModShade against a real
 * RetroFuturaGradle 1 reobfuscation build.
 */
public final class ForgeIntegrationMod {
    public ForgeIntegrationMod() {
        FixtureLibrary.value();
        minecraftReference();
    }

    public String minecraftReference() {
        return Items.STICK.getTranslationKey();
    }
}
