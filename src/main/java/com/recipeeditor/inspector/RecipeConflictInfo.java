package com.recipeeditor.inspector;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeTypeEnum;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class RecipeConflictInfo {
    public final CustomRecipeData attemptedRecipe;
    public final Item conflictingItem;
    public final String sourceName;
    public final RecipeTypeEnum workstationType;

    public RecipeConflictInfo(CustomRecipeData attemptedRecipe, Item conflictingItem, String sourceName, RecipeTypeEnum workstationType) {
        this.attemptedRecipe = attemptedRecipe;
        this.conflictingItem = conflictingItem;
        this.sourceName = sourceName;
        this.workstationType = workstationType;
    }

    public Text getConflictingItemName() {
        if (conflictingItem == null) return Text.literal("Unknown");
        return new ItemStack(conflictingItem).getName();
    }
}
