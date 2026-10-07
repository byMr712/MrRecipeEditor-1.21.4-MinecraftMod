package com.recipeeditor.recipe;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CookingRecipeCategory;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CustomRecipeDispatcher {
    private static volatile Set<String> cachedOverriddenIds = null;
    private static volatile Set<Identifier> cachedOverriddenIdentifiers = null;
    private static volatile int lastConfigVersion = -1;

    public static synchronized void invalidateRecipeBookCache() {
    }

    public static synchronized void clearOverriddenCaches() {
        cachedOverriddenIds = null;
        cachedOverriddenIdentifiers = null;
        lastConfigVersion = -1;
    }

    public static synchronized void invalidateOverriddenCache() {
        clearOverriddenCaches();
    }

    private static synchronized void updateOverriddenCaches(RecipeEditorConfig config) {
        Set<String> ids = new HashSet<>();
        Set<Identifier> identifiers = new HashSet<>();
        if (config != null && config.recipes != null) {
            for (CustomRecipeData recipeData : config.recipes.values()) {
                if (!recipeData.enabled || !recipeData.overrideExisting) continue;
                addIdVariants(ids, identifiers, recipeData.overriddenId);
                addIdVariants(ids, identifiers, recipeData.overriddenKey);
                addIdVariants(ids, identifiers, recipeData.id);
                addIdVariants(ids, identifiers, recipeData.resultItemId);
            }
        }
        cachedOverriddenIds = ids;
        cachedOverriddenIdentifiers = identifiers;
    }

    private static void addIdVariants(Set<String> ids, Set<Identifier> identifiers, String id) {
        if (id == null || id.isEmpty()) return;
        ids.add(id);
        Identifier parsed = Identifier.tryParse(id);
        if (parsed != null) {
            identifiers.add(parsed);
        }
        if (id.startsWith("minecraft:")) {
            String path = id.substring("minecraft:".length());
            ids.add(path);
            Identifier parsedPath = Identifier.tryParse("minecraft:" + path);
            if (parsedPath != null) identifiers.add(parsedPath);
        } else if (!id.contains(":")) {
            ids.add("minecraft:" + id);
            Identifier parsedMc = Identifier.tryParse("minecraft:" + id);
            if (parsedMc != null) identifiers.add(parsedMc);
        }
    }

    public static boolean isRecipeOverridden(RecipeEntry<?> entry) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return false;
        if (entry == null || entry.id() == null) return false;
        Identifier entryId = entry.id();
        if ("recipeeditor".equals(entryId.getNamespace())) return false;

        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return false;
        }

        int currentVer = config.configVersion;
        if (cachedOverriddenIds == null || lastConfigVersion != currentVer) {
            updateOverriddenCaches(config);
            lastConfigVersion = currentVer;
        }

        if (cachedOverriddenIdentifiers == null || cachedOverriddenIdentifiers.isEmpty()) {
            return false;
        }

        if (cachedOverriddenIdentifiers.contains(entryId)) {
            return true;
        }

        if ("minecraft".equals(entryId.getNamespace())) {
            return cachedOverriddenIds.contains(entryId.getPath());
        }

        return false;
    }

    public static boolean isIdentifierOverridden(Identifier entryId) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return false;
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

        if (cachedOverriddenIdentifiers == null || cachedOverriddenIdentifiers.isEmpty()) {
            return false;
        }

        if (cachedOverriddenIdentifiers.contains(entryId)) {
            return true;
        }

        if ("minecraft".equals(entryId.getNamespace())) {
            return cachedOverriddenIds.contains(entryId.getPath());
        }

        return false;
    }

    public static boolean isIdOverridden(String id) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return false;
        if (id == null || id.isEmpty()) return false;

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

        return cachedOverriddenIds.contains(id);
    }

    public static Identifier getRecipeIdentifier(CustomRecipeData data) {
        String baseId = data.id != null && !data.id.isEmpty() ? data.id : (data.resultItemId != null ? data.resultItemId : "custom");
        if (baseId.contains(":")) {
            baseId = baseId.substring(baseId.indexOf(':') + 1);
        }
        String cleanId = baseId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        if (cleanId.startsWith("recipeeditor_")) {
            cleanId = cleanId.substring("recipeeditor_".length());
        }
        String typeSuffix = data.type != null ? data.type.name().toLowerCase(java.util.Locale.ROOT) : "craft";
        int hash = Math.abs(data.getKey().hashCode());
        return Identifier.of("recipeeditor", cleanId + "_" + typeSuffix + "_" + Integer.toHexString(hash));
    }

    @SuppressWarnings("unchecked")
    public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeEntry<T>> getCustomMatch(RecipeType<T> type, I input, World world) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return Optional.empty();
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty() || input == null || input.isEmpty()) {
            return Optional.empty();
        }

        RecipeTypeEnum targetEnum = getEnumForType(type);
        if (targetEnum == null) return Optional.empty();

        if (targetEnum == RecipeTypeEnum.SHAPED_CRAFTING && input instanceof CraftingRecipeInput craftingInput) {
            List<CustomRecipeData> sorted = config.getSortedCraftingRecipes();
            for (CustomRecipeData recipeData : sorted) {
                if (!recipeData.enabled || recipeData.type != targetEnum) continue;
                if (matchesInput(recipeData, targetEnum, input)) {
                    RecipeEntry<T> entry = getCachedSyntheticEntry(recipeData, targetEnum, type);
                    if (entry != null) {
                        return Optional.of(entry);
                    }
                }
            }
            return Optional.empty();
        }

        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled || recipeData.type != targetEnum) continue;

            if (matchesInput(recipeData, targetEnum, input)) {
                RecipeEntry<T> entry = getCachedSyntheticEntry(recipeData, targetEnum, type);
                if (entry != null) {
                    return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public static <I extends RecipeInput, T extends Recipe<I>> List<RecipeEntry<T>> getCustomMatches(RecipeType<T> type, I input, World world) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return Collections.emptyList();
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty() || input == null || input.isEmpty()) {
            return Collections.emptyList();
        }

        RecipeTypeEnum targetEnum = getEnumForType(type);
        if (targetEnum == null) return Collections.emptyList();

        List<RecipeEntry<T>> matches = new ArrayList<>();
        if (targetEnum == RecipeTypeEnum.SHAPED_CRAFTING && input instanceof CraftingRecipeInput craftingInput) {
            List<CustomRecipeData> sorted = config.getSortedCraftingRecipes();
            for (CustomRecipeData recipeData : sorted) {
                if (!recipeData.enabled || recipeData.type != targetEnum) continue;
                if (matchesInput(recipeData, targetEnum, input)) {
                    RecipeEntry<T> entry = getCachedSyntheticEntry(recipeData, targetEnum, type);
                    if (entry != null) {
                        matches.add(entry);
                    }
                }
            }
            return matches;
        }

        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled || recipeData.type != targetEnum) continue;

            if (matchesInput(recipeData, targetEnum, input)) {
                RecipeEntry<T> entry = getCachedSyntheticEntry(recipeData, targetEnum, type);
                if (entry != null) {
                    matches.add(entry);
                }
            }
        }
        return matches;
    }

    @SuppressWarnings("unchecked")
    public static <I extends RecipeInput, T extends Recipe<I>> List<RecipeEntry<T>> getAllCustomRecipesOfType(RecipeType<T> type) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return Collections.emptyList();
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

    private static final Map<String, RecipeEntry<?>> SYNTHETIC_CACHE = new ConcurrentHashMap<>();
    private static volatile List<RecipeEntry<?>> CACHED_ALL_CUSTOM_RECIPES = null;
    private static volatile int lastCustomRecipesVersion = -1;

    private static volatile int cachedSmithingVersion = -1;
    private static final List<Ingredient> CACHED_SMITHING_TEMPLATES = new CopyOnWriteArrayList<>();
    private static final List<Ingredient> CACHED_SMITHING_BASES = new CopyOnWriteArrayList<>();
    private static final List<Ingredient> CACHED_SMITHING_ADDITIONS = new CopyOnWriteArrayList<>();

    public static void clearSyntheticCache() {
        SYNTHETIC_CACHE.clear();
        CACHED_ALL_CUSTOM_RECIPES = null;
        lastCustomRecipesVersion = -1;
        cachedSmithingVersion = -1;
        CACHED_SMITHING_TEMPLATES.clear();
        CACHED_SMITHING_BASES.clear();
        CACHED_SMITHING_ADDITIONS.clear();
    }

    private static void updateSmithingSlotCaches(RecipeEditorConfig config) {
        List<Ingredient> templates = new ArrayList<>();
        List<Ingredient> bases = new ArrayList<>();
        List<Ingredient> additions = new ArrayList<>();
        if (config != null && config.modEnabled && config.recipes != null) {
            for (CustomRecipeData recipe : config.recipes.values()) {
                if (!recipe.enabled || recipe.type != RecipeTypeEnum.SMITHING) continue;
                Ingredient t = recipe.getIngredientAt(0);
                if (t != null && !t.isEmpty()) templates.add(t);
                Ingredient b = recipe.getIngredientAt(1);
                if (b != null && !b.isEmpty()) bases.add(b);
                Ingredient a = recipe.getIngredientAt(2);
                if (a != null && !a.isEmpty()) additions.add(a);
            }
        }
        CACHED_SMITHING_TEMPLATES.clear();
        CACHED_SMITHING_TEMPLATES.addAll(templates);
        CACHED_SMITHING_BASES.clear();
        CACHED_SMITHING_BASES.addAll(bases);
        CACHED_SMITHING_ADDITIONS.clear();
        CACHED_SMITHING_ADDITIONS.addAll(additions);
        cachedSmithingVersion = config != null ? config.configVersion : 0;
    }

    public static boolean isCustomSmithingSlotMatch(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) return false;

        if (cachedSmithingVersion != config.configVersion) {
            updateSmithingSlotCaches(config);
        }

        List<Ingredient> list = switch (slot) {
            case 0 -> CACHED_SMITHING_TEMPLATES;
            case 1 -> CACHED_SMITHING_BASES;
            case 2 -> CACHED_SMITHING_ADDITIONS;
            default -> Collections.emptyList();
        };

        for (Ingredient ing : list) {
            if (ing.test(stack)) return true;
        }
        return false;
    }

    public static boolean isCustomSmithingTemplate(ItemStack stack) {
        return isCustomSmithingSlotMatch(0, stack);
    }

    public static boolean isCustomSmithingBase(ItemStack stack) {
        return isCustomSmithingSlotMatch(1, stack);
    }

    public static boolean isCustomSmithingAddition(ItemStack stack) {
        return isCustomSmithingSlotMatch(2, stack);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Recipe<?>> RecipeEntry<T> getCachedSyntheticEntry(CustomRecipeData recipeData, RecipeTypeEnum targetEnum, RecipeType<?> type) {
        String cacheKey = recipeData.getKey() + "#" + recipeData.hashCode() + "#" + targetEnum.name();
        return (RecipeEntry<T>) SYNTHETIC_CACHE.computeIfAbsent(cacheKey, k -> {
            Recipe<?> dynamicRecipe = createSyntheticRecipe(recipeData, targetEnum, (RecipeType) type);
            if (dynamicRecipe != null) {
                Identifier id = getRecipeIdentifier(recipeData);
                return new RecipeEntry(id, dynamicRecipe);
            }
            return null;
        });
    }

    public static Collection<RecipeEntry<?>> getAllCustomRecipes() {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return Collections.emptyList();
        }

        int currentVer = config.configVersion;
        if (CACHED_ALL_CUSTOM_RECIPES != null && lastCustomRecipesVersion == currentVer) {
            return CACHED_ALL_CUSTOM_RECIPES;
        }

        List<RecipeEntry<?>> list = new ArrayList<>();
        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled) continue;
            RecipeType<?> mcType = recipeData.type.toRecipeType();
            if (mcType != null) {
                RecipeEntry<?> entry = getCachedSyntheticEntry(recipeData, recipeData.type, mcType);
                if (entry != null) {
                    list.add(entry);
                }
            }
        }
        CACHED_ALL_CUSTOM_RECIPES = Collections.unmodifiableList(list);
        lastCustomRecipesVersion = currentVer;
        return CACHED_ALL_CUSTOM_RECIPES;
    }

    public static Optional<RecipeEntry<?>> getCustomRecipeForOverridden(Identifier id) {
        if (id == null) return Optional.empty();
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return Optional.empty();
        }
        String fullId = id.toString();
        String path = id.getPath();
        for (CustomRecipeData r : config.recipes.values()) {
            if (!r.enabled || !r.overrideExisting) continue;
            if (fullId.equals(r.overriddenId) || path.equals(r.overriddenId) ||
                fullId.equals(r.id) || path.equals(r.id) ||
                fullId.equals(r.resultItemId) || path.equals(r.resultItemId)) {
                RecipeType<?> mcType = r.type != null ? r.type.toRecipeType() : null;
                if (mcType != null) {
                    RecipeEntry<?> entry = getCachedSyntheticEntry(r, r.type, mcType);
                    if (entry != null) return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<RecipeEntry<?>> getCustomRecipeEntryById(Identifier id) {
        if (id == null) return Optional.empty();
        Collection<RecipeEntry<?>> all = getAllCustomRecipes();
        for (RecipeEntry<?> entry : all) {
            if (id.equals(entry.id())) {
                return Optional.of(entry);
            }
        }
        String path = id.getPath();
        for (RecipeEntry<?> entry : all) {
            if (entry.id().getPath().equals(path)) {
                return Optional.of(entry);
            }
        }
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config != null && config.recipes != null && !config.recipes.isEmpty()) {
            String fullId = id.toString();
            String strippedPath = path.startsWith("minecraft_") ? path.substring("minecraft_".length()) : path;
            if (strippedPath.startsWith("recipeeditor_")) {
                strippedPath = strippedPath.substring("recipeeditor_".length());
            }

            for (CustomRecipeData data : config.recipes.values()) {
                if (!data.enabled) continue;

                boolean match = false;
                if (fullId.equals(data.id) || path.equals(data.id) || strippedPath.equals(data.id)) {
                    match = true;
                } else if (data.overriddenId != null && (fullId.equals(data.overriddenId) || path.equals(data.overriddenId) || strippedPath.equals(data.overriddenId))) {
                    match = true;
                } else if (data.overriddenKey != null && (fullId.equals(data.overriddenKey) || path.equals(data.overriddenKey))) {
                    match = true;
                } else if (data.resultItemId != null && (fullId.equals(data.resultItemId) || path.equals(data.resultItemId) || strippedPath.equals(data.resultItemId))) {
                    match = true;
                } else if (data.id != null && (path.contains(data.id) || data.id.contains(path))) {
                    match = true;
                } else if (data.overriddenId != null && (path.contains(data.overriddenId) || data.overriddenId.contains(path))) {
                    match = true;
                }

                if (match) {
                    RecipeType<?> mcType = data.type != null ? data.type.toRecipeType() : null;
                    if (mcType != null) {
                        RecipeEntry<?> entry = getCachedSyntheticEntry(data, data.type, mcType);
                        if (entry != null) return Optional.of(entry);
                    }
                }
            }
        }

        for (RecipeEntry<?> entry : all) {
            String entryPath = entry.id().getPath();
            if (entryPath.startsWith(path + "_") || path.startsWith(entryPath + "_") ||
                entryPath.contains(path) || path.contains(entryPath)) {
                return Optional.of(entry);
            }
        }
        return getCustomRecipeForOverridden(id);
    }

    public static RecipeTypeEnum getEnumForType(RecipeType<?> type) {
        return RecipeTypeEnum.fromRecipeType(type);
    }

    private static boolean matchesInput(CustomRecipeData recipeData, RecipeTypeEnum targetEnum, RecipeInput input) {
        if (targetEnum == RecipeTypeEnum.SHAPED_CRAFTING) {
            if (input instanceof CraftingRecipeInput craftingInput) {
                return matchesCrafting(recipeData, craftingInput);
            }
            return false;
        }

        if (targetEnum == RecipeTypeEnum.SMITHING) {
            if (input instanceof SmithingRecipeInput smithingInput) {
                return matchesSmithing(recipeData, smithingInput);
            }
            return false;
        }

        if (input instanceof SingleStackRecipeInput singleInput) {
            ItemStack stack = singleInput.item();
            if (stack.isEmpty()) return false;
            Ingredient expected = recipeData.getIngredientAt(0);
            return expected != null && expected.test(stack);
        }

        return false;
    }

    private static boolean matchesCrafting(CustomRecipeData recipeData, CraftingRecipeInput input) {
        if (recipeData.isShapeless) {
            return matchesShapeless(recipeData, input);
        } else {
            return matchesShaped(recipeData, input);
        }
    }

    private static boolean matchesShaped(CustomRecipeData recipeData, CraftingRecipeInput input) {
        int patternW = recipeData.getPatternWidth();
        int patternH = recipeData.getPatternHeight();
        int inputW = input.getWidth();
        int inputH = input.getHeight();

        if (inputW < patternW || inputH < patternH) return false;

        int minRow = recipeData.getMinRow();
        int minCol = recipeData.getMinCol();

        for (int dy = 0; dy <= inputH - patternH; dy++) {
            for (int dx = 0; dx <= inputW - patternW; dx++) {
                if (checkMatchAt(recipeData, input, dx, dy, minRow, minCol, patternW, patternH, false)) {
                    return true;
                }
                if (checkMatchAt(recipeData, input, dx, dy, minRow, minCol, patternW, patternH, true)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean checkMatchAt(
            CustomRecipeData recipeData,
            CraftingRecipeInput input,
            int dx, int dy,
            int minRow, int minCol,
            int patternW, int patternH,
            boolean mirrored
    ) {
        int inputW = input.getWidth();
        int inputH = input.getHeight();

        for (int y = 0; y < inputH; y++) {
            for (int x = 0; x < inputW; x++) {
                ItemStack actual = input.getStackInSlot(x + y * inputW);

                boolean inPatternX = (x >= dx && x < dx + patternW);
                boolean inPatternY = (y >= dy && y < dy + patternH);

                if (inPatternX && inPatternY) {
                    int pCol = x - dx;
                    int pRow = y - dy;

                    int sourceCol = mirrored ? (patternW - 1 - pCol) : pCol;
                    int actualSlot = (minRow + pRow) * 3 + (minCol + sourceCol);

                    Ingredient expected = recipeData.getIngredientAt(actualSlot);

                    if (expected == null || expected.isEmpty()) {
                        if (!actual.isEmpty()) return false;
                    } else {
                        if (!expected.test(actual)) return false;
                    }
                } else {
                    if (!actual.isEmpty()) return false;
                }
            }
        }
        return true;
    }

    private static boolean matchesShapeless(CustomRecipeData recipeData, CraftingRecipeInput input) {
        List<Ingredient> ingredients = recipeData.getNonEmptyIngredients();
        if (input.getStackCount() != ingredients.size()) return false;

        List<ItemStack> inputStacks = new ArrayList<>();
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack st = input.getStackInSlot(i);
            if (!st.isEmpty()) inputStacks.add(st);
        }

        if (inputStacks.size() != ingredients.size()) return false;

        boolean[] usedInputs = new boolean[inputStacks.size()];
        return matchShapelessBacktrack(ingredients, 0, inputStacks, usedInputs);
    }

    private static boolean matchShapelessBacktrack(List<Ingredient> ingredients, int ingIndex, List<ItemStack> inputs, boolean[] usedInputs) {
        if (ingIndex >= ingredients.size()) return true;

        Ingredient ing = ingredients.get(ingIndex);
        for (int i = 0; i < inputs.size(); i++) {
            if (!usedInputs[i] && ing.test(inputs.get(i))) {
                usedInputs[i] = true;
                if (matchShapelessBacktrack(ingredients, ingIndex + 1, inputs, usedInputs)) {
                    return true;
                }
                usedInputs[i] = false;
            }
        }
        return false;
    }

    private static boolean matchesSmithing(CustomRecipeData recipeData, SmithingRecipeInput input) {
        Ingredient template = recipeData.getIngredientAt(0);
        Ingredient base = recipeData.getIngredientAt(1);
        Ingredient addition = recipeData.getIngredientAt(2);

        if (template != null && !template.isEmpty() && !template.test(input.template())) return false;
        if (base != null && !base.isEmpty() && !base.test(input.base())) return false;
        if (addition != null && !addition.isEmpty() && !addition.test(input.addition())) return false;

        return true;
    }

    public static CraftingRecipeCategory getCraftingCategory(Item item) {
        if (item == null || item == Items.AIR) return CraftingRecipeCategory.MISC;
        if (item.getComponents() != null) {
            if (item.getComponents().contains(net.minecraft.component.DataComponentTypes.TOOL) ||
                item.getComponents().contains(net.minecraft.component.DataComponentTypes.MAX_DAMAGE)) {
                return CraftingRecipeCategory.EQUIPMENT;
            }
        }
        if (item instanceof net.minecraft.item.BlockItem bi) {
            var block = bi.getBlock();
            if (block instanceof net.minecraft.block.RedstoneWireBlock ||
                block instanceof net.minecraft.block.RepeaterBlock ||
                block instanceof net.minecraft.block.ComparatorBlock ||
                block instanceof net.minecraft.block.RedstoneTorchBlock ||
                block instanceof net.minecraft.block.HopperBlock ||
                block instanceof net.minecraft.block.DropperBlock ||
                block instanceof net.minecraft.block.DispenserBlock ||
                block instanceof net.minecraft.block.LeverBlock ||
                block instanceof net.minecraft.block.ButtonBlock ||
                block instanceof net.minecraft.block.PressurePlateBlock ||
                block instanceof net.minecraft.block.ObserverBlock ||
                block instanceof net.minecraft.block.DaylightDetectorBlock ||
                block instanceof net.minecraft.block.TripwireHookBlock ||
                block instanceof net.minecraft.block.TargetBlock ||
                block instanceof net.minecraft.block.CrafterBlock) {
                return CraftingRecipeCategory.REDSTONE;
            }
            return CraftingRecipeCategory.BUILDING;
        }
        return CraftingRecipeCategory.MISC;
    }

    public static CookingRecipeCategory getCookingCategory(Item item) {
        if (item == null || item == Items.AIR) return CookingRecipeCategory.MISC;
        if (item.getComponents() != null && item.getComponents().contains(net.minecraft.component.DataComponentTypes.FOOD)) {
            return CookingRecipeCategory.FOOD;
        }
        if (item instanceof net.minecraft.item.BlockItem) {
            return CookingRecipeCategory.BLOCKS;
        }
        return CookingRecipeCategory.MISC;
    }

    @SuppressWarnings("unchecked")
    private static <I extends RecipeInput, T extends Recipe<I>> T createSyntheticRecipe(CustomRecipeData data, RecipeTypeEnum typeEnum, RecipeType<?> recipeType) {
        Item resultItem = data.getResultItem();
        if (resultItem == Items.AIR) return null;

        int safeCount = Math.min(resultItem.getMaxCount(), Math.max(1, data.getResultCountForType(typeEnum)));
        ItemStack resultStack = new ItemStack(resultItem, safeCount);
        String group = "";

        if (typeEnum == RecipeTypeEnum.SHAPED_CRAFTING) {
            CraftingRecipeCategory category = getCraftingCategory(resultItem);
            if (data.isShapeless) {
                net.minecraft.util.collection.DefaultedList<Ingredient> ingredients = net.minecraft.util.collection.DefaultedList.of();
                ingredients.addAll(data.getNonEmptyIngredients());
                return (T) new ShapelessRecipe(group, category, resultStack, ingredients);
            }
            RawShapedRecipe raw = data.getRawRecipe();
            if (raw == null) return null;
            return (T) new ShapedRecipe(group, category, raw, resultStack, true);
        } else if (typeEnum == RecipeTypeEnum.SMELTING) {
            Ingredient ing = data.getIngredientAt(0);
            if (ing == null) return null;
            return (T) new SmeltingRecipe(group, getCookingCategory(resultItem), ing, resultStack, data.experience, Math.max(1, data.cookingTime));
        } else if (typeEnum == RecipeTypeEnum.BLASTING) {
            Ingredient ing = data.getIngredientAt(0);
            if (ing == null) return null;
            return (T) new BlastingRecipe(group, getCookingCategory(resultItem), ing, resultStack, data.experience, Math.max(1, data.cookingTime));
        } else if (typeEnum == RecipeTypeEnum.SMOKING) {
            Ingredient ing = data.getIngredientAt(0);
            if (ing == null) return null;
            return (T) new SmokingRecipe(group, getCookingCategory(resultItem), ing, resultStack, data.experience, Math.max(1, data.cookingTime));
        } else if (typeEnum == RecipeTypeEnum.CAMPFIRE_COOKING) {
            Ingredient ing = data.getIngredientAt(0);
            if (ing == null) return null;
            return (T) new CampfireCookingRecipe(group, getCookingCategory(resultItem), ing, resultStack, data.experience, Math.max(1, data.cookingTime));
        } else if (typeEnum == RecipeTypeEnum.STONECUTTING) {
            Ingredient ing = data.getIngredientAt(0);
            if (ing == null) return null;
            return (T) new StonecuttingRecipe(group, ing, resultStack);
        } else if (typeEnum == RecipeTypeEnum.SMITHING) {
            Optional<Ingredient> template = data.createIngredientForSlot(0);
            Optional<Ingredient> base = data.createIngredientForSlot(1);
            Optional<Ingredient> addition = data.createIngredientForSlot(2);
            return (T) new CustomDynamicSmithingRecipe(group, template, base, addition, resultStack);
        }

        return null;
    }

    private static volatile Set<Identifier> PREVIOUS_CUSTOM_IDS = null;

    public static void sendCustomRecipeBookEntries(ServerPlayerEntity player) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        if (player == null || player.networkHandler == null) return;
        try {
            Collection<RecipeEntry<?>> custom = getAllCustomRecipes();
            if (!custom.isEmpty()) {
                if (player.server != null) {
                    player.networkHandler.sendPacket(new SynchronizeRecipesS2CPacket(player.server.getRecipeManager().values()));
                }
                List<Identifier> ids = new ArrayList<>();
                for (RecipeEntry<?> entry : custom) {
                    ids.add(entry.id());
                    player.getRecipeBook().add(entry);
                    player.getRecipeBook().display(entry);
                }
                player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.ChangeUnlockedRecipesS2CPacket(
                    net.minecraft.network.packet.s2c.play.ChangeUnlockedRecipesS2CPacket.Action.ADD,
                    ids,
                    ids,
                    player.getRecipeBook().getOptions()
                ));
            }
        } catch (Throwable ignored) {}
    }

    public static synchronized void syncRecipeBookToPlayers(Iterable<ServerPlayerEntity> players) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Collection<RecipeEntry<?>> custom = getAllCustomRecipes();
        List<Identifier> ids = new ArrayList<>();
        Set<Identifier> currentIds = new HashSet<>();
        for (RecipeEntry<?> entry : custom) {
            ids.add(entry.id());
            currentIds.add(entry.id());
        }

        List<Identifier> removedIds = new ArrayList<>();
        if (PREVIOUS_CUSTOM_IDS != null) {
            for (Identifier prevId : PREVIOUS_CUSTOM_IDS) {
                if (!currentIds.contains(prevId)) {
                    removedIds.add(prevId);
                }
            }
        }
        PREVIOUS_CUSTOM_IDS = currentIds;

        for (ServerPlayerEntity player : players) {
            if (player == null || player.networkHandler == null || player.server == null) continue;
            try {
                // 1. Send SynchronizeRecipesS2CPacket FIRST so the client recipeManager has the synthetic recipes
                player.networkHandler.sendPacket(new SynchronizeRecipesS2CPacket(player.server.getRecipeManager().values()));

                // 2. Remove any deleted custom recipes from the player's recipe book
                if (!removedIds.isEmpty()) {
                    player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.ChangeUnlockedRecipesS2CPacket(
                        net.minecraft.network.packet.s2c.play.ChangeUnlockedRecipesS2CPacket.Action.REMOVE,
                        removedIds,
                        Collections.emptyList(),
                        player.getRecipeBook().getOptions()
                    ));
                }

                // 3. Add and display all active custom recipes
                if (!ids.isEmpty()) {
                    for (RecipeEntry<?> entry : custom) {
                        player.getRecipeBook().add(entry);
                        player.getRecipeBook().display(entry);
                    }
                    player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.ChangeUnlockedRecipesS2CPacket(
                        net.minecraft.network.packet.s2c.play.ChangeUnlockedRecipesS2CPacket.Action.ADD,
                        ids,
                        ids,
                        player.getRecipeBook().getOptions()
                    ));
                }
            } catch (Throwable ignored) {}
        }

        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.player != null && client.player.getRecipeBook() != null) {
                client.execute(() -> {
                    try {
                        for (RecipeEntry<?> entry : custom) {
                            client.player.getRecipeBook().add(entry);
                            client.player.getRecipeBook().display(entry);
                        }
                    } catch (Throwable ignored) {}
                });
            }
        } catch (Throwable ignored) {}
    }

    public static void syncRecipesToAllPlayers(MinecraftServer server) {
        if (server != null && server.getPlayerManager() != null) {
            syncRecipeBookToPlayers(server.getPlayerManager().getPlayerList());
        }
    }
}
