package com.example.modshade.integration.legacyfabric.api;

import net.mezzdev.modshade.fixture.FixtureLibrary;
import net.minecraft.item.Items;

/**
 * Public API class that must stay free of shaded implementation classes.
 */
public final class LegacyFabricIntegrationApi {
    private LegacyFabricIntegrationApi() {
    }

    public static String id() {
        return "modshade_legacy_fabric_loom_115_integration:" + Items.STICK.getTranslationKey() + ":" + FixtureLibrary.value();
    }
}
