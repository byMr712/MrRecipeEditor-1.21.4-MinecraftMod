package com.recipeeditor.integration;

import net.fabricmc.loader.api.FabricLoader;

public class RecipeViewerIntegration {

    public static void reloadRecipeViewers() {
        if (FabricLoader.getInstance().isModLoaded("roughlyenoughitems")) {
            try {
                Class<?> reiRuntimeClass = Class.forName("me.shedaniel.rei.api.client.REIRuntime");
                Object instance = reiRuntimeClass.getMethod("getInstance").invoke(null);
                if (instance != null) {
                    reiRuntimeClass.getMethod("startReload").invoke(instance);
                }
            } catch (Throwable ignored) {}
        }
    }
}
