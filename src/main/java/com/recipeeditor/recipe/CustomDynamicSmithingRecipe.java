package com.recipeeditor.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class CustomDynamicSmithingRecipe implements SmithingRecipe {
    private final String group;
    private final Optional<Ingredient> template;
    private final Ingredient base;
    private final Optional<Ingredient> addition;
    private final ItemStack resultStack;
    private PlacementInfo placementInfo;

    public CustomDynamicSmithingRecipe(String group, Optional<Ingredient> template, Optional<Ingredient> base, Optional<Ingredient> addition, ItemStack resultStack) {
        this.group = group != null ? group : "";
        this.template = template != null ? template : Optional.empty();
        this.base = base != null && base.isPresent() ? base.get() : Ingredient.of(Stream.empty());
        this.addition = addition != null ? addition : Optional.empty();
        this.resultStack = resultStack != null ? resultStack : ItemStack.EMPTY;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        if (input == null) return false;
        return Ingredient.testOptionalIngredient(this.template, input.template())
                && this.base.test(input.base())
                && Ingredient.testOptionalIngredient(this.addition, input.addition());
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        if (this.resultStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack baseStack = input != null ? input.base() : ItemStack.EMPTY;
        if (!baseStack.isEmpty()) {
            try {
                ItemStack crafted = this.resultStack.copy();
                crafted.applyComponents(baseStack.getComponents());
                if (!this.resultStack.isDamageableItem()) {
                    crafted.remove(DataComponents.DAMAGE);
                }
                return crafted;
            } catch (Throwable ignored) {}
        }
        return this.resultStack.copy();
    }

    @Override
    public Optional<Ingredient> templateIngredient() {
        return this.template;
    }

    @Override
    public Ingredient baseIngredient() {
        return this.base;
    }

    @Override
    public Optional<Ingredient> additionIngredient() {
        return this.addition;
    }

    @Override
    public RecipeSerializer<? extends SmithingRecipe> getSerializer() {
        return SmithingTransformRecipe.SERIALIZER;
    }

    @Override
    public RecipeType<SmithingRecipe> getType() {
        return RecipeType.SMITHING;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.SMITHING;
    }

    @Override
    public boolean showNotification() {
        return true;
    }

    @Override
    public String group() {
        return this.group;
    }

    @Override
    public PlacementInfo placementInfo() {
        if (this.placementInfo == null) {
            this.placementInfo = PlacementInfo.createFromOptionals(List.of(this.template, Optional.of(this.base), this.addition));
        }
        return this.placementInfo;
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new SmithingRecipeDisplay(
                Ingredient.optionalIngredientToDisplay(this.template),
                this.base.display(),
                Ingredient.optionalIngredientToDisplay(this.addition),
                new SlotDisplay.ItemStackSlotDisplay(new ItemStackTemplate(this.resultStack.getItem(), this.resultStack.getCount())),
                new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)
        ));
    }
}
