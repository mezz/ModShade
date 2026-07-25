package com.example.modshade.integration.neoforge.api;

/**
 * Public API class that must stay free of shaded implementation classes.
 */
public final class NeoForgeIntegrationApi {
    private NeoForgeIntegrationApi() {
    }

    public static String id() {
        return "modshade_moddevgradle_integration";
    }
}
