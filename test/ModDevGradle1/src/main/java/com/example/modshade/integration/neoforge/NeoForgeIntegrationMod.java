package com.example.modshade.integration.neoforge;

import net.mezzdev.modshade.fixture.FixtureLibrary;
import net.minecraft.world.item.Items;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge ModDevGradle 1 integration mod used to verify ModShade against a real
 * loader build.
 */
@Mod("modshade_moddevgradle1_integration")
public final class NeoForgeIntegrationMod {
    public NeoForgeIntegrationMod() {
        FixtureLibrary.value();
        minecraftReference();
    }

    public String minecraftReference() {
        return Items.STICK.getDescriptionId();
    }
}
