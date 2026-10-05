package com.recipeeditor.integration.rei;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
import com.recipeeditor.recipe.CustomRecipeDispatcher;
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.plugin.common.BuiltinPlugin;
import me.shedaniel.rei.plugin.common.displays.DefaultCampfireDisplay;
import me.shedaniel.rei.plugin.common.displays.DefaultSmithingDisplay;
import me.shedaniel.rei.plugin.common.displays.DefaultStoneCuttingDisplay;
import me.shedaniel.rei.plugin.common.displays.cooking.DefaultBlastingDisplay;
import me.shedaniel.rei.plugin.common.displays.cooking.DefaultSmeltingDisplay;
import me.shedaniel.rei.plugin.common.displays.cooking.DefaultSmokingDisplay;
import me.shedaniel.rei.plugin.common.displays.crafting.DefaultCustomShapedDisplay;
import me.shedaniel.rei.plugin.common.displays.crafting.DefaultCustomShapelessDisplay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

@Environment(EnvType.CLIENT)
public class RecipeEditorDynamicDisplayGenerator implements DynamicDisplayGenerator<Display> {

    @Override
    public Optional<List<Display>> getRecipeFor(EntryStack<?> entry) {
        if (entry == null || entry.getType() != VanillaEntryTypes.ITEM) {
            return Optional.empty();
        }
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return Optional.empty();
        }

        ItemStack stack = entry.castValue();
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        Item item = stack.getItem();
        Identifier itemId = Registries.ITEM.getId(item);
        if (itemId == null) {
            return Optional.empty();
        }
        String itemIdStr = itemId.toString();

