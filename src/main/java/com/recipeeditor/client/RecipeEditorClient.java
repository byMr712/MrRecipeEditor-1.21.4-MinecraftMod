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
        ClientPlayNetworking.registerGlobalReceiver(SyncRecipesS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                String json = payload.jsonConfig();
                RecipeEditorConfig synced = RecipeEditorConfig.fromJson(json);
                if (synced != null) {
                    RecipeEditorConfig current = RecipeEditorConfig.getInstance();
                    current.modEnabled = synced.modEnabled;
                    current.recipes = synced.recipes;
                    RecipeInspector.invalidateCache();

                    if (context.client().currentScreen instanceof RecipeEditorScreen screen) {
                        screen.onServerConfigSynced(synced);
                    }
                }
            });
        });
    }
}
