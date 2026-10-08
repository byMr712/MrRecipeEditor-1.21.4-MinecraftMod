package com.recipeeditor.recipe;

import com.recipeeditor.RecipeEditorMod;
import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

import java.util.*;

public class CustomDynamicCraftingRecipe extends CustomRecipe {

    public CustomDynamicCraftingRecipe(CraftingBookCategory category) {
        super();
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    private static volatile CraftingInput lastInput = null;
    private static volatile CustomRecipeData lastMatchedRecipe = null;
    private static volatile int lastInputConfigVersion = -1;

    private static synchronized CustomRecipeData findMatchingRecipe(CraftingInput input) {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty() || input.isEmpty()) {
            return null;
        }

        int currentVer = config.configVersion;
        CraftingInput prevInput = lastInput;
        if (prevInput != null && lastInputConfigVersion == currentVer && input.equals(prevInput)) {
            return lastMatchedRecipe;
        }

        CustomRecipeData matched = null;
        List<CustomRecipeData> craftingRecipes = config.getSortedCraftingRecipes();
        for (int i = 0; i < craftingRecipes.size(); i++) {
            CustomRecipeData recipeData = craftingRecipes.get(i);
            if (matchesCrafting(input, recipeData)) {
                matched = recipeData;
                break;
            }
        }

        lastInput = input;
        lastMatchedRecipe = matched;
        lastInputConfigVersion = currentVer;
        return matched;
    }

    public static boolean matchesCrafting(CraftingInput input, CustomRecipeData recipeData) {
        if (recipeData == null || recipeData.type != RecipeTypeEnum.SHAPED_CRAFTING) return false;
        if (recipeData.isShapeless) {
            return matchesShapeless(input, recipeData);
        } else {
            return matchesShaped(input, recipeData);
        }
    }

    public static boolean matchesShapeless(CraftingInput input, CustomRecipeData recipeData) {
        if (input.isEmpty()) return false;

        List<Ingredient> ingredients = recipeData.getNonEmptyIngredients();
        int expectedCount = ingredients.size();
        if (expectedCount == 0 || input.ingredientCount() != expectedCount) {
            return false;
        }

        ItemStack[] inputStacks = new ItemStack[expectedCount];
        int count = 0;
        int inputSize = input.size();
        for (int i = 0; i < inputSize; i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                if (count >= expectedCount) return false;
                inputStacks[count++] = stack;
            }
        }
        if (count != expectedCount) return false;

        return matchShapelessBacktrack(inputStacks, ingredients, 0, new boolean[expectedCount]);
    }

    private static boolean matchShapelessBacktrack(ItemStack[] stacks, List<Ingredient> ingredients,
                                             int stackIdx, boolean[] usedIngs) {
        if (stackIdx >= stacks.length) {
            return true;
        }

        ItemStack currentStack = stacks[stackIdx];
        for (int i = 0; i < ingredients.size(); i++) {
            if (!usedIngs[i] && ingredients.get(i).test(currentStack)) {
                usedIngs[i] = true;
                if (matchShapelessBacktrack(stacks, ingredients, stackIdx + 1, usedIngs)) {
                    return true;
                }
                usedIngs[i] = false;
            }
        }
        return false;
    }

    public static boolean matchesShaped(CraftingInput input, CustomRecipeData recipeData) {
        if (input.isEmpty()) return false;

        int patternW = recipeData.getPatternWidth();
        int patternH = recipeData.getPatternHeight();
        if (patternW == 0 || patternH == 0) {
            return false;
        }

        int inputW = input.width();
        int inputH = input.height();

        if (inputW < patternW || inputH < patternH) {
            return false;
        }

        int minRow = recipeData.getMinRow();
        int minCol = recipeData.getMinCol();
        int maxCol = recipeData.getMaxCol();

        for (int dx = 0; dx <= inputW - patternW; dx++) {
            for (int dy = 0; dy <= inputH - patternH; dy++) {
                if (checkMatchAt(input, recipeData, minRow, minCol, maxCol, patternW, patternH, dx, dy, false)) {
                    return true;
                }
                if (checkMatchAt(input, recipeData, minRow, minCol, maxCol, patternW, patternH, dx, dy, true)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean checkMatchAt(CraftingInput input, CustomRecipeData recipeData,
                                 int minRow, int minCol, int maxCol, int patternW, int patternH,
                                 int dx, int dy, boolean mirrored) {
        int inputW = input.width();
        int inputH = input.height();

        for (int ir = 0; ir < inputH; ir++) {
            for (int ic = 0; ic < inputW; ic++) {
                int pr = ir - dy;
                int pc = ic - dx;

                Ingredient expected = null;
                if (pr >= 0 && pr < patternH && pc >= 0 && pc < patternW) {
                    int slotCol = mirrored ? (maxCol - pc) : (minCol + pc);
                    int slotIdx = (minRow + pr) * 3 + slotCol;
                    expected = recipeData.getIngredientAt(slotIdx);
                }

                ItemStack actual = input.getItem(ic, ir);
                if (expected == null) {
                    if (!actual.isEmpty()) {
                        return false;
                    }
                } else {
                    if (!expected.test(actual)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findMatchingRecipe(input) != null;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return CraftingRecipe.defaultCraftingReminder(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        CustomRecipeData matched = findMatchingRecipe(input);
        if (matched != null) {
            Item resultItem = matched.getResultItem();
            if (resultItem != Items.AIR) {
                int safeCount = Math.min(resultItem.getDefaultInstance().getMaxStackSize(), Math.max(1, matched.getResultCountForType(matched.type)));
                return new ItemStack(resultItem, safeCount);
            }
        }
        return ItemStack.EMPTY;
    }

    private static final java.util.concurrent.atomic.AtomicInteger DISPLAY_VERSION = new java.util.concurrent.atomic.AtomicInteger(0);

    public static void invalidateDisplayCache() {
        DISPLAY_VERSION.incrementAndGet();
        lastInput = null;
        lastMatchedRecipe = null;
        lastInputConfigVersion = -1;
    }

    @Override
    public List<RecipeDisplay> display() {
        return Collections.emptyList();
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return RecipeEditorMod.CUSTOM_CRAFTING_SERIALIZER;
    }
}
