package com.recipeeditor.integration.rei;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import dev.architectury.event.EventResult;
import me.shedaniel.rei.api.client.REIRuntime;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class RecipeEditorReiClientPlugin implements REIClientPlugin {

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        // Register dynamic display generator for all custom recipes (instant live support for new workstations!)
        registry.registerGlobalDisplayGenerator(new RecipeEditorDynamicDisplayGenerator());

        // Register visibility predicate to hide any vanilla or modded recipes that were overridden
        registry.registerVisibilityPredicate((category, display) -> {
            Object origin = registry.getDisplayOrigin(display);
            if (origin instanceof RecipeEntry<?> entry && CustomRecipeDispatcher.isRecipeOverridden(entry)) {
                return EventResult.interruptFalse();
            }
            if (display.getDisplayLocation().isPresent()) {
                Identifier loc = display.getDisplayLocation().get();
                if (CustomRecipeDispatcher.isIdentifierOverridden(loc)) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });
    }

    public static void reload() {
        try {
            REIRuntime.getInstance().startReload();
        } catch (Throwable ignored) {}
    }
}
