package com.example.modshade.integration.fabric.api;

/**
 * Public API class that must stay free of shaded implementation classes.
 */
public final class FabricIntegrationApi {
    private FabricIntegrationApi() {
    }

    public static String id() {
        return "modshade_fabric_loom_non_remap_integration";
    }
}
