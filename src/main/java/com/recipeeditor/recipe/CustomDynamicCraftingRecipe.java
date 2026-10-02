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
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.*;

public class CustomDynamicCraftingRecipe extends ShapedRecipe {

    public CustomDynamicCraftingRecipe(CraftingRecipeCategory category) {
        super("recipeeditor", category, createDefaultRaw(), new ItemStack(Items.DIRT), true);
    }

    private static RawShapedRecipe createDefaultRaw() {
        Map<Character, Ingredient> key = Map.of('A', Ingredient.ofItem(Items.DIRT));
        return RawShapedRecipe.create(key, "A");
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
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty() || input.isEmpty()) {
            return null;
        }

        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled) continue;

            if (recipeData.type == RecipeTypeEnum.SHAPED_CRAFTING) {
                if (matchesShaped(input, recipeData)) {
                    return recipeData;
                }
            }
        }
        return null;
    }

    private boolean matchesShaped(CraftingRecipeInput input, CustomRecipeData recipeData) {
        if (input.isEmpty()) return false;

        // 1. Determine bounding box of non-empty slots in recipe
        int minRow = 3, maxRow = -1, minCol = 3, maxCol = -1;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int slotIdx = r * 3 + c;
                Ingredient ing = recipeData.getIngredientAt(slotIdx);
                if (ing != null) {
                    if (r < minRow) minRow = r;
                    if (r > maxRow) maxRow = r;
                    if (c < minCol) minCol = c;
                    if (c > maxCol) maxCol = c;
                }
            }
        }

        if (maxRow == -1) {
            // Recipe is completely empty
            return false;
        }

        int patternW = maxCol - minCol + 1;
        int patternH = maxRow - minRow + 1;

        if (input.getWidth() != patternW || input.getHeight() != patternH) {
            return false;
        }

        // Check normal orientation
        boolean normalMatches = true;
        for (int r = 0; r < patternH; r++) {
            for (int c = 0; c < patternW; c++) {
                int slotIdx = (minRow + r) * 3 + (minCol + c);
                Ingredient expected = recipeData.getIngredientAt(slotIdx);
                ItemStack actual = input.getStackInSlot(c, r);

                if (expected == null) {
                    if (!actual.isEmpty()) {
                        normalMatches = false;
                        break;
                    }
                } else {
                    if (!expected.test(actual)) {
                        normalMatches = false;
                        break;
                    }
                }
            }
            if (!normalMatches) break;
        }

        if (normalMatches) {
            return true;
        }

        // Check mirrored orientation (flipped horizontally)
        boolean mirroredMatches = true;
        for (int r = 0; r < patternH; r++) {
            for (int c = 0; c < patternW; c++) {
                int slotIdx = (minRow + r) * 3 + (maxCol - c);
                Ingredient expected = recipeData.getIngredientAt(slotIdx);
                ItemStack actual = input.getStackInSlot(c, r);

                if (expected == null) {
                    if (!actual.isEmpty()) {
                        mirroredMatches = false;
                        break;
                    }
                } else {
                    if (!expected.test(actual)) {
                        mirroredMatches = false;
                        break;
                    }
                }
            }
            if (!mirroredMatches) break;
        }

        return mirroredMatches;
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        return findMatchingRecipe(input) != null;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        CustomRecipeData matched = findMatchingRecipe(input);
        if (matched != null) {
            Item resultItem = matched.getResultItem();
            if (resultItem != Items.AIR) {
                return new ItemStack(resultItem, matched.getResultCountForType(matched.type));
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public List<RecipeDisplay> getDisplays() {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return Collections.emptyList();
        }

        List<RecipeDisplay> displays = new ArrayList<>();
        SlotDisplay craftingStation = new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE);

        for (CustomRecipeData recipe : config.recipes.values()) {
            if (!recipe.enabled) continue;
            Item resultItem = recipe.getResultItem();
            if (resultItem == Items.AIR) continue;

            int safeCount = Math.min(resultItem.getMaxCount(), Math.max(1, recipe.getResultCountForType(recipe.type)));
            SlotDisplay resultDisplay = new SlotDisplay.StackSlotDisplay(new ItemStack(resultItem, safeCount));

            if (recipe.type == RecipeTypeEnum.SHAPED_CRAFTING) {
                List<SlotDisplay> ingredients = new ArrayList<>(9);
                for (int i = 0; i < 9; i++) {
                    String slotStr = recipe.getSlotString(i);
                    if (slotStr == null || slotStr.isEmpty() || slotStr.equals("minecraft:air")) {
                        ingredients.add(SlotDisplay.EmptySlotDisplay.INSTANCE);
                    } else if (slotStr.startsWith("#")) {
                        Identifier tagId = Identifier.tryParse(slotStr.substring(1));
                        if (tagId != null) {
                            ingredients.add(new SlotDisplay.TagSlotDisplay(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.ITEM, tagId)));
                        } else {
                            Item item = recipe.getItemAt(i);
                            ingredients.add(item != Items.AIR ? new SlotDisplay.ItemSlotDisplay(item) : SlotDisplay.EmptySlotDisplay.INSTANCE);
                        }
                    } else {
                        Item item = recipe.getItemAt(i);
                        ingredients.add(item != Items.AIR ? new SlotDisplay.ItemSlotDisplay(item) : SlotDisplay.EmptySlotDisplay.INSTANCE);
                    }
                }
                displays.add(new ShapedCraftingRecipeDisplay(3, 3, ingredients, resultDisplay, craftingStation));
            }
        }

        return displays;
    }

    @Override
    public RecipeSerializer<? extends ShapedRecipe> getSerializer() {
        return RecipeEditorMod.RECIPE_SERIALIZER;
    }
}
