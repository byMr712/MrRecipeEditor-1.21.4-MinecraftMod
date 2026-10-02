package com.recipeeditor.recipe;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CookingRecipeCategory;
import net.minecraft.recipe.display.CuttingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CustomRecipeDispatcher {

    public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeEntry<T>> getCustomMatch(RecipeType<T> type, I input, World world) {
        List<RecipeEntry<T>> matches = getCustomMatches(type, input, world);
        if (!matches.isEmpty()) {
            return Optional.of(matches.get(0));
        }
        return Optional.empty();
    }

    public static <I extends RecipeInput, T extends Recipe<I>> List<RecipeEntry<T>> getCustomMatches(RecipeType<T> type, I input, World world) {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty() || input == null || input.isEmpty()) {
            return Collections.emptyList();
        }

        RecipeTypeEnum targetEnum = getEnumForType(type);
        if (targetEnum == null) return Collections.emptyList();

        List<RecipeEntry<T>> list = new ArrayList<>();
        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled || recipeData.type != targetEnum) continue;

            if (matchesInput(recipeData, targetEnum, input)) {
                T dynamicRecipe = createSyntheticRecipe(recipeData, targetEnum, type);
                if (dynamicRecipe != null) {
                    Identifier id = Identifier.of("recipeeditor", "dynamic_" + sanitizeId(recipeData.id));
                    RegistryKey<Recipe<?>> key = RegistryKey.of(RegistryKeys.RECIPE, id);
                    list.add(new RecipeEntry<>(key, dynamicRecipe));
                }
            }
        }
        return list;
    }

    public static <I extends RecipeInput, T extends Recipe<I>> List<RecipeEntry<T>> getAllCustomRecipesOfType(RecipeType<T> type) {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return Collections.emptyList();
        }

        RecipeTypeEnum targetEnum = getEnumForType(type);
        if (targetEnum == null) return Collections.emptyList();

        List<RecipeEntry<T>> list = new ArrayList<>();
        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled || recipeData.type != targetEnum) continue;

            T dynamicRecipe = createSyntheticRecipe(recipeData, targetEnum, type);
            if (dynamicRecipe != null) {
                Identifier id = Identifier.of("recipeeditor", "dynamic_" + sanitizeId(recipeData.id));
                RegistryKey<Recipe<?>> key = RegistryKey.of(RegistryKeys.RECIPE, id);
                list.add(new RecipeEntry<>(key, dynamicRecipe));
            }
        }
        return list;
    }

    public static CuttingRecipeDisplay.Grouping<StonecuttingRecipe> getCustomStonecutterGrouping() {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return CuttingRecipeDisplay.Grouping.empty();
        }

        List<CuttingRecipeDisplay.GroupEntry<StonecuttingRecipe>> entries = new ArrayList<>();
        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled || recipeData.type != RecipeTypeEnum.STONECUTTING) continue;

            Ingredient inputIng = recipeData.getIngredientAt(0);
            if (inputIng == null) continue;

            StonecuttingRecipe recipe = createSyntheticRecipe(recipeData, RecipeTypeEnum.STONECUTTING, RecipeType.STONECUTTING);
            if (recipe != null) {
                Identifier id = Identifier.of("recipeeditor", "dynamic_" + sanitizeId(recipeData.id));
                RegistryKey<Recipe<?>> key = RegistryKey.of(RegistryKeys.RECIPE, id);
                RecipeEntry<StonecuttingRecipe> recipeEntry = new RecipeEntry<>(key, recipe);

                int count = Math.max(1, recipeData.getResultCountForType(RecipeTypeEnum.STONECUTTING));
                ItemStack resultStack = new ItemStack(recipeData.getResultItem(), count);
                SlotDisplay optionDisplay = new SlotDisplay.StackSlotDisplay(resultStack);
                CuttingRecipeDisplay<StonecuttingRecipe> display = new CuttingRecipeDisplay<>(optionDisplay, Optional.of(recipeEntry));
                entries.add(new CuttingRecipeDisplay.GroupEntry<>(inputIng, display));
            }
        }
        return new CuttingRecipeDisplay.Grouping<>(entries);
    }

    public static List<RecipeEntry<?>> getAllCustomRecipes() {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return Collections.emptyList();
        }

        List<RecipeEntry<?>> list = new ArrayList<>();
        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled) continue;
            RecipeType<?> mcType = getMcTypeForEnum(recipeData.type);
            if (mcType == null) continue;

            Recipe<?> dynamicRecipe = createSyntheticRecipe(recipeData, recipeData.type, mcType);
            if (dynamicRecipe != null) {
                Identifier id = Identifier.of("recipeeditor", "dynamic_" + sanitizeId(recipeData.id));
                RegistryKey<Recipe<?>> key = RegistryKey.of(RegistryKeys.RECIPE, id);
                list.add(new RecipeEntry<>(key, dynamicRecipe));
            }
        }
        return list;
    }

    private static String sanitizeId(String id) {
        if (id == null || id.isEmpty()) return "craft";
        return id.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
    }

    public static RecipeTypeEnum getEnumForType(RecipeType<?> type) {
        if (type == RecipeType.CRAFTING) return RecipeTypeEnum.SHAPED_CRAFTING;
        if (type == RecipeType.SMELTING) return RecipeTypeEnum.SMELTING;
        if (type == RecipeType.BLASTING) return RecipeTypeEnum.BLASTING;
        if (type == RecipeType.SMOKING) return RecipeTypeEnum.SMOKING;
        if (type == RecipeType.STONECUTTING) return RecipeTypeEnum.STONECUTTING;
        if (type == RecipeType.SMITHING) return RecipeTypeEnum.SMITHING;
        if (type == RecipeType.CAMPFIRE_COOKING) return RecipeTypeEnum.CAMPFIRE_COOKING;
        return null;
    }

    public static RecipeType<?> getMcTypeForEnum(RecipeTypeEnum typeEnum) {
        if (typeEnum == null) return null;
        return switch (typeEnum) {
            case SHAPED_CRAFTING -> RecipeType.CRAFTING;
            case SMELTING -> RecipeType.SMELTING;
            case BLASTING -> RecipeType.BLASTING;
            case SMOKING -> RecipeType.SMOKING;
            case STONECUTTING -> RecipeType.STONECUTTING;
            case SMITHING -> RecipeType.SMITHING;
            case CAMPFIRE_COOKING -> RecipeType.CAMPFIRE_COOKING;
            default -> null;
        };
    }

    private static boolean matchesInput(CustomRecipeData data, RecipeTypeEnum typeEnum, RecipeInput input) {
        if (input instanceof SingleStackRecipeInput single) {
            ItemStack stack = single.item();
            if (stack.isEmpty()) return false;
            Ingredient ing = data.getIngredientAt(0);
            return ing != null && ing.test(stack);
        }

        if (input instanceof SmithingRecipeInput smithing) {
            Ingredient templateIng = data.getIngredientAt(0);
            Ingredient baseIng = data.getIngredientAt(1);
            Ingredient additionIng = data.getIngredientAt(2);

            boolean matchTemplate = (templateIng == null && smithing.template().isEmpty()) ||
                    (templateIng != null && templateIng.test(smithing.template()));
            boolean matchBase = (baseIng == null && smithing.base().isEmpty()) ||
                    (baseIng != null && baseIng.test(smithing.base()));
            boolean matchAddition = (additionIng == null && smithing.addition().isEmpty()) ||
                    (additionIng != null && additionIng.test(smithing.addition()));

            return matchTemplate && matchBase && matchAddition;
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private static <I extends RecipeInput, T extends Recipe<I>> T createSyntheticRecipe(CustomRecipeData data, RecipeTypeEnum typeEnum, RecipeType<?> recipeType) {
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
