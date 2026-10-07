package com.recipeeditor.recipe;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CustomRecipeDispatcher {
    private static volatile Set<String> cachedOverriddenIds = null;
    private static volatile Set<Identifier> cachedOverriddenIdentifiers = null;
    private static volatile int lastConfigVersion = -1;

    private static final Map<RecipeDisplayId, RecipeManager.ServerDisplayInfo> CUSTOM_SERVER_RECIPES = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Recipe<?>>, List<RecipeManager.ServerDisplayInfo>> CUSTOM_SERVER_RECIPES_BY_KEY = new ConcurrentHashMap<>();
    private static final List<ClientboundRecipeBookAddPacket.Entry> CUSTOM_DISPLAY_PACKET_ENTRIES = new CopyOnWriteArrayList<>();
    private static final Set<RecipeDisplayId> PREVIOUS_NETWORK_IDS = ConcurrentHashMap.newKeySet();
    private static volatile int lastRecipeBookVersion = -1;

    public static synchronized void invalidateRecipeBookCache() {
        lastRecipeBookVersion = -1;
    }

    public static synchronized void invalidateOverriddenCache() {
        cachedOverriddenIds = null;
        cachedOverriddenIdentifiers = null;
        lastConfigVersion = -1;
        invalidateRecipeBookCache();
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

    public static boolean isRecipeOverridden(RecipeHolder<?> entry) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return false;
        if (entry == null || entry.id() == null) return false;
        Identifier entryId = entry.id().identifier();
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

    public static boolean isIdOverridden(String fullOrShortId) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return false;
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

        return cachedOverriddenIds.contains("minecraft:" + fullOrShortId);
    }

    public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getCustomMatch(RecipeType<T> type, I input, Level world) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return Optional.empty();
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty() || input == null || input.isEmpty()) {
            return Optional.empty();
        }

        RecipeTypeEnum targetEnum = getEnumForType(type);
        if (targetEnum == null) return Optional.empty();

        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled || recipeData.type != targetEnum) continue;

            if (matchesInput(recipeData, targetEnum, input)) {
                RecipeHolder<T> entry = getCachedSyntheticEntry(recipeData, targetEnum, type);
                if (entry != null) {
                    return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
    }

    private static final java.util.Map<String, RecipeHolder<?>> SYNTHETIC_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile SelectableRecipe.SingleInputSet<StonecutterRecipe> CACHED_CUSTOM_STONECUTTER_GROUPING = null;
    private static volatile int lastCustomStonecutterVersion = -1;

    private static volatile int cachedSmithingVersion = -1;
    private static final List<Ingredient> CACHED_SMITHING_TEMPLATES = new CopyOnWriteArrayList<>();
    private static final List<Ingredient> CACHED_SMITHING_BASES = new CopyOnWriteArrayList<>();
    private static final List<Ingredient> CACHED_SMITHING_ADDITIONS = new CopyOnWriteArrayList<>();

    public static void clearSyntheticCache() {
        SYNTHETIC_CACHE.clear();
        CACHED_ALL_CUSTOM_RECIPES = null;
        lastCustomRecipesVersion = -1;
        CACHED_CUSTOM_STONECUTTER_GROUPING = null;
        lastCustomStonecutterVersion = -1;
        cachedSmithingVersion = -1;
        CACHED_SMITHING_TEMPLATES.clear();
        CACHED_SMITHING_BASES.clear();
        CACHED_SMITHING_ADDITIONS.clear();
        invalidateRecipeBookCache();
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

    @SuppressWarnings("unchecked")
    public static <I extends RecipeInput, T extends Recipe<I>> RecipeHolder<T> getCachedSyntheticEntry(CustomRecipeData recipeData, RecipeTypeEnum targetEnum, RecipeType<T> type) {
        String cacheKey = recipeData.getKey() + "#" + recipeData.hashCode() + "#" + targetEnum.name();
        return (RecipeHolder<T>) SYNTHETIC_CACHE.computeIfAbsent(cacheKey, k -> {
            T dynamicRecipe = createSyntheticRecipe(recipeData, targetEnum, type);
            if (dynamicRecipe != null) {
                Identifier id = getRecipeIdentifier(recipeData);
                ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
                return new RecipeHolder<>(key, dynamicRecipe);
            }
            return null;
        });
    }

    public static <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> getCustomMatches(RecipeType<T> type, I input, Level world) {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty() || input == null || input.isEmpty()) {
            return Collections.emptyList();
        }

        RecipeTypeEnum targetEnum = getEnumForType(type);
        if (targetEnum == null) return Collections.emptyList();

        List<RecipeHolder<T>> list = new ArrayList<>();
        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled || recipeData.type != targetEnum) continue;

            if (matchesInput(recipeData, targetEnum, input)) {
                RecipeHolder<T> entry = getCachedSyntheticEntry(recipeData, targetEnum, type);
                if (entry != null) {
                    list.add(entry);
                }
            }
        }
        return list;
    }

    public static <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> getAllCustomRecipesOfType(RecipeType<T> type) {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return Collections.emptyList();
        }

        RecipeTypeEnum targetEnum = getEnumForType(type);
        if (targetEnum == null) return Collections.emptyList();

        List<RecipeHolder<T>> list = new ArrayList<>();
        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled || recipeData.type != targetEnum) continue;

            RecipeHolder<T> entry = getCachedSyntheticEntry(recipeData, targetEnum, type);
            if (entry != null) {
                list.add(entry);
            }
        }
        return list;
    }

    public static SelectableRecipe.SingleInputSet<StonecutterRecipe> getCustomStonecutterGrouping() {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return SelectableRecipe.SingleInputSet.empty();
        }

        if (CACHED_CUSTOM_STONECUTTER_GROUPING != null && lastCustomStonecutterVersion == config.configVersion) {
            return CACHED_CUSTOM_STONECUTTER_GROUPING;
        }

        List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> entries = new ArrayList<>();
        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled || recipeData.type != RecipeTypeEnum.STONECUTTING) continue;

            Ingredient inputIng = recipeData.getIngredientAt(0);
            if (inputIng == null) continue;

            RecipeHolder<StonecutterRecipe> recipeEntry = getCachedSyntheticEntry(recipeData, RecipeTypeEnum.STONECUTTING, RecipeType.STONECUTTING);
            if (recipeEntry != null && recipeEntry.value() != null) {
                SlotDisplay optionDisplay = recipeEntry.value().resultDisplay();
                SelectableRecipe<StonecutterRecipe> display = new SelectableRecipe<>(optionDisplay, Optional.of(recipeEntry));
                entries.add(new SelectableRecipe.SingleInputEntry<>(inputIng, display));
            }
        }
        CACHED_CUSTOM_STONECUTTER_GROUPING = new SelectableRecipe.SingleInputSet<>(entries);
        lastCustomStonecutterVersion = config.configVersion;
        return CACHED_CUSTOM_STONECUTTER_GROUPING;
    }

    private static volatile List<RecipeHolder<?>> CACHED_ALL_CUSTOM_RECIPES = null;
    private static volatile int lastCustomRecipesVersion = -1;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<RecipeHolder<?>> getAllCustomRecipes() {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return Collections.emptyList();
        }

        if (CACHED_ALL_CUSTOM_RECIPES != null && lastCustomRecipesVersion == config.configVersion) {
            return CACHED_ALL_CUSTOM_RECIPES;
        }

        List<RecipeHolder<?>> list = new ArrayList<>();
        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled) continue;
            RecipeType<?> mcType = getMcTypeForEnum(recipeData.type);
            if (mcType == null) continue;

            RecipeHolder<?> entry = getCachedSyntheticEntry(recipeData, recipeData.type, (RecipeType) mcType);
            if (entry != null) {
                list.add(entry);
            }
        }
        CACHED_ALL_CUSTOM_RECIPES = Collections.unmodifiableList(list);
        lastCustomRecipesVersion = config.configVersion;
        return CACHED_ALL_CUSTOM_RECIPES;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Optional<RecipeHolder<?>> getCustomRecipeEntryByKey(ResourceKey<Recipe<?>> key) {
        if (key == null || key.identifier() == null) return Optional.empty();
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return Optional.empty();
        }
        Identifier targetId = key.identifier();
        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled) continue;
            Identifier id = getRecipeIdentifier(recipeData);
            if (id.equals(targetId)) {
                RecipeType<?> mcType = getMcTypeForEnum(recipeData.type);
                if (mcType != null) {
                    RecipeHolder<?> entry = getCachedSyntheticEntry(recipeData, recipeData.type, (RecipeType) mcType);
                    if (entry != null) return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
    }

    public static Identifier getRecipeIdentifier(CustomRecipeData recipeData) {
        String base = sanitizeId(recipeData.id);
        String sigHash = Integer.toHexString(recipeData.getKey().hashCode() & 0x7FFFFFFF);
        return Identifier.fromNamespaceAndPath("recipeeditor", "dynamic_" + base + "_" + sigHash);
    }

    private static String sanitizeId(String id) {
        if (id == null || id.isEmpty()) return "craft";
        return id.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
    }

    public static RecipeTypeEnum getEnumForType(RecipeType<?> type) {
        return RecipeTypeEnum.fromRecipeType(type);
    }

    public static RecipeType<?> getMcTypeForEnum(RecipeTypeEnum typeEnum) {
        if (typeEnum == null) return null;
        return typeEnum.toRecipeType();
    }

    private static boolean matchesInput(CustomRecipeData data, RecipeTypeEnum typeEnum, RecipeInput input) {
        if (input instanceof CraftingInput crafting) {
            return CustomDynamicCraftingRecipe.matchesCrafting(crafting, data);
        }

        if (input instanceof SingleRecipeInput single) {
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

    public static CraftingBookCategory getCraftingCategory(Item item) {
        if (item == null || item == Items.AIR) return CraftingBookCategory.MISC;
        ItemStack defaultStack = item.getDefaultInstance();
        if (defaultStack.has(DataComponents.EQUIPPABLE) ||
            defaultStack.has(DataComponents.TOOL) ||
            defaultStack.has(DataComponents.WEAPON) ||
            item instanceof net.minecraft.world.item.ShieldItem ||
            item instanceof net.minecraft.world.item.BowItem ||
            item instanceof net.minecraft.world.item.CrossbowItem ||
            item instanceof net.minecraft.world.item.TridentItem ||
            item instanceof net.minecraft.world.item.MaceItem ||
            item instanceof net.minecraft.world.item.FishingRodItem ||
            item instanceof net.minecraft.world.item.ShearsItem ||
            item instanceof net.minecraft.world.item.FlintAndSteelItem) {
            return CraftingBookCategory.EQUIPMENT;
        }
        if (item instanceof net.minecraft.world.item.BlockItem blockItem) {
            net.minecraft.world.level.block.Block block = blockItem.getBlock();
            if (block instanceof net.minecraft.world.level.block.DiodeBlock ||
                block instanceof net.minecraft.world.level.block.RedstoneWireBlock ||
                block instanceof net.minecraft.world.level.block.piston.PistonBaseBlock ||
                block instanceof net.minecraft.world.level.block.HopperBlock ||
                block instanceof net.minecraft.world.level.block.DropperBlock ||
                block instanceof net.minecraft.world.level.block.DispenserBlock ||
                block instanceof net.minecraft.world.level.block.LeverBlock ||
                block instanceof net.minecraft.world.level.block.ButtonBlock ||
                block instanceof net.minecraft.world.level.block.BasePressurePlateBlock ||
                block instanceof net.minecraft.world.level.block.ObserverBlock ||
                block instanceof net.minecraft.world.level.block.DaylightDetectorBlock ||
                block instanceof net.minecraft.world.level.block.TripWireHookBlock ||
                block instanceof net.minecraft.world.level.block.TargetBlock ||
                block instanceof net.minecraft.world.level.block.CrafterBlock) {
                return CraftingBookCategory.REDSTONE;
            }
            return CraftingBookCategory.BUILDING;
        }
        return CraftingBookCategory.MISC;
    }

    public static CookingBookCategory getCookingCategory(Item item) {
        if (item == null || item == Items.AIR) return CookingBookCategory.MISC;
        if (item.getDefaultInstance().has(DataComponents.FOOD)) {
            return CookingBookCategory.FOOD;
        }
        if (item instanceof net.minecraft.world.item.BlockItem) {
            return CookingBookCategory.BLOCKS;
        }
        return CookingBookCategory.MISC;
    }

    @SuppressWarnings("unchecked")
    private static <I extends RecipeInput, T extends Recipe<I>> T createSyntheticRecipe(CustomRecipeData data, RecipeTypeEnum typeEnum, RecipeType<?> recipeType) {
        Item resultItem = data.getResultItem();
        if (resultItem == Items.AIR) return null;

        try {
            int safeCount = Math.min(resultItem.getDefaultInstance().getMaxStackSize(), Math.max(1, data.getResultCountForType(typeEnum)));
            ItemStackTemplate template = new ItemStackTemplate(resultItem, safeCount);
            String group = "";
            Recipe.CommonInfo commonInfo = new Recipe.CommonInfo(true);

            if (typeEnum == RecipeTypeEnum.SHAPED_CRAFTING) {
                CraftingBookCategory category = getCraftingCategory(resultItem);
                CraftingRecipe.CraftingBookInfo bookInfo = new CraftingRecipe.CraftingBookInfo(category, group);
                if (data.isShapeless) {
                    return (T) new ShapelessRecipe(commonInfo, bookInfo, template, new ArrayList<>(data.getNonEmptyIngredients()));
                }
                ShapedRecipePattern raw = data.getRawRecipe();
                if (raw == null) return null;
                return (T) new ShapedRecipe(commonInfo, bookInfo, raw, template);
            } else if (typeEnum == RecipeTypeEnum.SMELTING) {
                Ingredient ing = data.getIngredientAt(0);
                if (ing == null || ing.isEmpty()) return null;
                return (T) new SmeltingRecipe(commonInfo, new AbstractCookingRecipe.CookingBookInfo(getCookingCategory(resultItem), group), ing, template, data.experience, Math.max(1, data.cookingTime));
            } else if (typeEnum == RecipeTypeEnum.BLASTING) {
                Ingredient ing = data.getIngredientAt(0);
                if (ing == null || ing.isEmpty()) return null;
                return (T) new BlastingRecipe(commonInfo, new AbstractCookingRecipe.CookingBookInfo(getCookingCategory(resultItem), group), ing, template, data.experience, Math.max(1, data.cookingTime));
            } else if (typeEnum == RecipeTypeEnum.SMOKING) {
                Ingredient ing = data.getIngredientAt(0);
                if (ing == null || ing.isEmpty()) return null;
                return (T) new SmokingRecipe(commonInfo, new AbstractCookingRecipe.CookingBookInfo(getCookingCategory(resultItem), group), ing, template, data.experience, Math.max(1, data.cookingTime));
            } else if (typeEnum == RecipeTypeEnum.CAMPFIRE_COOKING) {
                Ingredient ing = data.getIngredientAt(0);
                if (ing == null || ing.isEmpty()) return null;
                return (T) new CampfireCookingRecipe(commonInfo, new AbstractCookingRecipe.CookingBookInfo(getCookingCategory(resultItem), group), ing, template, data.experience, Math.max(1, data.cookingTime));
            } else if (typeEnum == RecipeTypeEnum.STONECUTTING) {
                Ingredient ing = data.getIngredientAt(0);
                if (ing == null || ing.isEmpty()) return null;
                return (T) new StonecutterRecipe(commonInfo, ing, template);
            } else if (typeEnum == RecipeTypeEnum.SMITHING) {
                Optional<Ingredient> templateIng = data.createIngredientForSlot(0);
                Optional<Ingredient> baseIng = data.createIngredientForSlot(1);
                Optional<Ingredient> additionIng = data.createIngredientForSlot(2);
                if (baseIng.isEmpty() || baseIng.get().isEmpty()) {
                    return null;
                }
                return (T) new CustomDynamicSmithingRecipe(group, templateIng, baseIng, additionIng, new ItemStack(resultItem, safeCount));
            }
        } catch (Throwable t) {
            return null;
        }

        return null;
    }

    public static synchronized void ensureRecipeBookEntriesUpToDate() {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            CUSTOM_SERVER_RECIPES.clear();
            CUSTOM_SERVER_RECIPES_BY_KEY.clear();
            CUSTOM_DISPLAY_PACKET_ENTRIES.clear();
            PREVIOUS_NETWORK_IDS.clear();
            lastRecipeBookVersion = config != null ? config.configVersion : 0;
            return;
        }

        if (lastRecipeBookVersion == config.configVersion) {
            return;
        }

        CUSTOM_SERVER_RECIPES.clear();
        CUSTOM_SERVER_RECIPES_BY_KEY.clear();
        CUSTOM_DISPLAY_PACKET_ENTRIES.clear();

        for (CustomRecipeData recipeData : config.recipes.values()) {
            try {
                if (!recipeData.enabled) continue;
                RecipeType<?> mcType = getMcTypeForEnum(recipeData.type);
                if (mcType == null) continue;

                @SuppressWarnings({"rawtypes", "unchecked"})
                RecipeHolder<?> entry = getCachedSyntheticEntry(recipeData, recipeData.type, (RecipeType) mcType);
                if (entry == null || entry.value() == null) continue;

                Recipe<?> recipe = entry.value();
                OptionalInt group = OptionalInt.empty();
                Optional<List<Ingredient>> ingredients;
                try {
                    if (recipe.isSpecial()) {
                        ingredients = Optional.empty();
                    } else {
                        PlacementInfo placement = recipe.placementInfo();
                        ingredients = placement != null ? Optional.of(placement.ingredients()) : Optional.empty();
                    }
                } catch (Throwable t) {
                    ingredients = Optional.empty();
                }
                RecipeBookCategory category = recipe.recipeBookCategory();

                List<RecipeManager.ServerDisplayInfo> list = new ArrayList<>();
                List<RecipeDisplay> displays = null;
                try {
                    displays = recipe.display();
                } catch (Throwable t) {
                    displays = Collections.emptyList();
                }

                if (displays != null) {
                    int baseNetId = getBaseNetworkId(recipeData.getKey());
                    for (int d = 0; d < displays.size(); d++) {
                        RecipeDisplayId netId = new RecipeDisplayId(baseNetId + d);
                        RecipeDisplayEntry displayEntry = new RecipeDisplayEntry(netId, displays.get(d), group, category, ingredients);
                        RecipeManager.ServerDisplayInfo serverRecipe = new RecipeManager.ServerDisplayInfo(displayEntry, entry);

                        CUSTOM_SERVER_RECIPES.put(netId, serverRecipe);
                        list.add(serverRecipe);
                        CUSTOM_DISPLAY_PACKET_ENTRIES.add(new ClientboundRecipeBookAddPacket.Entry(displayEntry, false, false));
                    }
                }
                CUSTOM_SERVER_RECIPES_BY_KEY.put(entry.id(), list);
            } catch (Throwable t) {
                // Ignore failure on a single malformed recipe
            }
        }
        PREVIOUS_NETWORK_IDS.clear();
        PREVIOUS_NETWORK_IDS.addAll(CUSTOM_SERVER_RECIPES.keySet());
        lastRecipeBookVersion = config.configVersion;
    }

    public static int getBaseNetworkId(String recipeKey) {
        if (recipeKey == null) return 1_000_000;
        return 1_000_000 + ((recipeKey.hashCode() & 0x7FFFFFFF) % 80_000_000) * 10;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<RecipeDisplayEntry> createDisplayEntriesForRecipe(CustomRecipeData data) {
        if (data == null || data.type == null) return Collections.emptyList();
        RecipeType mcType = getMcTypeForEnum(data.type);
        if (mcType == null) return Collections.emptyList();
        RecipeHolder entry = getCachedSyntheticEntry(data, data.type, mcType);
        if (entry == null || entry.value() == null) return Collections.emptyList();
        Recipe<?> recipe = (Recipe<?>) entry.value();

        OptionalInt group = recipe.group().isEmpty() ? OptionalInt.empty() : OptionalInt.of((recipe.group().hashCode() & 0x7FFFFFFF) % 10000);
        Optional<List<Ingredient>> ingredients;
        try {
            if (recipe.isSpecial()) {
                ingredients = Optional.empty();
            } else {
                PlacementInfo placement = recipe.placementInfo();
                ingredients = placement != null ? Optional.of(placement.ingredients()) : Optional.empty();
            }
        } catch (Throwable t) {
            ingredients = Optional.empty();
        }
        RecipeBookCategory category = recipe.recipeBookCategory();

        List<RecipeDisplay> displays = null;
        try {
            displays = recipe.display();
        } catch (Throwable t) {
            displays = Collections.emptyList();
        }

        if (displays == null || displays.isEmpty()) return Collections.emptyList();

        int baseNetId = getBaseNetworkId(data.getKey());
        List<RecipeDisplayEntry> result = new ArrayList<>();
        for (int d = 0; d < displays.size(); d++) {
            RecipeDisplayId netId = new RecipeDisplayId(baseNetId + d);
            result.add(new RecipeDisplayEntry(netId, displays.get(d), group, category, ingredients));
        }
        return result;
    }

    public static RecipeManager.ServerDisplayInfo getCustomServerRecipe(RecipeDisplayId id) {
        ensureRecipeBookEntriesUpToDate();
        return CUSTOM_SERVER_RECIPES.get(id);
    }

    public static List<RecipeManager.ServerDisplayInfo> getCustomServerRecipesByKey(ResourceKey<Recipe<?>> key) {
        ensureRecipeBookEntriesUpToDate();
        return CUSTOM_SERVER_RECIPES_BY_KEY.get(key);
    }

    public static List<ClientboundRecipeBookAddPacket.Entry> getCustomDisplayPacketEntries() {
        ensureRecipeBookEntriesUpToDate();
        return Collections.unmodifiableList(CUSTOM_DISPLAY_PACKET_ENTRIES);
    }

    public static void sendCustomRecipeBookEntries(ServerPlayer player) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        if (player == null || player.connection == null) return;
        ensureRecipeBookEntriesUpToDate();
        PREVIOUS_NETWORK_IDS.clear();
        PREVIOUS_NETWORK_IDS.addAll(CUSTOM_SERVER_RECIPES.keySet());
        if (!CUSTOM_DISPLAY_PACKET_ENTRIES.isEmpty()) {
            player.connection.send(new ClientboundRecipeBookAddPacket(new ArrayList<>(CUSTOM_DISPLAY_PACKET_ENTRIES), false));
        }
    }

    public static synchronized void syncRecipeBookToPlayers(Iterable<ServerPlayer> players) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        List<RecipeDisplayId> oldIds = new ArrayList<>(PREVIOUS_NETWORK_IDS);

        invalidateRecipeBookCache();
        ensureRecipeBookEntriesUpToDate();

        Set<RecipeDisplayId> newIds = new HashSet<>(CUSTOM_SERVER_RECIPES.keySet());
        PREVIOUS_NETWORK_IDS.clear();
        PREVIOUS_NETWORK_IDS.addAll(newIds);

        oldIds.removeAll(newIds);

        ClientboundRecipeBookRemovePacket removePacket = !oldIds.isEmpty() ? new ClientboundRecipeBookRemovePacket(oldIds) : null;
        ClientboundRecipeBookAddPacket addPacket = !CUSTOM_DISPLAY_PACKET_ENTRIES.isEmpty()
                ? new ClientboundRecipeBookAddPacket(new ArrayList<>(CUSTOM_DISPLAY_PACKET_ENTRIES), false)
                : null;

        for (ServerPlayer player : players) {
            if (player == null || player.connection == null) continue;
            if (removePacket != null) {
                player.connection.send(removePacket);
            }
            if (addPacket != null) {
                player.connection.send(addPacket);
            }
        }

        net.minecraft.server.MinecraftServer server = null;
        for (ServerPlayer player : players) {
            if (player != null && player.level() != null && player.level().getServer() != null) {
                server = player.level().getServer();
                break;
            }
        }
        if (server != null && server.getRecipeManager() != null) {
            net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket updatePacket =
                    new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(
                            server.getRecipeManager().getSynchronizedItemProperties(),
                            server.getRecipeManager().getSynchronizedStonecutterRecipes()
                    );
            for (ServerPlayer player : players) {
                if (player != null && player.connection != null) {
                    player.connection.send(updatePacket);
                }
            }
        }
    }
}
