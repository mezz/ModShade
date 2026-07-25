package com.example.modshade.integration.forge;

import net.mezzdev.modshade.fixture.FixtureLibrary;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.common.Mod;

/**
 * ForgeGradle integration mod used to verify ModShade against a real loader
 * build.
 */
@Mod("modshade_forge_integration")
public final class ForgeIntegrationMod {
    public ForgeIntegrationMod() {
        FixtureLibrary.value();
        minecraftReference();
    }

    public String minecraftReference() {
        return Items.STICK.getDescriptionId();
    }
}
