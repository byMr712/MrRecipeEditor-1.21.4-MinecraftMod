package com.recipeeditor.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.SmithingTransformRecipe;

import java.util.Optional;

public class CustomDynamicSmithingRecipe extends SmithingTransformRecipe {
    public CustomDynamicSmithingRecipe(String group, Optional<Ingredient> template, Optional<Ingredient> base, Optional<Ingredient> addition, ItemStack resultStack) {
        super(
            template.orElseGet(Ingredient::empty),
            base.orElseGet(Ingredient::empty),
            addition.orElseGet(Ingredient::empty),
            resultStack
        );
    }
}
