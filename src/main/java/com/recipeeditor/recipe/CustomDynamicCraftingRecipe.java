package com.recipeeditor.recipe;

import com.recipeeditor.RecipeEditorMod;
import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.book.RecipeBookCategories;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

import java.util.*;

public class CustomDynamicCraftingRecipe extends ShapedRecipe {

    public CustomDynamicCraftingRecipe(CraftingRecipeCategory category) {
        super("recipeeditor", category, createDefaultRaw(), new ItemStack(Items.TOTEM_OF_UNDYING), true);
    }

    private static RawShapedRecipe createDefaultRaw() {
        Map<Character, Ingredient> key = Map.of(
                'A', Ingredient.ofItem(Items.GOLDEN_APPLE),
                'G', Ingredient.ofItem(Items.GHAST_TEAR)
        );
        return RawShapedRecipe.create(key, "AAA", "AGA", "AAA");
    }

    @Override
    public int getWidth() {
        return 3;
    }

    @Override
    public int getHeight() {
        return 3;
    }

    @Override
    public boolean isIgnoredInRecipeBook() {
        return false;
    }

    @Override
    public boolean showNotification() {
        return true;
    }

    @Override
    public RecipeBookCategory getRecipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    private CustomRecipeData findMatchingRecipe(CraftingRecipeInput input) {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config.recipes == null) return null;

        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled) continue;

            if (recipeData.type == RecipeTypeEnum.SHAPED_CRAFTING) {
                if (recipeData.getRawRecipe().matches(input)) {
                    return recipeData;
                }
            } else if (recipeData.type == RecipeTypeEnum.SHAPELESS_CRAFTING) {
                if (matchesShapeless(input, recipeData)) {
                    return recipeData;
                }
            }
        }
        return null;
    }

    private boolean matchesShapeless(CraftingRecipeInput input, CustomRecipeData recipeData) {
        List<ItemStack> inputItems = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (!stack.isEmpty()) {
                inputItems.add(stack);
            }
        }

        List<Ingredient> ingredients = recipeData.getShapelessIngredients();
        if (inputItems.size() != ingredients.size()) {
            return false;
        }

        boolean[] matched = new boolean[ingredients.size()];
        for (ItemStack stack : inputItems) {
            boolean found = false;
            for (int i = 0; i < ingredients.size(); i++) {
                if (!matched[i] && ingredients.get(i).test(stack)) {
                    matched[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        return findMatchingRecipe(input) != null;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        CustomRecipeData matched = findMatchingRecipe(input);
        if (matched != null) {
            return new ItemStack(matched.getResultItem(), matched.resultCount);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public List<RecipeDisplay> getDisplays() {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config.recipes == null) {
            return Collections.emptyList();
        }

        List<RecipeDisplay> displays = new ArrayList<>();
        SlotDisplay craftingStation = new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE);

        for (CustomRecipeData recipe : config.recipes.values()) {
            if (!recipe.enabled) continue;
            Item resultItem = recipe.getResultItem();
            if (resultItem == Items.AIR) continue;

            SlotDisplay resultDisplay = new SlotDisplay.StackSlotDisplay(new ItemStack(resultItem, recipe.resultCount));

            if (recipe.type == RecipeTypeEnum.SHAPED_CRAFTING) {
                List<SlotDisplay> ingredients = new ArrayList<>(9);
                for (int i = 0; i < 9; i++) {
                    Item item = recipe.getItemAt(i);
                    if (item == Items.AIR) {
                        ingredients.add(SlotDisplay.EmptySlotDisplay.INSTANCE);
                    } else {
                        ingredients.add(new SlotDisplay.ItemSlotDisplay(item));
                    }
                }
                displays.add(new ShapedCraftingRecipeDisplay(3, 3, ingredients, resultDisplay, craftingStation));
            } else if (recipe.type == RecipeTypeEnum.SHAPELESS_CRAFTING) {
                List<SlotDisplay> ingredients = new ArrayList<>();
                for (int i = 0; i < 9; i++) {
                    Item item = recipe.getItemAt(i);
                    if (item != Items.AIR) {
                        ingredients.add(new SlotDisplay.ItemSlotDisplay(item));
                    }
                }
                if (!ingredients.isEmpty()) {
                    displays.add(new ShapelessCraftingRecipeDisplay(ingredients, resultDisplay, craftingStation));
                }
            }
        }

        return displays;
    }

    @Override
    public RecipeSerializer<? extends ShapedRecipe> getSerializer() {
        return RecipeEditorMod.RECIPE_SERIALIZER;
    }
}
