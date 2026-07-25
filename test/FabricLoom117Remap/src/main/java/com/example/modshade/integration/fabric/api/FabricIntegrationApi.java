package com.example.modshade.integration.fabric.api;

import net.mezzdev.modshade.fixture.FixtureLibrary;
import net.minecraft.item.Items;

/**
 * Public API class that must stay free of shaded implementation classes.
 */
public final class FabricIntegrationApi {
    private FabricIntegrationApi() {
    }

    public static String id() {
        return "modshade_fabric_loom_remap_integration:" + Items.STICK.getTranslationKey() + ":" + FixtureLibrary.value();
    }
}
