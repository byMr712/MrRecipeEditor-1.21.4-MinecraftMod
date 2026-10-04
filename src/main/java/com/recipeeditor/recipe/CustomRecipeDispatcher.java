package com.recipeeditor.recipe;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CookingRecipeCategory;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.display.CuttingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class CustomRecipeDispatcher {
    private static volatile Set<String> cachedOverriddenIds = null;
    private static volatile int lastConfigVersion = -1;

    public static synchronized void invalidateOverriddenCache() {
        cachedOverriddenIds = null;
        lastConfigVersion = -1;
    }

    private static synchronized void updateOverriddenCaches(RecipeEditorConfig config) {
        Set<String> ids = new HashSet<>();
        if (config != null && config.recipes != null) {
            for (CustomRecipeData recipeData : config.recipes.values()) {
                if (!recipeData.enabled || !recipeData.overrideExisting) continue;
                if (recipeData.overriddenId != null && !recipeData.overriddenId.isEmpty()) {
                    ids.add(recipeData.overriddenId);
                    if (recipeData.overriddenId.startsWith("minecraft:")) {
                        ids.add(recipeData.overriddenId.substring("minecraft:".length()));
                    }
                }
            }
        }
        cachedOverriddenIds = ids;
    }

    public static boolean isRecipeOverridden(RecipeEntry<?> entry) {
        if (entry == null || entry.id() == null) return false;
        Identifier entryId = entry.id().getValue();
        if (entryId == null || "recipeeditor".equals(entryId.getNamespace())) return false;

        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return false;
        }

        int currentVer = config.configVersion;
        if (cachedOverriddenIds == null || lastConfigVersion != currentVer) {
            updateOverriddenCaches(config);
            lastConfigVersion = currentVer;
        }

        if (cachedOverriddenIds == null || cachedOverriddenIds.isEmpty()) {
            return false;
        }

        String fullId = entryId.toString();
        if (cachedOverriddenIds.contains(fullId)) {
            return true;
        }

        if ("minecraft".equals(entryId.getNamespace())) {
            String path = entryId.getPath();
            return cachedOverriddenIds.contains(path);
        }

        return false;
    }

    public static boolean isIdOverridden(String fullOrShortId) {
        if (fullOrShortId == null || fullOrShortId.isEmpty()) return false;
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return false;
        }

        int currentVer = config.configVersion;
        if (cachedOverriddenIds == null || lastConfigVersion != currentVer) {
            updateOverriddenCaches(config);
            lastConfigVersion = currentVer;
        }

        if (cachedOverriddenIds == null || cachedOverriddenIds.isEmpty()) {
            return false;
        }

        if (cachedOverriddenIds.contains(fullOrShortId)) {
            return true;
        }

        if (fullOrShortId.startsWith("minecraft:")) {
            return cachedOverriddenIds.contains(fullOrShortId.substring("minecraft:".length()));
        }

        return false;
    }

    public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeEntry<T>> getCustomMatch(RecipeType<T> type, I input, World world) {
        List<RecipeEntry<T>> matches = getCustomMatches(type, input, world);
        if (!matches.isEmpty()) {
            return Optional.of(matches.get(0));
        }
        return Optional.empty();
    }

    private static final java.util.Map<String, RecipeEntry<?>> SYNTHETIC_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static void clearSyntheticCache() {
        SYNTHETIC_CACHE.clear();
    }

    @SuppressWarnings("unchecked")
    public static <I extends RecipeInput, T extends Recipe<I>> RecipeEntry<T> getCachedSyntheticEntry(CustomRecipeData recipeData, RecipeTypeEnum targetEnum, RecipeType<T> type) {
        String cacheKey = recipeData.getKey() + "#" + recipeData.hashCode() + "#" + targetEnum.name();
        return (RecipeEntry<T>) SYNTHETIC_CACHE.computeIfAbsent(cacheKey, k -> {
            T dynamicRecipe = createSyntheticRecipe(recipeData, targetEnum, type);
            if (dynamicRecipe != null) {
                Identifier id = getRecipeIdentifier(recipeData);
                RegistryKey<Recipe<?>> key = RegistryKey.of(RegistryKeys.RECIPE, id);
                return new RecipeEntry<>(key, dynamicRecipe);
            }
            return null;
        });
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
                RecipeEntry<T> entry = getCachedSyntheticEntry(recipeData, targetEnum, type);
                if (entry != null) {
                    list.add(entry);
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

            RecipeEntry<T> entry = getCachedSyntheticEntry(recipeData, targetEnum, type);
            if (entry != null) {
                list.add(entry);
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

            RecipeEntry<StonecuttingRecipe> recipeEntry = getCachedSyntheticEntry(recipeData, RecipeTypeEnum.STONECUTTING, RecipeType.STONECUTTING);
            if (recipeEntry != null) {
                int count = Math.max(1, recipeData.getResultCountForType(RecipeTypeEnum.STONECUTTING));
                ItemStack resultStack = new ItemStack(recipeData.getResultItem(), count);
                SlotDisplay optionDisplay = new SlotDisplay.StackSlotDisplay(resultStack);
                CuttingRecipeDisplay<StonecuttingRecipe> display = new CuttingRecipeDisplay<>(optionDisplay, Optional.of(recipeEntry));
                entries.add(new CuttingRecipeDisplay.GroupEntry<>(inputIng, display));
            }
        }
        return new CuttingRecipeDisplay.Grouping<>(entries);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
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

            RecipeEntry<?> entry = getCachedSyntheticEntry(recipeData, recipeData.type, (RecipeType) mcType);
            if (entry != null) {
                list.add(entry);
            }
        }
        return list;
    }

    private static Identifier getRecipeIdentifier(CustomRecipeData recipeData) {
        String base = sanitizeId(recipeData.id);
        String sigHash = Integer.toHexString(Math.abs(recipeData.getKey().hashCode()));
        return Identifier.of("recipeeditor", "dynamic_" + base + "_" + sigHash);
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
        if (input instanceof net.minecraft.recipe.input.CraftingRecipeInput crafting) {
            return CustomDynamicCraftingRecipe.matchesCrafting(crafting, data);
        }

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

        if (typeEnum == RecipeTypeEnum.SHAPED_CRAFTING) {
            if (data.isShapeless) {
                net.minecraft.util.collection.DefaultedList<Ingredient> ingredients = net.minecraft.util.collection.DefaultedList.of();
                ingredients.addAll(data.getNonEmptyIngredients());
                return (T) new ShapelessRecipe(data.id, CraftingRecipeCategory.MISC, resultStack, ingredients);
            }
            RawShapedRecipe raw = data.getRawRecipe();
            if (raw == null) return null;
            return (T) new ShapedRecipe(data.id, CraftingRecipeCategory.MISC, raw, resultStack, true);
        } else if (typeEnum == RecipeTypeEnum.SMELTING) {
            Ingredient ing = data.getIngredientAt(0);
            if (ing == null) return null;
            return (T) new SmeltingRecipe(data.id, CookingRecipeCategory.MISC, ing, resultStack, data.experience, Math.max(1, data.cookingTime));
        } else if (typeEnum == RecipeTypeEnum.BLASTING) {
            Ingredient ing = data.getIngredientAt(0);
            if (ing == null) return null;
            return (T) new BlastingRecipe(data.id, CookingRecipeCategory.MISC, ing, resultStack, data.experience, Math.max(1, data.cookingTime));
        } else if (typeEnum == RecipeTypeEnum.SMOKING) {
            Ingredient ing = data.getIngredientAt(0);
            if (ing == null) return null;
            return (T) new SmokingRecipe(data.id, CookingRecipeCategory.MISC, ing, resultStack, data.experience, Math.max(1, data.cookingTime));
        } else if (typeEnum == RecipeTypeEnum.CAMPFIRE_COOKING) {
            Ingredient ing = data.getIngredientAt(0);
            if (ing == null) return null;
            return (T) new CampfireCookingRecipe(data.id, CookingRecipeCategory.MISC, ing, resultStack, data.experience, Math.max(1, data.cookingTime));
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
