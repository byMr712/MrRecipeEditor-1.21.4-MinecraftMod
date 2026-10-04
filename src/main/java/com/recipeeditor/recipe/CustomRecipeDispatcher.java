package com.recipeeditor.recipe;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.RecipeBookAddS2CPacket;
import net.minecraft.network.packet.s2c.play.RecipeBookRemoveS2CPacket;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CookingRecipeCategory;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.display.CuttingRecipeDisplay;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CustomRecipeDispatcher {
    private static volatile Set<String> cachedOverriddenIds = null;
    private static volatile int lastConfigVersion = -1;

    private static final Map<NetworkRecipeId, ServerRecipeManager.ServerRecipe> CUSTOM_SERVER_RECIPES = new ConcurrentHashMap<>();
    private static final Map<RegistryKey<Recipe<?>>, List<ServerRecipeManager.ServerRecipe>> CUSTOM_SERVER_RECIPES_BY_KEY = new ConcurrentHashMap<>();
    private static final List<RecipeBookAddS2CPacket.Entry> CUSTOM_DISPLAY_PACKET_ENTRIES = new CopyOnWriteArrayList<>();
    private static final Set<NetworkRecipeId> PREVIOUS_NETWORK_IDS = ConcurrentHashMap.newKeySet();
    private static volatile int lastRecipeBookVersion = -1;

    public static synchronized void invalidateRecipeBookCache() {
        lastRecipeBookVersion = -1;
    }

    public static synchronized void invalidateOverriddenCache() {
        cachedOverriddenIds = null;
        lastConfigVersion = -1;
        invalidateRecipeBookCache();
    }

    private static synchronized void updateOverriddenCaches(RecipeEditorConfig config) {
        Set<String> ids = new HashSet<>();
        if (config != null && config.recipes != null) {
            for (CustomRecipeData recipeData : config.recipes.values()) {
                if (!recipeData.enabled || !recipeData.overrideExisting) continue;
                addIdVariants(ids, recipeData.overriddenId);
                addIdVariants(ids, recipeData.overriddenKey);
                addIdVariants(ids, recipeData.id);
                addIdVariants(ids, recipeData.resultItemId);
            }
        }
        cachedOverriddenIds = ids;
    }

    private static void addIdVariants(Set<String> ids, String id) {
        if (id == null || id.isEmpty()) return;
        ids.add(id);
        if (id.startsWith("minecraft:")) {
            ids.add(id.substring("minecraft:".length()));
        } else {
            ids.add("minecraft:" + id);
        }
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

        return cachedOverriddenIds.contains("minecraft:" + fullOrShortId);
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
        invalidateRecipeBookCache();
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Optional<RecipeEntry<?>> getCustomRecipeEntryByKey(RegistryKey<Recipe<?>> key) {
        if (key == null || key.getValue() == null) return Optional.empty();
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            return Optional.empty();
        }
        Identifier targetId = key.getValue();
        for (CustomRecipeData recipeData : config.recipes.values()) {
            if (!recipeData.enabled) continue;
            Identifier id = getRecipeIdentifier(recipeData);
            if (id.equals(targetId)) {
                RecipeType<?> mcType = getMcTypeForEnum(recipeData.type);
                if (mcType != null) {
                    RecipeEntry<?> entry = getCachedSyntheticEntry(recipeData, recipeData.type, (RecipeType) mcType);
                    if (entry != null) return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
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

    public static CraftingRecipeCategory getCraftingCategory(Item item) {
        if (item == null || item == Items.AIR) return CraftingRecipeCategory.MISC;
        if (item instanceof net.minecraft.item.SwordItem ||
            item instanceof net.minecraft.item.MiningToolItem ||
            item instanceof net.minecraft.item.ArmorItem ||
            item instanceof net.minecraft.item.ShieldItem ||
            item instanceof net.minecraft.item.BowItem ||
            item instanceof net.minecraft.item.CrossbowItem ||
            item instanceof net.minecraft.item.TridentItem ||
            item instanceof net.minecraft.item.MaceItem ||
            item instanceof net.minecraft.item.FishingRodItem ||
            item instanceof net.minecraft.item.ShearsItem ||
            item instanceof net.minecraft.item.FlintAndSteelItem) {
            return CraftingRecipeCategory.EQUIPMENT;
        }
        if (item instanceof net.minecraft.item.BlockItem blockItem) {
            net.minecraft.block.Block block = blockItem.getBlock();
            if (block instanceof net.minecraft.block.AbstractRedstoneGateBlock ||
                block instanceof net.minecraft.block.RedstoneWireBlock ||
                block instanceof net.minecraft.block.PistonBlock ||
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
        String group = data.id != null ? data.id : "";

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
            return (T) new SmithingTransformRecipe(template, base, addition, new net.minecraft.item.ItemStack(resultItem, safeCount));
        }

        return null;
    }

    public static synchronized void ensureRecipeBookEntriesUpToDate() {
        RecipeEditorConfig config = RecipeEditorConfig.getInstance();
        if (config == null || !config.modEnabled || config.recipes == null || config.recipes.isEmpty()) {
            CUSTOM_SERVER_RECIPES.clear();
            CUSTOM_SERVER_RECIPES_BY_KEY.clear();
            CUSTOM_DISPLAY_PACKET_ENTRIES.clear();
            lastRecipeBookVersion = config != null ? config.configVersion : 0;
            return;
        }

        if (lastRecipeBookVersion == config.configVersion && !CUSTOM_SERVER_RECIPES.isEmpty()) {
            return;
        }

        CUSTOM_SERVER_RECIPES.clear();
        CUSTOM_SERVER_RECIPES_BY_KEY.clear();
        CUSTOM_DISPLAY_PACKET_ENTRIES.clear();

        List<RecipeEntry<?>> customEntries = getAllCustomRecipes();
        int idCounter = 1_000_000;

        for (RecipeEntry<?> entry : customEntries) {
            Recipe<?> recipe = entry.value();
            if (recipe == null) continue;

            OptionalInt group = recipe.getGroup().isEmpty() ? OptionalInt.empty() : OptionalInt.of(Math.abs(recipe.getGroup().hashCode() % 10000));
            Optional<List<Ingredient>> ingredients;
            try {
                if (recipe.isIgnoredInRecipeBook()) {
                    ingredients = Optional.empty();
                } else {
                    IngredientPlacement placement = recipe.getIngredientPlacement();
                    ingredients = placement != null ? Optional.of(placement.getIngredients()) : Optional.empty();
                }
            } catch (Throwable t) {
                ingredients = Optional.empty();
            }
            RecipeBookCategory category = recipe.getRecipeBookCategory();

            List<ServerRecipeManager.ServerRecipe> list = new ArrayList<>();
            List<RecipeDisplay> displays = null;
            try {
                displays = recipe.getDisplays();
            } catch (Throwable t) {
                displays = Collections.emptyList();
            }

            if (displays != null) {
                for (RecipeDisplay display : displays) {
                    NetworkRecipeId netId = new NetworkRecipeId(idCounter++);
                    RecipeDisplayEntry displayEntry = new RecipeDisplayEntry(netId, display, group, category, ingredients);
                    ServerRecipeManager.ServerRecipe serverRecipe = new ServerRecipeManager.ServerRecipe(displayEntry, entry);

                    CUSTOM_SERVER_RECIPES.put(netId, serverRecipe);
                    list.add(serverRecipe);
                    CUSTOM_DISPLAY_PACKET_ENTRIES.add(new RecipeBookAddS2CPacket.Entry(displayEntry, false, false));
                }
            }
            CUSTOM_SERVER_RECIPES_BY_KEY.put(entry.id(), list);
        }
        lastRecipeBookVersion = config.configVersion;
    }

    public static ServerRecipeManager.ServerRecipe getCustomServerRecipe(NetworkRecipeId id) {
        ensureRecipeBookEntriesUpToDate();
        return CUSTOM_SERVER_RECIPES.get(id);
    }

    public static List<ServerRecipeManager.ServerRecipe> getCustomServerRecipesByKey(RegistryKey<Recipe<?>> key) {
        ensureRecipeBookEntriesUpToDate();
        return CUSTOM_SERVER_RECIPES_BY_KEY.get(key);
    }

    public static List<RecipeBookAddS2CPacket.Entry> getCustomDisplayPacketEntries() {
        ensureRecipeBookEntriesUpToDate();
        return Collections.unmodifiableList(CUSTOM_DISPLAY_PACKET_ENTRIES);
    }

    public static void sendCustomRecipeBookEntries(ServerPlayerEntity player) {
        if (player == null || player.networkHandler == null) return;
        ensureRecipeBookEntriesUpToDate();
        if (!CUSTOM_DISPLAY_PACKET_ENTRIES.isEmpty()) {
            player.networkHandler.sendPacket(new RecipeBookAddS2CPacket(new ArrayList<>(CUSTOM_DISPLAY_PACKET_ENTRIES), false));
        }
    }

    public static synchronized void syncRecipeBookToPlayers(Iterable<ServerPlayerEntity> players) {
        List<NetworkRecipeId> oldIds = new ArrayList<>(PREVIOUS_NETWORK_IDS);

        invalidateRecipeBookCache();
        ensureRecipeBookEntriesUpToDate();

        Set<NetworkRecipeId> newIds = new HashSet<>(CUSTOM_SERVER_RECIPES.keySet());
        PREVIOUS_NETWORK_IDS.clear();
        PREVIOUS_NETWORK_IDS.addAll(newIds);

        oldIds.removeAll(newIds);

        RecipeBookRemoveS2CPacket removePacket = !oldIds.isEmpty() ? new RecipeBookRemoveS2CPacket(oldIds) : null;
        RecipeBookAddS2CPacket addPacket = !CUSTOM_DISPLAY_PACKET_ENTRIES.isEmpty()
                ? new RecipeBookAddS2CPacket(new ArrayList<>(CUSTOM_DISPLAY_PACKET_ENTRIES), false)
                : null;

        for (ServerPlayerEntity player : players) {
            if (player == null || player.networkHandler == null) continue;
            if (removePacket != null) {
                player.networkHandler.sendPacket(removePacket);
            }
            if (addPacket != null) {
                player.networkHandler.sendPacket(addPacket);
            }
        }
    }
}
