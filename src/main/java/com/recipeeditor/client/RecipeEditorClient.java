package com.recipeeditor.client;

import com.recipeeditor.client.gui.RecipeEditorScreen;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.inspector.RecipeInspector;
import com.recipeeditor.network.SyncRecipesS2CPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class RecipeEditorClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Asynchronously scan Fabric Mod JARs and tags in background at startup
        RecipeInspector.startJarScanAsync();

        // Clear world-specific recipe entries and caches when disconnecting from server/world
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            RecipeInspector.invalidateWorldCache();
            RecipeEditorConfig.getInstance().invalidateAllRecipeCaches();
            RecipeEditorConfig.reloadLocal();
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

        ClientPlayNetworking.registerGlobalReceiver(SyncRecipesS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                String json = payload.jsonConfig();
                RecipeEditorConfig synced = RecipeEditorConfig.fromJson(json);
                if (synced != null) {
                    RecipeEditorConfig current = RecipeEditorConfig.getInstance();
                    current.modEnabled = synced.modEnabled;
                    current.recipes = synced.recipes;
                    current.invalidateAllRecipeCaches();
                    RecipeInspector.invalidateCache();

                    if (context.client().currentScreen instanceof RecipeEditorScreen screen) {
                        screen.onServerConfigSynced(synced);
                    }
                    com.recipeeditor.integration.RecipeViewerIntegration.reloadRecipeViewers();
                }
            });
        });
    }
}
