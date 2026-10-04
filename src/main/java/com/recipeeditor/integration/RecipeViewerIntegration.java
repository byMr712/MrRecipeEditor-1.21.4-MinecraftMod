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
        if (FabricLoader.getInstance().isModLoaded("emi")) {
            try {
                Class<?> emiReloadManager = Class.forName("dev.emi.emi.runtime.EmiReloadManager");
                emiReloadManager.getMethod("reload").invoke(null);
            } catch (Throwable ignored) {
                try {
                    Class<?> emiRecipes = Class.forName("dev.emi.emi.registry.EmiRecipes");
                    emiRecipes.getMethod("bake").invoke(null);
                } catch (Throwable ignored2) {}
            }
        }
    }
}
