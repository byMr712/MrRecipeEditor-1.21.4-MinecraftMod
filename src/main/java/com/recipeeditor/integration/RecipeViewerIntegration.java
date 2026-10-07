package com.recipeeditor.integration;

import net.minecraft.client.gui.screens.Screen;

public class RecipeViewerIntegration {
    public static void init() {}
    public static void notifyRecipeChanged() {}
    public static void scheduleDebouncedReload() {}
    public static void updateRecipeInViewers(com.recipeeditor.config.CustomRecipeData recipe, String originalKey) {}
    public static void removeRecipeFromViewers(String key) {}
}
