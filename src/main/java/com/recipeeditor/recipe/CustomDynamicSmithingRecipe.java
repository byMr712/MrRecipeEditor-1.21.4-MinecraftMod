package com.recipeeditor.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.SmithingTransformRecipe;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

import java.util.Optional;

public class CustomDynamicSmithingRecipe extends SmithingTransformRecipe {
    private final Ingredient templateIngredient;
    private final Ingredient baseIngredient;
    private final Ingredient additionIngredient;
    private final ItemStack result;

    public CustomDynamicSmithingRecipe(String group, Optional<Ingredient> template, Optional<Ingredient> base, Optional<Ingredient> addition, ItemStack resultStack) {
        super(
            template.orElseGet(Ingredient::empty),
            base.orElseGet(Ingredient::empty),
            addition.orElseGet(Ingredient::empty),
            resultStack
        );
        this.templateIngredient = template.orElseGet(Ingredient::empty);
        this.baseIngredient = base.orElseGet(Ingredient::empty);
        this.additionIngredient = addition.orElseGet(Ingredient::empty);
        this.result = resultStack;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, World world) {
        if (!testSlot(this.templateIngredient, input.template())) return false;
        if (!testSlot(this.baseIngredient, input.base())) return false;
        if (!testSlot(this.additionIngredient, input.addition())) return false;
        return true;
    }

    private static boolean testSlot(Ingredient ingredient, ItemStack stack) {
        if (ingredient == null || ingredient.isEmpty()) {
            return stack == null || stack.isEmpty();
        }
        return stack != null && ingredient.test(stack);
    }

    @Override
    public ItemStack craft(SmithingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        return this.result.copy();
    }

    @Override
    public ItemStack getResult(RegistryWrapper.WrapperLookup lookup) {
        return this.result;
    }

    @Override
    public boolean testTemplate(ItemStack stack) {
        return this.templateIngredient != null && !this.templateIngredient.isEmpty() && this.templateIngredient.test(stack);
    }

    @Override
    public boolean testBase(ItemStack stack) {
        return this.baseIngredient != null && !this.baseIngredient.isEmpty() && this.baseIngredient.test(stack);
    }

    @Override
    public boolean testAddition(ItemStack stack) {
        return this.additionIngredient != null && !this.additionIngredient.isEmpty() && this.additionIngredient.test(stack);
    }
}
