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
        Map<Character, Ingredient> key = Map.of('A', Ingredient.ofItems(Items.DIRT));
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
    public CraftingRecipeCategory getCategory() {
        return CraftingRecipeCategory.MISC;
    }

    private static volatile CraftingRecipeInput lastInput = null;
    private static volatile CustomRecipeData lastMatchedRecipe = null;
    private static volatile int lastInputConfigVersion = -1;

    private static synchronized CustomRecipeData findMatchingRecipe(CraftingRecipeInput input) {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty() || input.isEmpty()) {
            return null;
        }

        int currentVer = config.configVersion;
        CraftingRecipeInput prevInput = lastInput;
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

    public static boolean matchesCrafting(CraftingRecipeInput input, CustomRecipeData recipeData) {
        if (recipeData == null || recipeData.type != RecipeTypeEnum.SHAPED_CRAFTING) return false;
        if (recipeData.isShapeless) {
            return matchesShapeless(input, recipeData);
        } else {
            return matchesShaped(input, recipeData);
        }
    }

    public static boolean matchesShapeless(CraftingRecipeInput input, CustomRecipeData recipeData) {
        if (input.isEmpty()) return false;

        List<Ingredient> ingredients = recipeData.getNonEmptyIngredients();
        int expectedCount = ingredients.size();
        if (expectedCount == 0 || input.getStackCount() != expectedCount) {
            return false;
        }

        ItemStack[] inputStacks = new ItemStack[expectedCount];
        int count = 0;
        int inputSize = input.getSize();
        for (int i = 0; i < inputSize; i++) {
            ItemStack stack = input.getStackInSlot(i);
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

    public static boolean matchesShaped(CraftingRecipeInput input, CustomRecipeData recipeData) {
        if (input.isEmpty()) return false;

        int patternW = recipeData.getPatternWidth();
        int patternH = recipeData.getPatternHeight();
        if (patternW == 0 || patternH == 0) {
            return false;
        }

        int inputW = input.getWidth();
        int inputH = input.getHeight();

        if (inputW < patternW || inputH < patternH) {
            return false;
        }

        int minRow = recipeData.getMinRow();
        int minCol = recipeData.getMinCol();
        int maxCol = recipeData.getMaxCol();

        // Test all possible positions (dx, dy) where pattern can fit in the crafting input grid
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

    private static boolean checkMatchAt(CraftingRecipeInput input, CustomRecipeData recipeData,
                                 int minRow, int minCol, int maxCol, int patternW, int patternH,
                                 int dx, int dy, boolean mirrored) {
        int inputW = input.getWidth();
        int inputH = input.getHeight();

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

                ItemStack actual = input.getStackInSlot(ic, ir);
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
    public boolean matches(CraftingRecipeInput input, World world) {
        return findMatchingRecipe(input) != null;
    }

    @Override
    public net.minecraft.util.collection.DefaultedList<ItemStack> getRemainder(CraftingRecipeInput input) {
        net.minecraft.util.collection.DefaultedList<ItemStack> remainders = net.minecraft.util.collection.DefaultedList.ofSize(input.getSize(), ItemStack.EMPTY);
        for (int i = 0; i < remainders.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            Item item = stack.getItem();
            if (item.hasRecipeRemainder()) {
                remainders.set(i, new ItemStack(item.getRecipeRemainder()));
            }
        }
        return remainders;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        CustomRecipeData matched = findMatchingRecipe(input);
        if (matched != null) {
            Item resultItem = matched.getResultItem();
            if (resultItem != Items.AIR) {
                int safeCount = Math.min(resultItem.getMaxCount(), Math.max(1, matched.getResultCountForType(matched.type)));
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
    public RecipeSerializer<? extends ShapedRecipe> getSerializer() {
        return RecipeEditorMod.RECIPE_SERIALIZER;
    }
}
