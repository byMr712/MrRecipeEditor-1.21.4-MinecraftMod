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
                if (matchesCrafting(input, recipeData)) {
                    return recipeData;
                }
            }
        }
        return null;
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
        int inputSize = input.size();
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

        int inputW = input.getWidth();
        int inputH = input.getHeight();

        if (inputW < patternW || inputH < patternH) {
            return false;
        }

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
    public net.minecraft.util.collection.DefaultedList<ItemStack> getRecipeRemainders(CraftingRecipeInput input) {
        return CraftingRecipe.collectRecipeRemainders(input);
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
    }

    private volatile List<RecipeDisplay> cachedDisplays = null;
    private volatile int cachedDisplaysHash = 0;

    @Override
    public List<RecipeDisplay> getDisplays() {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return Collections.emptyList();
        }

        int version = DISPLAY_VERSION.get();
        int configHash = (config.recipes != null ? config.recipes.hashCode() : 0) ^ (config.modEnabled ? 1 : 0) ^ version;
        List<RecipeDisplay> cached = cachedDisplays;
        if (cached != null && cachedDisplaysHash == configHash) {
            return cached;
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
                if (recipe.isShapeless) {
                    List<SlotDisplay> ingredients = new ArrayList<>();
                    for (int i = 0; i < 9; i++) {
                        String slotStr = recipe.getSlotString(i);
                        if (slotStr != null && !slotStr.isEmpty() && !slotStr.equals("minecraft:air")) {
                            if (slotStr.startsWith("#")) {
                                Identifier tagId = Identifier.tryParse(slotStr.substring(1));
                                if (tagId != null) {
                                    ingredients.add(new SlotDisplay.TagSlotDisplay(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.ITEM, tagId)));
                                } else {
                                    Item item = recipe.getItemAt(i);
                                    if (item != Items.AIR) ingredients.add(new SlotDisplay.ItemSlotDisplay(item));
                                }
                            } else {
                                Item item = recipe.getItemAt(i);
                                if (item != Items.AIR) ingredients.add(new SlotDisplay.ItemSlotDisplay(item));
                            }
                        }
                    }
                    displays.add(new ShapelessCraftingRecipeDisplay(ingredients, resultDisplay, craftingStation));
                } else {
                    int minRow = 3, maxRow = -1, minCol = 3, maxCol = -1;
                    for (int r = 0; r < 3; r++) {
                        for (int c = 0; c < 3; c++) {
                            int idx = r * 3 + c;
                            String s = recipe.getSlotString(idx);
                            if (s != null && !s.isEmpty() && !s.equals("minecraft:air")) {
                                minRow = Math.min(minRow, r);
                                maxRow = Math.max(maxRow, r);
                                minCol = Math.min(minCol, c);
                                maxCol = Math.max(maxCol, c);
                            }
                        }
                    }

                    if (minRow <= maxRow && minCol <= maxCol) {
                        int patternW = maxCol - minCol + 1;
                        int patternH = maxRow - minRow + 1;
                        List<SlotDisplay> ingredients = new ArrayList<>(patternW * patternH);
                        for (int r = minRow; r <= maxRow; r++) {
                            for (int c = minCol; c <= maxCol; c++) {
                                int i = r * 3 + c;
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
                        }
                        displays.add(new ShapedCraftingRecipeDisplay(patternW, patternH, ingredients, resultDisplay, craftingStation));
                    }
                }
            }
        }

        cachedDisplays = Collections.unmodifiableList(displays);
        cachedDisplaysHash = configHash;
        return cachedDisplays;
    }

    @Override
    public RecipeSerializer<? extends ShapedRecipe> getSerializer() {
        return RecipeEditorMod.RECIPE_SERIALIZER;
    }
}
