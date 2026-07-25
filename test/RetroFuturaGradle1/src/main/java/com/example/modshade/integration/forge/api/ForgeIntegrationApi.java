package com.example.modshade.integration.forge.api;

/**
 * Public API class that must stay free of shaded implementation classes.
 */
public final class ForgeIntegrationApi {
    private ForgeIntegrationApi() {
    }

    public static String id() {
        return "modshade_forge_112_integration";
    }
}
