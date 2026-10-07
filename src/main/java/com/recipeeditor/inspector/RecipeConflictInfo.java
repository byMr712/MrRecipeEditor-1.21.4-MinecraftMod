package com.recipeeditor.inspector;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeTypeEnum;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class RecipeConflictInfo {
    public final CustomRecipeData attemptedRecipe;
    public final Item conflictingItem;
    public final String sourceName;
    public final RecipeTypeEnum workstationType;

    public final String conflictingRecipeId;
    public final String conflictingRecipeKey;

    public RecipeConflictInfo(CustomRecipeData attemptedRecipe, Item conflictingItem, String sourceName, RecipeTypeEnum workstationType) {
        this(attemptedRecipe, conflictingItem, sourceName, workstationType, null, null);
    }

    public RecipeConflictInfo(CustomRecipeData attemptedRecipe, Item conflictingItem, String sourceName, RecipeTypeEnum workstationType, String conflictingRecipeId, String conflictingRecipeKey) {
        this.attemptedRecipe = attemptedRecipe;
        this.conflictingItem = conflictingItem;
        this.sourceName = sourceName;
        this.workstationType = workstationType;
        this.conflictingRecipeId = conflictingRecipeId;
        this.conflictingRecipeKey = conflictingRecipeKey;
    }

    public Component getConflictingItemName() {
        if (conflictingItem == null) return Component.literal("Unknown");
        try {
            if (conflictingItem.builtInRegistryHolder().areComponentsBound()) {
                return new ItemStack(conflictingItem).getHoverName();
            }
        } catch (Throwable ignored) {}
        return Component.translatable(conflictingItem.getDescriptionId());
    }
}
