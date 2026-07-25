package com.example.modshade.integration.neoforge;

import net.mezzdev.modshade.fixture.FixtureLibrary;
import net.minecraft.world.item.Items;
import net.neoforged.fml.common.Mod;

/**
 * NeoGradle integration mod used to verify ModShade against a real loader build.
 */
@Mod("modshade_neogradle_integration")
public final class NeoGradleIntegrationMod {
    public NeoGradleIntegrationMod() {
        FixtureLibrary.value();
        minecraftReference();
    }

    public String minecraftReference() {
        return Items.STICK.getDescriptionId();
    }
}
