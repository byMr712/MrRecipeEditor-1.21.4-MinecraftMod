package com.recipeeditor.recipe;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CookingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Optional;

public class CustomRecipeDispatcher {

    public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeEntry<T>> getCustomMatch(RecipeType<T> type, I input, World world) {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty() || input == null || input.isEmpty()) {
            return Optional.empty();
        }

        RecipeTypeEnum targetEnum = getEnumForType(type);
        if (targetEnum == null) return Optional.empty();

        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled || recipeData.type != targetEnum) continue;

            if (matchesInput(recipeData, targetEnum, input)) {
                T dynamicRecipe = createSyntheticRecipe(recipeData, targetEnum, type);
                if (dynamicRecipe != null) {
                    Identifier id = Identifier.of("recipeeditor", "dynamic_" + sanitizeId(recipeData.id));
                    RegistryKey<Recipe<?>> key = RegistryKey.of(RegistryKeys.RECIPE, id);
                    return Optional.of(new RecipeEntry<>(key, dynamicRecipe));
                }
            }
        }
        return Optional.empty();
    }

    private static String sanitizeId(String id) {
        if (id == null || id.isEmpty()) return "craft";
        return id.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
    }

    private static RecipeTypeEnum getEnumForType(RecipeType<?> type) {
        if (type == RecipeType.CRAFTING) return RecipeTypeEnum.SHAPED_CRAFTING;
        if (type == RecipeType.SMELTING) return RecipeTypeEnum.SMELTING;
        if (type == RecipeType.BLASTING) return RecipeTypeEnum.BLASTING;
        if (type == RecipeType.SMOKING) return RecipeTypeEnum.SMOKING;
        if (type == RecipeType.STONECUTTING) return RecipeTypeEnum.STONECUTTING;
        if (type == RecipeType.SMITHING) return RecipeTypeEnum.SMITHING;
        if (type == RecipeType.CAMPFIRE_COOKING) return RecipeTypeEnum.CAMPFIRE_COOKING;
        return null;
    }

    private static boolean matchesInput(CustomRecipeData data, RecipeTypeEnum typeEnum, RecipeInput input) {
        if (input instanceof SingleStackRecipeInput single) {
            Item expected = data.getItemAt(0);
            return expected != Items.AIR && single.item().isOf(expected);
        }

        if (input instanceof SmithingRecipeInput smithing) {
            Item template = data.getItemAt(0);
            Item base = data.getItemAt(1);
            Item addition = data.getItemAt(2);

            boolean matchTemplate = (template == Items.AIR && smithing.template().isEmpty()) || (template != Items.AIR && smithing.template().isOf(template));
            boolean matchBase = (base == Items.AIR && smithing.base().isEmpty()) || (base != Items.AIR && smithing.base().isOf(base));
            boolean matchAddition = (addition == Items.AIR && smithing.addition().isEmpty()) || (addition != Items.AIR && smithing.addition().isOf(addition));

            return matchTemplate && matchBase && matchAddition;
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private static <I extends RecipeInput, T extends Recipe<I>> T createSyntheticRecipe(CustomRecipeData data, RecipeTypeEnum typeEnum, RecipeType<T> recipeType) {
        Item resultItem = data.getResultItem();
        if (resultItem == Items.AIR) return null;

        int safeCount = Math.min(resultItem.getMaxCount(), Math.max(1, data.getResultCountForType(typeEnum)));
        ItemStack resultStack = new ItemStack(resultItem, safeCount);

        if (typeEnum == RecipeTypeEnum.SMELTING) {
            Ingredient ing = data.getIngredientAt(0);
            if (ing == null) return null;
            return (T) new SmeltingRecipe(data.id, CookingRecipeCategory.MISC, ing, resultStack, data.experience, data.cookingTime);
        } else if (typeEnum == RecipeTypeEnum.BLASTING) {
            Ingredient ing = data.getIngredientAt(0);
            if (ing == null) return null;
            return (T) new BlastingRecipe(data.id, CookingRecipeCategory.MISC, ing, resultStack, data.experience, Math.max(20, data.cookingTime / 2));
        } else if (typeEnum == RecipeTypeEnum.SMOKING) {
            Ingredient ing = data.getIngredientAt(0);
            if (ing == null) return null;
            return (T) new SmokingRecipe(data.id, CookingRecipeCategory.MISC, ing, resultStack, data.experience, Math.max(20, data.cookingTime / 2));
        } else if (typeEnum == RecipeTypeEnum.CAMPFIRE_COOKING) {
            Ingredient ing = data.getIngredientAt(0);
            if (ing == null) return null;
            return (T) new CampfireCookingRecipe(data.id, CookingRecipeCategory.MISC, ing, resultStack, data.experience, data.cookingTime * 3);
        } else if (typeEnum == RecipeTypeEnum.STONECUTTING) {
            Ingredient ing = data.getIngredientAt(0);
            if (ing == null) return null;
            return (T) new StonecuttingRecipe(data.id, ing, resultStack);
        } else if (typeEnum == RecipeTypeEnum.SMITHING) {
            Optional<Ingredient> template = data.createIngredientForSlot(0);
            Optional<Ingredient> base = data.createIngredientForSlot(1);
            Optional<Ingredient> addition = data.createIngredientForSlot(2);
            return (T) new SmithingTransformRecipe(template, base, addition, new net.minecraft.item.ItemStack(resultItem, safeCount));
        }

        return null;
    }
}