        List<Display> result = new ArrayList<>();
        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled) continue;
            if (itemIdStr.equals(recipeData.resultItemId) || recipeData.getResultItem() == item) {
                Display d = createDisplay(recipeData);
                if (d != null) {
                    result.add(d);
                }
            }
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    @Override
    public Optional<List<Display>> getUsageFor(EntryStack<?> entry) {
        if (entry == null || entry.getType() != VanillaEntryTypes.ITEM) {
            return Optional.empty();
        }
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return Optional.empty();
        }

        ItemStack stack = entry.castValue();
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }

        List<Display> result = new ArrayList<>();
        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled) continue;
            if (recipeMatchesIngredient(recipeData, stack)) {
                Display d = createDisplay(recipeData);
                if (d != null) {
                    result.add(d);
                }
            }
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    @Override
    public Optional<List<Display>> generate(ViewSearchBuilder builder) {
        if (!builder.getRecipesFor().isEmpty() || !builder.getUsagesFor().isEmpty()) {
            return Optional.empty();
        }

        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return Optional.empty();
        }

        Collection<CategoryIdentifier<?>> requested = builder.getCategories();
        List<Display> result = new ArrayList<>();
        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled) continue;
            CategoryIdentifier<?> cat = getCategoryForEnum(recipeData.type);
            if (cat != null && (requested.isEmpty() || requested.contains(cat))) {
                Display d = createDisplay(recipeData);
                if (d != null) {
                    result.add(d);
                }
            }
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    public static CategoryIdentifier<?> getCategoryForEnum(RecipeTypeEnum type) {
        if (type == null) return null;
        return switch (type) {
            case SHAPED_CRAFTING -> BuiltinPlugin.CRAFTING;
            case SMELTING -> BuiltinPlugin.SMELTING;
            case BLASTING -> BuiltinPlugin.BLASTING;
            case SMOKING -> BuiltinPlugin.SMOKING;
            case CAMPFIRE_COOKING -> BuiltinPlugin.CAMPFIRE;
            case STONECUTTING -> BuiltinPlugin.STONE_CUTTING;
            case SMITHING -> BuiltinPlugin.SMITHING;
        };
    }

    private static boolean recipeMatchesIngredient(CustomRecipeData recipeData, ItemStack stack) {
        if (recipeData.type == RecipeTypeEnum.SHAPED_CRAFTING) {
            for (int i = 0; i < 9; i++) {
                Ingredient ing = recipeData.getIngredientAt(i);
                if (ing != null && ing.test(stack)) {
                    return true;
                }
            }
        } else if (recipeData.type == RecipeTypeEnum.SMITHING) {
            for (int i = 0; i < 3; i++) {
                Ingredient ing = recipeData.getIngredientAt(i);
                if (ing != null && ing.test(stack)) {
                    return true;
                }
            }
        } else {
            Ingredient ing = recipeData.getIngredientAt(0);
            if (ing != null && ing.test(stack)) {
                return true;
            }
        }
        return false;
    }

    public static Display createDisplay(CustomRecipeData data) {
        if (data == null || data.type == null) return null;
        Item resultItem = data.getResultItem();
        if (resultItem == Items.AIR) return null;

        int safeCount = Math.min(resultItem.getMaxCount(), Math.max(1, data.getResultCountForType(data.type)));
        ItemStack resultStack = new ItemStack(resultItem, safeCount);
        EntryIngredient output = EntryIngredients.of(resultStack);
        Optional<Identifier> location = Optional.of(CustomRecipeDispatcher.getRecipeIdentifier(data));

        return switch (data.type) {
            case SHAPED_CRAFTING -> {
                if (data.isShapeless) {
                    List<EntryIngredient> inputs = new ArrayList<>();
                    for (Ingredient ing : data.getNonEmptyIngredients()) {
                        inputs.add(EntryIngredients.ofIngredient(ing));
                    }
                    yield new DefaultCustomShapelessDisplay(inputs, List.of(output), location);
                } else {
                    List<EntryIngredient> inputs = new ArrayList<>(9);
                    for (int i = 0; i < 9; i++) {
                        Ingredient ing = data.getIngredientAt(i);
                        if (ing != null && !ing.isEmpty()) {
                            inputs.add(EntryIngredients.ofIngredient(ing));
                        } else {
                            inputs.add(EntryIngredient.empty());
                        }
                    }
                    yield new DefaultCustomShapedDisplay(inputs, List.of(output), location, 3, 3);
                }
            }
            case SMELTING -> {
                Ingredient ing = data.getIngredientAt(0);
                if (ing == null) yield null;
                yield new DefaultSmeltingDisplay(List.of(EntryIngredients.ofIngredient(ing)), List.of(output), location, data.experience, Math.max(1, data.cookingTime));
            }
            case BLASTING -> {
                Ingredient ing = data.getIngredientAt(0);
                if (ing == null) yield null;
                yield new DefaultBlastingDisplay(List.of(EntryIngredients.ofIngredient(ing)), List.of(output), location, data.experience, Math.max(1, data.cookingTime));
            }
            case SMOKING -> {
                Ingredient ing = data.getIngredientAt(0);
                if (ing == null) yield null;
                yield new DefaultSmokingDisplay(List.of(EntryIngredients.ofIngredient(ing)), List.of(output), location, data.experience, Math.max(1, data.cookingTime));
            }
            case CAMPFIRE_COOKING -> {
                Ingredient ing = data.getIngredientAt(0);
                if (ing == null) yield null;
                yield new DefaultCampfireDisplay(List.of(EntryIngredients.ofIngredient(ing)), List.of(output), location, Math.max(1, data.cookingTime));
            }
            case STONECUTTING -> {
                Ingredient ing = data.getIngredientAt(0);
                if (ing == null) yield null;
                yield new DefaultStoneCuttingDisplay(List.of(EntryIngredients.ofIngredient(ing)), List.of(output), location);
            }
            case SMITHING -> {
                List<EntryIngredient> inputs = new ArrayList<>(3);
                for (int i = 0; i < 3; i++) {
                    Ingredient ing = data.getIngredientAt(i);
                    inputs.add(ing != null ? EntryIngredients.ofIngredient(ing) : EntryIngredient.empty());
                }
                yield new DefaultSmithingDisplay(inputs, List.of(output), location);
            }
        };
    }
}
