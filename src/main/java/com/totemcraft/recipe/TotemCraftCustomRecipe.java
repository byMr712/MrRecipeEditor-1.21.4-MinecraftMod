package com.totemcraft.recipe;

import com.totemcraft.TotemCraftMod;
import com.totemcraft.config.TotemCraftConfig;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.IngredientPlacement;
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.book.RecipeBookCategories;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TotemCraftCustomRecipe extends ShapedRecipe {

    public TotemCraftCustomRecipe(CraftingRecipeCategory category) {
        super("totemcraft", category, createDefaultRaw(), new ItemStack(Items.TOTEM_OF_UNDYING), true);
    }

    private static RawShapedRecipe createDefaultRaw() {
        Map<Character, Ingredient> key = Map.of(
                'A', Ingredient.ofItem(Items.GOLDEN_APPLE),
                'G', Ingredient.ofItem(Items.GHAST_TEAR)
        );
        return RawShapedRecipe.create(key, "AAA", "AGA", "AAA");
    }

    private RawShapedRecipe cachedRawRecipe = null;
    private int cachedHash = 0;

    public RawShapedRecipe getRawRecipe() {
        TotemCraftConfig config = TotemCraftConfig.getInstance();
        int hash = java.util.Arrays.hashCode(config.patternSlots);
        if (cachedRawRecipe == null || cachedHash != hash) {
            List<Optional<Ingredient>> ingredients = new ArrayList<>(9);
            for (int i = 0; i < 9; i++) {
                Item item = config.getItemAt(i);
                if (item == Items.AIR) {
                    ingredients.add(Optional.empty());
                } else {
                    ingredients.add(Optional.of(Ingredient.ofItem(item)));
                }
            }
            cachedRawRecipe = new RawShapedRecipe(3, 3, ingredients, Optional.empty());
            cachedHash = hash;
        }
        return cachedRawRecipe;
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

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        TotemCraftConfig config = TotemCraftConfig.getInstance();
        if (!config.enabled) {
            return false;
        }
        return getRawRecipe().matches(input);
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        TotemCraftConfig config = TotemCraftConfig.getInstance();
        return new ItemStack(config.getResultItem(), config.resultCount);
    }

    @Override
    public List<Optional<Ingredient>> getIngredients() {
        return getRawRecipe().getIngredients();
    }

    @Override
    public IngredientPlacement getIngredientPlacement() {
        return IngredientPlacement.forMultipleSlots(getIngredients());
    }

    @Override
    public List<RecipeDisplay> getDisplays() {
        TotemCraftConfig config = TotemCraftConfig.getInstance();
        if (!config.enabled) {
            return Collections.emptyList();
        }

        List<SlotDisplay> ingredients = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            Item item = config.getItemAt(i);
            if (item == Items.AIR) {
                ingredients.add(SlotDisplay.EmptySlotDisplay.INSTANCE);
            } else {
                ingredients.add(new SlotDisplay.ItemSlotDisplay(item));
            }
        }

        SlotDisplay result = new SlotDisplay.StackSlotDisplay(new ItemStack(config.getResultItem(), config.resultCount));
        SlotDisplay craftingStation = new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE);

        return Collections.singletonList(new ShapedCraftingRecipeDisplay(3, 3, ingredients, result, craftingStation));
    }

    @Override
    public RecipeSerializer<? extends ShapedRecipe> getSerializer() {
        return TotemCraftMod.RECIPE_SERIALIZER;
    }
}
