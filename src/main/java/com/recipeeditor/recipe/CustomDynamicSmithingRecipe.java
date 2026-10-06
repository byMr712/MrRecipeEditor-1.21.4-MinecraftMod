package com.recipeeditor.recipe;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.IngredientPlacement;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SmithingRecipe;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.display.SmithingRecipeDisplay;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;

public class CustomDynamicSmithingRecipe implements SmithingRecipe {
    private final String group;
    private final Optional<Ingredient> template;
    private final Optional<Ingredient> base;
    private final Optional<Ingredient> addition;
    private final ItemStack resultStack;
    private IngredientPlacement ingredientPlacement;

    public CustomDynamicSmithingRecipe(String group, Optional<Ingredient> template, Optional<Ingredient> base, Optional<Ingredient> addition, ItemStack resultStack) {
        this.group = group != null ? group : "";
        this.template = template != null ? template : Optional.empty();
        this.base = base != null ? base : Optional.empty();
        this.addition = addition != null ? addition : Optional.empty();
        this.resultStack = resultStack != null ? resultStack : ItemStack.EMPTY;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, World world) {
        if (input == null) return false;
        return Ingredient.matches(this.template, input.template())
                && Ingredient.matches(this.base, input.base())
                && Ingredient.matches(this.addition, input.addition());
    }

    @Override
    public ItemStack craft(SmithingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        if (this.resultStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack baseStack = input != null ? input.base() : ItemStack.EMPTY;
        if (!baseStack.isEmpty()) {
            try {
                ItemStack crafted = baseStack.copyComponentsToNewStack(this.resultStack.getItem(), this.resultStack.getCount());
                crafted.applyUnvalidatedChanges(this.resultStack.getComponentChanges());
                if (!this.resultStack.isDamageable()) {
                    crafted.remove(DataComponentTypes.DAMAGE);
                }
                if (!crafted.isEmpty()) {
                    return crafted;
                }
            } catch (Throwable ignored) {}
        }
        return this.resultStack.copy();
    }

    @Override
    public Optional<Ingredient> template() {
        return this.template;
    }

    @Override
    public Optional<Ingredient> base() {
        return this.base;
    }

    @Override
    public Optional<Ingredient> addition() {
        return this.addition;
    }

    @Override
    public RecipeSerializer<? extends SmithingRecipe> getSerializer() {
        return RecipeSerializer.SMITHING_TRANSFORM;
    }

    @Override
    public IngredientPlacement getIngredientPlacement() {
        if (this.ingredientPlacement == null) {
            this.ingredientPlacement = IngredientPlacement.forMultipleSlots(List.of(this.template, this.base, this.addition));
        }
        return this.ingredientPlacement;
    }

    @Override
    public List<RecipeDisplay> getDisplays() {
        return List.of(new SmithingRecipeDisplay(
                Ingredient.toDisplay(this.template),
                Ingredient.toDisplay(this.base),
                Ingredient.toDisplay(this.addition),
                new SlotDisplay.StackSlotDisplay(this.resultStack),
                new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)
        ));
    }

    @Override
    public String getGroup() {
        return this.group;
    }
}
