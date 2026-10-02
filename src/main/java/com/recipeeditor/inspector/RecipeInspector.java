package com.recipeeditor.inspector;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.recipeeditor.RecipeEditorMod;
import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.*;
import net.minecraft.recipe.display.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class RecipeInspector {
    private static final Gson GSON = new Gson();
    private static final Map<Item, List<RecipeEntry<?>>> RECIPE_ENTRIES = new HashMap<>();
    private static final Map<Item, List<RecipeDisplay>> RECIPE_DISPLAYS = new HashMap<>();
    private static final Set<Item> KNOWN_RECIPE_ITEMS = new HashSet<>();
    private static final Map<Item, CustomRecipeData> DECOMPILED_CACHE = new HashMap<>();
    private static boolean cacheInitialized = false;

    public static void invalidateCache() {
        RECIPE_ENTRIES.clear();
        RECIPE_DISPLAYS.clear();
        KNOWN_RECIPE_ITEMS.clear();
        DECOMPILED_CACHE.clear();
        cacheInitialized = false;
    }

    public static void initializeCache(World world) {
        RECIPE_ENTRIES.clear();
        RECIPE_DISPLAYS.clear();
        KNOWN_RECIPE_ITEMS.clear();
        DECOMPILED_CACHE.clear();
        MinecraftClient client = MinecraftClient.getInstance();

        // 1. Scan Fabric Mod JARs directly (works 100% in Main Menu and in-game)
        scanFabricModJars();

        // 2. Scan active Server Recipe Manager (if in singleplayer / integrated server)
        if (client != null) {
            MinecraftServer server = client.getServer();
            if (server != null && server.getRecipeManager() != null) {
                for (RecipeEntry<?> entry : server.getRecipeManager().values()) {
                    indexRecipeEntry(entry);
                }
                cacheInitialized = true;
                return;
            }

            // 3. Scan Client Recipe Book (if on dedicated server / client)
            if (client.player != null) {
                ClientRecipeBook recipeBook = client.player.getRecipeBook();
                if (recipeBook != null) {
                    for (RecipeResultCollection collection : recipeBook.getOrderedResults()) {
                        for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                            RecipeDisplay display = entry.display();
                            Set<Item> resultItems = getAllItemsFromSlotDisplay(display.result());
                            for (Item resultItem : resultItems) {
                                if (resultItem != Items.AIR) {
                                    RECIPE_DISPLAYS.computeIfAbsent(resultItem, k -> new ArrayList<>()).add(display);
                                    KNOWN_RECIPE_ITEMS.add(resultItem);
                                }
                            }
                        }
                    }
                }
                cacheInitialized = true;
                return;
            }
        }

        // 4. Offline / Main Menu Fallback for Vanilla data
        try (net.minecraft.resource.LifecycledResourceManager resourceManager =
                     new net.minecraft.resource.LifecycledResourceManagerImpl(
                             net.minecraft.resource.ResourceType.SERVER_DATA,
                             java.util.List.of(net.minecraft.resource.VanillaDataPackProvider.createDefaultPack()))) {
            OfflineRecipeManager offlineManager = new OfflineRecipeManager(net.minecraft.registry.DynamicRegistryManager.of(Registries.REGISTRIES));
            offlineManager.load(resourceManager);
            for (RecipeEntry<?> entry : offlineManager.values()) {
                indexRecipeEntry(entry);
            }
        } catch (Exception e) {
            RecipeEditorMod.LOGGER.error("Failed to load offline vanilla recipes", e);
        }

        cacheInitialized = true;
    }

    private static void scanFabricModJars() {
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            try {
                Optional<Path> dataDir = mod.findPath("data");
                if (dataDir.isPresent()) {
                    try (Stream<Path> stream = Files.walk(dataDir.get())) {
                        stream.filter(p -> p.toString().endsWith(".json") &&
                                        (p.toString().contains("/recipe/") || p.toString().contains("/recipes/") ||
                                         p.toString().contains("\\recipe\\") || p.toString().contains("\\recipes\\")))
                              .forEach(RecipeInspector::parseModRecipeJson);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static void parseModRecipeJson(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj == null) return;

            Item resultItem = Items.AIR;
            int count = 1;

            if (obj.has("result")) {
                JsonElement resElem = obj.get("result");
                if (resElem.isJsonPrimitive()) {
                    Identifier id = Identifier.tryParse(resElem.getAsString());
                    if (id != null && Registries.ITEM.containsId(id)) {
                        resultItem = Registries.ITEM.get(id);
                    }
                } else if (resElem.isJsonObject()) {
                    JsonObject resObj = resElem.getAsJsonObject();
                    if (resObj.has("id")) {
                        Identifier id = Identifier.tryParse(resObj.get("id").getAsString());
                        if (id != null && Registries.ITEM.containsId(id)) {
                            resultItem = Registries.ITEM.get(id);
                        }
                    } else if (resObj.has("item")) {
                        Identifier id = Identifier.tryParse(resObj.get("item").getAsString());
                        if (id != null && Registries.ITEM.containsId(id)) {
                            resultItem = Registries.ITEM.get(id);
                        }
                    }
                    if (resObj.has("count")) {
                        count = resObj.get("count").getAsInt();
                    }
                }
            }

            if (resultItem != Items.AIR) {
                KNOWN_RECIPE_ITEMS.add(resultItem);
                if (!DECOMPILED_CACHE.containsKey(resultItem)) {
                    CustomRecipeData decompiled = buildDecompiledRecipeFromJson(obj, resultItem, count);
                    if (decompiled != null) {
                        DECOMPILED_CACHE.put(resultItem, decompiled);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static CustomRecipeData buildDecompiledRecipeFromJson(JsonObject obj, Item resultItem, int count) {
        Identifier resId = Registries.ITEM.getId(resultItem);
        CustomRecipeData data = new CustomRecipeData(
                resId != null ? resId.getPath() : "recipe",
                resId != null ? resId.toString() : "minecraft:air",
                count > 0 ? count : 1,
                RecipeTypeEnum.SHAPED_CRAFTING
        );

        String typeStr = obj.has("type") ? obj.get("type").getAsString().toLowerCase(Locale.ROOT) : "";

        if (obj.has("pattern") && obj.has("key")) {
            data.type = RecipeTypeEnum.SHAPED_CRAFTING;
            JsonArray patternArr = obj.getAsJsonArray("pattern");
            JsonObject keyObj = obj.getAsJsonObject("key");
            int height = Math.min(3, patternArr.size());
            for (int r = 0; r < height; r++) {
                String line = patternArr.get(r).getAsString();
                int width = Math.min(3, line.length());
                for (int c = 0; c < width; c++) {
                    char ch = line.charAt(c);
                    if (ch != ' ' && keyObj.has(String.valueOf(ch))) {
                        String slotStr = parseIngredientElement(keyObj.get(String.valueOf(ch)));
                        data.setSlotString(r * 3 + c, slotStr);
                    }
                }
            }
            return data;
        } else if (obj.has("ingredients")) {
            data.type = RecipeTypeEnum.SHAPELESS_CRAFTING;
            JsonArray ings = obj.getAsJsonArray("ingredients");
            for (int i = 0; i < Math.min(9, ings.size()); i++) {
                data.setSlotString(i, parseIngredientElement(ings.get(i)));
            }
            return data;
        } else if (typeStr.contains("blasting")) {
            data.type = RecipeTypeEnum.BLASTING;
            if (obj.has("ingredient")) {
                data.setSlotString(0, parseIngredientElement(obj.get("ingredient")));
            }
            return data;
        } else if (typeStr.contains("smoking")) {
            data.type = RecipeTypeEnum.SMOKING;
            if (obj.has("ingredient")) {
                data.setSlotString(0, parseIngredientElement(obj.get("ingredient")));
            }
            return data;
        } else if (typeStr.contains("smelt") || obj.has("cookingtime")) {
            data.type = RecipeTypeEnum.SMELTING;
            if (obj.has("ingredient")) {
                data.setSlotString(0, parseIngredientElement(obj.get("ingredient")));
            }
            return data;
        } else if (typeStr.contains("stonecutting")) {
            data.type = RecipeTypeEnum.STONECUTTING;
            if (obj.has("ingredient")) {
                data.setSlotString(0, parseIngredientElement(obj.get("ingredient")));
            }
            return data;
        }
        return data;
    }

    private static String parseIngredientElement(JsonElement elem) {
        if (elem == null || elem.isJsonNull()) return "minecraft:air";
        if (elem.isJsonPrimitive()) {
            return elem.getAsString();
        } else if (elem.isJsonObject()) {
            JsonObject o = elem.getAsJsonObject();
            if (o.has("item")) return o.get("item").getAsString();
            if (o.has("id")) return o.get("id").getAsString();
            if (o.has("tag")) return "#" + o.get("tag").getAsString();
        } else if (elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            if (arr.size() > 0) {
                return parseIngredientElement(arr.get(0));
            }
        }
        return "minecraft:air";
    }

    private static class OfflineRecipeManager extends ServerRecipeManager {
        public OfflineRecipeManager(net.minecraft.registry.RegistryWrapper.WrapperLookup registries) {
            super(registries);
        }

        public void load(net.minecraft.resource.ResourceManager resourceManager) {
            net.minecraft.recipe.PreparedRecipes prepared = this.prepare(resourceManager, net.minecraft.util.profiler.DummyProfiler.INSTANCE);
            this.apply(prepared, resourceManager, net.minecraft.util.profiler.DummyProfiler.INSTANCE);
            this.initialize(net.minecraft.resource.featuretoggle.FeatureFlags.VANILLA_FEATURES);
        }
    }

    private static void indexRecipeEntry(RecipeEntry<?> entry) {
        Recipe<?> recipe = entry.value();
        try {
            List<RecipeDisplay> displays = recipe.getDisplays();
            for (RecipeDisplay display : displays) {
                Set<Item> resultItems = getAllItemsFromSlotDisplay(display.result());
                for (Item resultItem : resultItems) {
                    if (resultItem != Items.AIR) {
                        RECIPE_ENTRIES.computeIfAbsent(resultItem, k -> new ArrayList<>()).add(entry);
                        RECIPE_DISPLAYS.computeIfAbsent(resultItem, k -> new ArrayList<>()).add(display);
                        KNOWN_RECIPE_ITEMS.add(resultItem);
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            if (recipe instanceof ShapedRecipe shaped) {
                Item res = shaped.craft(net.minecraft.recipe.input.CraftingRecipeInput.EMPTY, net.minecraft.registry.DynamicRegistryManager.of(Registries.REGISTRIES)).getItem();
                if (res != Items.AIR) {
                    RECIPE_ENTRIES.computeIfAbsent(res, k -> new ArrayList<>()).add(entry);
                    KNOWN_RECIPE_ITEMS.add(res);
                }
            }
        } catch (Exception ignored) {}
    }

    public static Set<Item> getAllItemsFromSlotDisplay(SlotDisplay display) {
        Set<Item> items = new HashSet<>();
        collectItemsFromSlotDisplay(display, items);
        return items;
    }

    private static void collectItemsFromSlotDisplay(SlotDisplay display, Set<Item> items) {
        if (display == null) return;
        if (display instanceof SlotDisplay.ItemSlotDisplay itemDisplay) {
            items.add(itemDisplay.item().value());
        } else if (display instanceof SlotDisplay.StackSlotDisplay stackDisplay) {
            items.add(stackDisplay.stack().getItem());
        } else if (display instanceof SlotDisplay.WithRemainderSlotDisplay withRemainder) {
            collectItemsFromSlotDisplay(withRemainder.input(), items);
        } else if (display instanceof SlotDisplay.TagSlotDisplay tagDisplay) {
            for (var entry : Registries.ITEM.iterateEntries(tagDisplay.tag())) {
                items.add(entry.value());
            }
        } else if (display instanceof SlotDisplay.CompositeSlotDisplay composite) {
            for (SlotDisplay child : composite.contents()) {
                collectItemsFromSlotDisplay(child, items);
            }
        }
    }

    public static RecipeStatus getStatus(Item item, World world, RecipeEditorConfig config) {
        if (item == null || item == Items.AIR) {
            return RecipeStatus.UNCRAFTABLE;
        }
        if (config != null && config.hasCustomRecipe(item)) {
            CustomRecipeData custom = config.getRecipeFor(item);
            if (custom != null && custom.enabled) {
                return RecipeStatus.CUSTOM;
            }
        }
        if (!cacheInitialized) {
            initializeCache(world);
        }
        boolean hasEntry = RECIPE_ENTRIES.containsKey(item) || RECIPE_DISPLAYS.containsKey(item) || KNOWN_RECIPE_ITEMS.contains(item);
        if (hasEntry) {
            return RecipeStatus.VANILLA_OR_MODDED;
        }
        return RecipeStatus.UNCRAFTABLE;
    }

    public static boolean hasExistingRecipe(Item item, World world) {
        if (item == null || item == Items.AIR) return false;
        if (!cacheInitialized) {
            initializeCache(world);
        }
        return RECIPE_ENTRIES.containsKey(item) || RECIPE_DISPLAYS.containsKey(item) || KNOWN_RECIPE_ITEMS.contains(item);
    }

    public static CustomRecipeData decompileRecipe(Item targetItem, World world) {
        if (targetItem == null || targetItem == Items.AIR) return null;
        if (!cacheInitialized) {
            initializeCache(world);
        }

        Identifier targetId = Registries.ITEM.getId(targetItem);
        CustomRecipeData data = new CustomRecipeData(
                targetId != null ? targetId.getPath() : "recipe",
                targetId != null ? targetId.toString() : "minecraft:air",
                1,
                RecipeTypeEnum.SHAPED_CRAFTING
        );

        // 1. Try decompiling directly from RecipeEntry<?> (exact ingredient matching)
        List<RecipeEntry<?>> entries = RECIPE_ENTRIES.get(targetItem);
        if (entries != null && !entries.isEmpty()) {
            Recipe<?> recipe = entries.get(0).value();
            if (recipe instanceof ShapedRecipe shaped) {
                data.type = RecipeTypeEnum.SHAPED_CRAFTING;
                int width = shaped.getWidth();
                int height = shaped.getHeight();
                List<Optional<Ingredient>> ings = shaped.getIngredients();
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        int slotIdx = r * 3 + c;
                        if (r < height && c < width) {
                            int ingIdx = r * width + c;
                            if (ingIdx < ings.size()) {
                                Optional<Ingredient> opt = ings.get(ingIdx);
                                if (opt.isPresent()) {
                                    data.setSlotString(slotIdx, getSlotStringFromIngredient(opt.get()));
                                }
                            }
                        }
                    }
                }
                return data;
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                data.type = RecipeTypeEnum.SHAPELESS_CRAFTING;
                List<RecipeDisplay> displays = recipe.getDisplays();
                if (!displays.isEmpty() && displays.get(0) instanceof ShapelessCraftingRecipeDisplay disp) {
                    List<SlotDisplay> ings = disp.ingredients();
                    for (int i = 0; i < Math.min(9, ings.size()); i++) {
                        data.setSlotString(i, getSlotStringFromSlotDisplay(ings.get(i)));
                    }
                }
                return data;
            } else if (recipe instanceof SingleStackRecipe singleStack) {
                data.type = RecipeTypeEnum.STONECUTTING;
                data.setSlotString(0, getSlotStringFromIngredient(singleStack.ingredient()));
                return data;
            }
        }

        // 2. Fallback to decompiling from RecipeDisplay
        List<RecipeDisplay> displays = RECIPE_DISPLAYS.get(targetItem);
        if (displays != null && !displays.isEmpty()) {
            RecipeDisplay display = displays.get(0);
            if (display instanceof ShapedCraftingRecipeDisplay shaped) {
                data.type = RecipeTypeEnum.SHAPED_CRAFTING;
                int width = shaped.width();
                int height = shaped.height();
                List<SlotDisplay> ings = shaped.ingredients();
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        int slotIdx = r * 3 + c;
                        if (r < height && c < width) {
                            int ingIdx = r * width + c;
                            if (ingIdx < ings.size()) {
                                data.setSlotString(slotIdx, getSlotStringFromSlotDisplay(ings.get(ingIdx)));
                            }
                        }
                    }
                }
            } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
                data.type = RecipeTypeEnum.SHAPELESS_CRAFTING;
                List<SlotDisplay> ings = shapeless.ingredients();
                for (int i = 0; i < Math.min(9, ings.size()); i++) {
                    data.setSlotString(i, getSlotStringFromSlotDisplay(ings.get(i)));
                }
            } else if (display instanceof FurnaceRecipeDisplay furnace) {
                data.type = RecipeTypeEnum.SMELTING;
                data.experience = furnace.experience();
                data.cookingTime = furnace.duration();
                data.setSlotString(0, getSlotStringFromSlotDisplay(furnace.ingredient()));
            } else if (display instanceof StonecutterRecipeDisplay stonecutter) {
                data.type = RecipeTypeEnum.STONECUTTING;
                data.setSlotString(0, getSlotStringFromSlotDisplay(stonecutter.input()));
            }
            return data;
        }

        // 3. Fallback to cached mod decompiled recipe
        if (DECOMPILED_CACHE.containsKey(targetItem)) {
            return DECOMPILED_CACHE.get(targetItem).copy();
        }

        return null;
    }

    public static String getSlotStringFromIngredient(Ingredient ing) {
        if (ing == null || ing.isEmpty()) return "minecraft:air";
        SlotDisplay display = ing.toDisplay();
        return getSlotStringFromSlotDisplay(display);
    }

    public static Item getItemFromSlotDisplay(SlotDisplay display) {
        if (display == null) return Items.AIR;
        if (display instanceof SlotDisplay.ItemSlotDisplay itemDisplay) {
            return itemDisplay.item().value();
        } else if (display instanceof SlotDisplay.StackSlotDisplay stackDisplay) {
            return stackDisplay.stack().getItem();
        } else if (display instanceof SlotDisplay.WithRemainderSlotDisplay withRemainder) {
            return getItemFromSlotDisplay(withRemainder.input());
        } else if (display instanceof SlotDisplay.TagSlotDisplay tagDisplay) {
            for (var entry : Registries.ITEM.iterateEntries(tagDisplay.tag())) {
                return entry.value();
            }
        } else if (display instanceof SlotDisplay.CompositeSlotDisplay composite) {
            for (SlotDisplay child : composite.contents()) {
                Item item = getItemFromSlotDisplay(child);
                if (item != Items.AIR) return item;
            }
        }
        return Items.AIR;
    }

    public static String getSlotStringFromSlotDisplay(SlotDisplay display) {
        if (display == null) return "minecraft:air";
        if (display instanceof SlotDisplay.ItemSlotDisplay itemDisplay) {
            Identifier id = Registries.ITEM.getId(itemDisplay.item().value());
            return id != null ? id.toString() : "minecraft:air";
        } else if (display instanceof SlotDisplay.StackSlotDisplay stackDisplay) {
            Identifier id = Registries.ITEM.getId(stackDisplay.stack().getItem());
            return id != null ? id.toString() : "minecraft:air";
        } else if (display instanceof SlotDisplay.WithRemainderSlotDisplay withRemainder) {
            return getSlotStringFromSlotDisplay(withRemainder.input());
        } else if (display instanceof SlotDisplay.TagSlotDisplay tagDisplay) {
            return "#" + tagDisplay.tag().id().toString();
        } else if (display instanceof SlotDisplay.CompositeSlotDisplay composite) {
            for (SlotDisplay child : composite.contents()) {
                String str = getSlotStringFromSlotDisplay(child);
                if (!str.equals("minecraft:air")) return str;
            }
        }
        return "minecraft:air";
    }
}
