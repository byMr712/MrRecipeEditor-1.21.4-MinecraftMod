package com.recipeeditor.inspector;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeTypeEnum;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

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

    public Text getConflictingItemName() {
        if (conflictingItem == null) return Text.literal("Unknown");
        return conflictingItem.getName();
    }
}
