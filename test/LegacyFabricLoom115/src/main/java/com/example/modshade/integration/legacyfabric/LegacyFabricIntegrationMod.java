package com.example.modshade.integration.legacyfabric;

import net.fabricmc.api.ModInitializer;
import net.mezzdev.modshade.fixture.FixtureLibrary;
import net.minecraft.item.Items;

/**
 * Legacy Fabric integration mod used to verify ModShade against a real loader
 * build for Minecraft 1.13.2.
 */
public final class LegacyFabricIntegrationMod implements ModInitializer {
    @Override
    public void onInitialize() {
        FixtureLibrary.value();
        minecraftReference();
    }

    public String minecraftReference() {
        return Items.STICK.getTranslationKey();
    }
}
