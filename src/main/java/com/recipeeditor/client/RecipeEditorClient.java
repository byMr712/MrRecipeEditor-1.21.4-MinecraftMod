package com.recipeeditor.client;

import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.inspector.RecipeInspector;
import net.fabricmc.api.ClientModInitializer;

public class RecipeEditorClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Initialize recipe viewer hooks (REI visibility predicate, etc.)
        com.recipeeditor.integration.RecipeViewerIntegration.init();

        // Asynchronously scan Fabric Mod JARs and tags in background at startup
        RecipeInspector.startJarScanAsync();

        // Clear world-specific recipe entries and caches when disconnecting from server/world
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            RecipeInspector.invalidateWorldCache();
            RecipeEditorConfig.getInstance().invalidateAllRecipeCaches();
        });

        // Invalidate and pre-warm recipe caches when joining a new world so tag lookups refresh
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            RecipeEditorConfig.getInstance().invalidateAllRecipeCaches();
            RecipeInspector.invalidateCache();
            RecipeInspector.startLangIndexViaRMAsync();
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    RecipeInspector.initializeCache(client.world);
                } catch (Throwable ignored) {}
            });
        });
    }
}
