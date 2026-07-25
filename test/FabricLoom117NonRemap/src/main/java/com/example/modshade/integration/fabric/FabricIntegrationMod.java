package com.example.modshade.integration.fabric;

import net.fabricmc.api.ModInitializer;
import net.minecraft.world.item.Items;
import net.mezzdev.modshade.fixture.FixtureLibrary;

/**
 * Fabric Loom 1.17 integration mod used to verify ModShade against a real loader
 * build.
 */
public final class FabricIntegrationMod implements ModInitializer {
    @Override
    public void onInitialize() {
        FixtureLibrary.value();
        minecraftReference();
    }

    public String minecraftReference() {
        return Items.STICK.getDescriptionId();
    }
}
