package com.recipeeditor.inspector;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.item.Item;
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
    private static final Map<Item, List<RecipeEntry<?>>> RECIPE_ENTRIES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Item, List<RecipeDisplay>> RECIPE_DISPLAYS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<Item> KNOWN_RECIPE_ITEMS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Map<Item, CustomRecipeData> DECOMPILED_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Item, List<CustomRecipeData>> DECOMPILED_VARIANTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean cacheInitialized = false;

    public static void invalidateCache() {
        RECIPE_ENTRIES.clear();
        RECIPE_DISPLAYS.clear();
        KNOWN_RECIPE_ITEMS.clear();
        DECOMPILED_CACHE.clear();
        DECOMPILED_VARIANTS.clear();
        TagResolver.clearCache();
        cacheInitialized = false;
    }

    public static void initializeCache(World world) {
        RECIPE_ENTRIES.clear();
        RECIPE_DISPLAYS.clear();
        KNOWN_RECIPE_ITEMS.clear();
        DECOMPILED_CACHE.clear();
        DECOMPILED_VARIANTS.clear();
        TagResolver.clearCache();
        MinecraftClient client = MinecraftClient.getInstance();

        // 1. Scan Fabric Mod & Vanilla JARs directly (clean, fast, 100% offline & online)
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

        cacheInitialized = true;
    }

    private static void scanFabricModJars() {
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            try {
                Optional<Path> dataDir = mod.findPath("data");
                if (dataDir.isPresent()) {
                    try (Stream<Path> stream = Files.walk(dataDir.get())) {
                        stream.filter(p -> {
                            String s = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                            return s.endsWith(".json") && (
                                    s.contains("/recipe/") || s.contains("/recipes/") ||
                                    s.contains("/tags/item/") || s.contains("/tags/items/")
                            );
                        }).forEach(p -> {
                            String s = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                            if (s.contains("/tags/item/") || s.contains("/tags/items/")) {
                                TagResolver.parseTagJson(p);
                            } else {
                                parseModRecipeJson(p);
                            }
                        });
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
                CustomRecipeData decompiled = buildDecompiledRecipeFromJson(obj, resultItem, count);
                if (decompiled != null) {
                    DECOMPILED_VARIANTS.computeIfAbsent(resultItem, k -> new ArrayList<>()).add(decompiled);
                    if (!DECOMPILED_CACHE.containsKey(resultItem)) {
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
        } else if (typeStr.contains("smithing")) {
            data.type = RecipeTypeEnum.SMITHING;
            if (obj.has("template")) {
                data.setSlotString(0, parseIngredientElement(obj.get("template")));
            }
            if (obj.has("base")) {
                data.setSlotString(1, parseIngredientElement(obj.get("base")));
            }
            if (obj.has("addition")) {
                data.setSlotString(2, parseIngredientElement(obj.get("addition")));
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
        } else if (typeStr.contains("campfire")) {
            data.type = RecipeTypeEnum.CAMPFIRE_COOKING;
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
        } else if (obj.has("ingredients")) {
            data.type = RecipeTypeEnum.SHAPED_CRAFTING;
            JsonArray ings = obj.getAsJsonArray("ingredients");
            for (int i = 0; i < Math.min(9, ings.size()); i++) {
                data.setSlotString(i, parseIngredientElement(ings.get(i)));
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
        boolean hasEntry = RECIPE_ENTRIES.containsKey(item) || RECIPE_DISPLAYS.containsKey(item) || KNOWN_RECIPE_ITEMS.contains(item) || !getSyntheticDynamicRecipes(item).isEmpty();
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
        return RECIPE_ENTRIES.containsKey(item) || RECIPE_DISPLAYS.containsKey(item) || KNOWN_RECIPE_ITEMS.contains(item) || !getSyntheticDynamicRecipes(item).isEmpty();
    }

    public static List<CustomRecipeData> getAllRecipeVariants(Item targetItem, World world) {
        if (targetItem == null || targetItem == Items.AIR) return Collections.emptyList();
        if (!cacheInitialized) {
            initializeCache(world);
        }

        List<CustomRecipeData> variants = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();

        java.util.function.Consumer<CustomRecipeData> adder = d -> {
            if (d == null) return;
            List<CustomRecipeData> expanded = expandRecipeTags(d);
            for (CustomRecipeData exp : expanded) {
                if (seenSignatures.add(getRecipeSignature(exp))) {
                    variants.add(exp);
                }
            }
        };

        // 1. From Server Recipe Entries
        List<RecipeEntry<?>> entries = RECIPE_ENTRIES.get(targetItem);
        if (entries != null) {
            for (RecipeEntry<?> entry : entries) {
                adder.accept(decompileFromEntry(entry, targetItem));
            }
        }

        // 2. From Displays
        List<RecipeDisplay> displays = RECIPE_DISPLAYS.get(targetItem);
        if (displays != null) {
            for (RecipeDisplay disp : displays) {
                adder.accept(decompileFromDisplay(disp, targetItem));
            }
        }

        // 3. From Scanned Mod & Vanilla JARs
        List<CustomRecipeData> fromJars = DECOMPILED_VARIANTS.get(targetItem);
        if (fromJars != null) {
            for (CustomRecipeData d : fromJars) {
                adder.accept(d);
            }
        }

        // 4. Dynamic Vanilla Special Recipes (Netherite Upgrades, Bundles, Wools, Beds, Shulkers, Candles, etc.)
        List<CustomRecipeData> synthetics = getSyntheticDynamicRecipes(targetItem);
        for (CustomRecipeData syn : synthetics) {
            adder.accept(syn);
        }

        return variants;
    }

    public static List<CustomRecipeData> expandRecipeTags(CustomRecipeData recipe) {
        if (recipe == null || recipe.patternSlots == null) return Collections.emptyList();

        // 1. Collect all distinct tags used in recipe pattern
        Set<String> tags = new LinkedHashSet<>();
        for (String slot : recipe.patternSlots) {
            if (slot != null && slot.startsWith("#")) {
                tags.add(slot);
            }
        }

        if (tags.isEmpty()) {
            return Collections.singletonList(recipe);
        }

        List<CustomRecipeData> currentLevel = new ArrayList<>();
        currentLevel.add(recipe.copy());

        for (String tag : tags) {
            List<Item> tagItems = TagResolver.getAllItemsForTag(tag);
            if (tagItems.isEmpty()) {
                Item resolved = TagResolver.resolveTag(tag);
                if (resolved != Items.AIR) {
                    tagItems = Collections.singletonList(resolved);
                }
            }

            if (!tagItems.isEmpty()) {
                List<CustomRecipeData> nextLevel = new ArrayList<>();
                for (CustomRecipeData parent : currentLevel) {
                    for (Item item : tagItems) {
                        if (nextLevel.size() >= 32) break;
                        Identifier itemId = Registries.ITEM.getId(item);
                        if (itemId != null) {
                            CustomRecipeData variant = parent.copy();
                            for (int i = 0; i < 9; i++) {
                                if (tag.equals(variant.patternSlots[i])) {
                                    variant.setSlotString(i, itemId.toString());
                                }
                            }
                            nextLevel.add(variant);
                        }
                    }
                    if (nextLevel.size() >= 32) break;
                }
                currentLevel = nextLevel;
                if (currentLevel.size() >= 32) break;
            }
        }

        // Final sanity check: ensure no remaining "#" tags exist in any slot
        for (CustomRecipeData r : currentLevel) {
            for (int i = 0; i < 9; i++) {
                if (r.patternSlots[i] != null && r.patternSlots[i].startsWith("#")) {
                    Item res = TagResolver.resolveTag(r.patternSlots[i]);
                    Identifier id = Registries.ITEM.getId(res);
                    r.setSlotString(i, id != null ? id.toString() : "minecraft:air");
                }
            }
        }

        return currentLevel;
    }

    public static List<CustomRecipeData> getSyntheticDynamicRecipes(Item targetItem) {
        if (targetItem == null || targetItem == Items.AIR) return Collections.emptyList();
        Identifier id = Registries.ITEM.getId(targetItem);
        if (id == null) return Collections.emptyList();

        String path = id.getPath();
        List<CustomRecipeData> list = new ArrayList<>();

        // 1. Netherite Smithing Upgrades (Helmet, Chestplate, Leggings, Boots, Sword, Shovel, Pickaxe, Axe, Hoe)
        if (path.startsWith("netherite_") && id.getNamespace().equals("minecraft")) {
            String baseEquip = path.replace("netherite_", "diamond_");
            Identifier baseId = Identifier.of("minecraft", baseEquip);
            if (Registries.ITEM.containsId(baseId)) {
                CustomRecipeData smithing = new CustomRecipeData(path, id.toString(), 1, RecipeTypeEnum.SMITHING);
                smithing.setSlotString(0, "minecraft:netherite_upgrade_smithing_template");
                smithing.setSlotString(1, "minecraft:" + baseEquip);
                smithing.setSlotString(2, "minecraft:netherite_ingot");
                list.add(smithing);
                return list;
            }
        }

        String[] colors = new String[]{
                "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
        };

        // 2. Colored Bundles (e.g. red_bundle -> bundle + red_dye, or other_bundle + red_dye)
        if (path.endsWith("_bundle") && !path.equals("bundle")) {
            for (String color : colors) {
                if (path.equals(color + "_bundle")) {
                    String dye = "minecraft:" + color + "_dye";
                    // Base undyed bundle + dye
                    CustomRecipeData baseVar = new CustomRecipeData(path, id.toString(), 1, RecipeTypeEnum.SHAPED_CRAFTING);
                    baseVar.setSlotString(0, "minecraft:bundle");
                    baseVar.setSlotString(1, dye);
                    list.add(baseVar);

                    // Re-dye from every other bundle color
                    for (String other : colors) {
                        if (!other.equals(color)) {
                            CustomRecipeData otherVar = new CustomRecipeData(path, id.toString(), 1, RecipeTypeEnum.SHAPED_CRAFTING);
                            otherVar.setSlotString(0, "minecraft:" + other + "_bundle");
                            otherVar.setSlotString(1, dye);
                            list.add(otherVar);
                        }
                    }
                    return list;
                }
            }
        }

        // 3. Undyed Bundle (string + leather)
        if (path.equals("bundle") && id.getNamespace().equals("minecraft")) {
            CustomRecipeData data = new CustomRecipeData("bundle", "minecraft:bundle", 1, RecipeTypeEnum.SHAPED_CRAFTING);
            data.setSlotString(1, "minecraft:string");
            data.setSlotString(4, "minecraft:leather");
            list.add(data);
            return list;
        }

        // 4. Colored Wool (dye from every other wool color)
        if (path.endsWith("_wool")) {
            for (String color : colors) {
                if (path.equals(color + "_wool")) {
                    String dye = "minecraft:" + color + "_dye";
                    if (color.equals("white")) {
                        CustomRecipeData stringCraft = new CustomRecipeData("white_wool_string", "minecraft:white_wool", 1, RecipeTypeEnum.SHAPED_CRAFTING);
                        stringCraft.setSlotString(0, "minecraft:string");
                        stringCraft.setSlotString(1, "minecraft:string");
                        stringCraft.setSlotString(3, "minecraft:string");
                        stringCraft.setSlotString(4, "minecraft:string");
                        list.add(stringCraft);
                    }
                    for (String other : colors) {
                        if (!other.equals(color)) {
                            CustomRecipeData data = new CustomRecipeData(path, id.toString(), 1, RecipeTypeEnum.SHAPED_CRAFTING);
                            data.setSlotString(0, "minecraft:" + other + "_wool");
                            data.setSlotString(1, dye);
                            list.add(data);
                        }
                    }
                    return list;
                }
            }
        }

        // 5. Colored Beds (bed + dye, or 3 wool + 3 planks)
        if (path.endsWith("_bed")) {
            for (String color : colors) {
                if (path.equals(color + "_bed")) {
                    String dye = "minecraft:" + color + "_dye";
                    // Dye from any other bed
                    for (String other : colors) {
                        if (!other.equals(color)) {
                            CustomRecipeData data = new CustomRecipeData(path, id.toString(), 1, RecipeTypeEnum.SHAPED_CRAFTING);
                            data.setSlotString(0, "minecraft:" + other + "_bed");
                            data.setSlotString(1, dye);
                            list.add(data);
                        }
                    }
                    // Shaped craft from 3 wool + 3 planks
                    CustomRecipeData bedCraft = new CustomRecipeData(path, id.toString(), 1, RecipeTypeEnum.SHAPED_CRAFTING);
                    bedCraft.setSlotString(0, "minecraft:" + color + "_wool");
                    bedCraft.setSlotString(1, "minecraft:" + color + "_wool");
                    bedCraft.setSlotString(2, "minecraft:" + color + "_wool");
                    bedCraft.setSlotString(3, "#minecraft:planks");
                    bedCraft.setSlotString(4, "#minecraft:planks");
                    bedCraft.setSlotString(5, "#minecraft:planks");
                    list.add(bedCraft);
                    return list;
                }
            }
        }

        // 6. Colored Candles (candle + dye, or other_candle + dye)
        if (path.endsWith("_candle") && !path.equals("candle")) {
            for (String color : colors) {
                if (path.equals(color + "_candle")) {
                    String dye = "minecraft:" + color + "_dye";
                    CustomRecipeData baseVar = new CustomRecipeData(path, id.toString(), 1, RecipeTypeEnum.SHAPED_CRAFTING);
                    baseVar.setSlotString(0, "minecraft:candle");
                    baseVar.setSlotString(1, dye);
                    list.add(baseVar);

                    for (String other : colors) {
                        if (!other.equals(color)) {
                            CustomRecipeData otherVar = new CustomRecipeData(path, id.toString(), 1, RecipeTypeEnum.SHAPED_CRAFTING);
                            otherVar.setSlotString(0, "minecraft:" + other + "_candle");
                            otherVar.setSlotString(1, dye);
                            list.add(otherVar);
                        }
                    }
                    return list;
                }
            }
        }

        // 7. Colored Shulker Boxes (shulker_box + dye, or other_shulker_box + dye)
        if (path.endsWith("_shulker_box") && !path.equals("shulker_box")) {
            for (String color : colors) {
                if (path.equals(color + "_shulker_box")) {
                    String dye = "minecraft:" + color + "_dye";
                    CustomRecipeData baseVar = new CustomRecipeData(path, id.toString(), 1, RecipeTypeEnum.SHAPED_CRAFTING);
                    baseVar.setSlotString(0, "minecraft:shulker_box");
                    baseVar.setSlotString(1, dye);
                    list.add(baseVar);

                    for (String other : colors) {
                        if (!other.equals(color)) {
                            CustomRecipeData otherVar = new CustomRecipeData(path, id.toString(), 1, RecipeTypeEnum.SHAPED_CRAFTING);
                            otherVar.setSlotString(0, "minecraft:" + other + "_shulker_box");
                            otherVar.setSlotString(1, dye);
                            list.add(otherVar);
                        }
                    }
                    return list;
                }
            }
        }

        // 8. Colored Carpets (2 wool, or 8 carpets + dye)
        if (path.endsWith("_carpet")) {
            for (String color : colors) {
                if (path.equals(color + "_carpet")) {
                    String dye = "minecraft:" + color + "_dye";
                    CustomRecipeData woolCraft = new CustomRecipeData(path, id.toString(), 3, RecipeTypeEnum.SHAPED_CRAFTING);
                    woolCraft.setSlotString(0, "minecraft:" + color + "_wool");
                    woolCraft.setSlotString(1, "minecraft:" + color + "_wool");
                    list.add(woolCraft);

                    for (String other : colors) {
                        if (!other.equals(color)) {
                            CustomRecipeData data = new CustomRecipeData(path, id.toString(), 8, RecipeTypeEnum.SHAPED_CRAFTING);
                            for (int i = 0; i < 9; i++) {
                                if (i == 4) data.setSlotString(i, dye);
                                else data.setSlotString(i, "minecraft:" + other + "_carpet");
                            }
                            list.add(data);
                        }
                    }
                    return list;
                }
            }
        }

        return list;
    }

    private static String getRecipeSignature(CustomRecipeData d) {
        if (d == null) return "";
        return d.type + ":" + Arrays.toString(d.patternSlots);
    }

    public static CustomRecipeData decompileRecipe(Item targetItem, World world) {
        List<CustomRecipeData> variants = getAllRecipeVariants(targetItem, world);
        if (!variants.isEmpty()) {
            return variants.get(0).copy();
        }
        return null;
    }

    public static CustomRecipeData decompileFromEntry(RecipeEntry<?> entry, Item targetItem) {
        if (entry == null || targetItem == null) return null;
        Identifier resId = Registries.ITEM.getId(targetItem);
        CustomRecipeData data = new CustomRecipeData(
                resId != null ? resId.getPath() : "recipe",
                resId != null ? resId.toString() : "minecraft:air",
                1,
                RecipeTypeEnum.SHAPED_CRAFTING
        );

        Recipe<?> recipe = entry.value();
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
            data.type = RecipeTypeEnum.SHAPED_CRAFTING;
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
        } else if (recipe instanceof SmithingTransformRecipe smithing) {
            data.type = RecipeTypeEnum.SMITHING;
            smithing.template().ifPresent(ing -> data.setSlotString(0, getSlotStringFromIngredient(ing)));
            smithing.base().ifPresent(ing -> data.setSlotString(1, getSlotStringFromIngredient(ing)));
            smithing.addition().ifPresent(ing -> data.setSlotString(2, getSlotStringFromIngredient(ing)));
            return data;
        } else if (recipe instanceof BlastingRecipe blasting) {
            data.type = RecipeTypeEnum.BLASTING;
            data.setSlotString(0, getSlotStringFromIngredient(blasting.ingredient()));
            return data;
        } else if (recipe instanceof SmokingRecipe smoking) {
            data.type = RecipeTypeEnum.SMOKING;
            data.setSlotString(0, getSlotStringFromIngredient(smoking.ingredient()));
            return data;
        } else if (recipe instanceof CampfireCookingRecipe campfire) {
            data.type = RecipeTypeEnum.CAMPFIRE_COOKING;
            data.setSlotString(0, getSlotStringFromIngredient(campfire.ingredient()));
            return data;
        } else if (recipe instanceof SmeltingRecipe smelting) {
            data.type = RecipeTypeEnum.SMELTING;
            data.setSlotString(0, getSlotStringFromIngredient(smelting.ingredient()));
            return data;
        }
        return null;
    }

    public static CustomRecipeData decompileFromDisplay(RecipeDisplay display, Item targetItem) {
        if (display == null || targetItem == null) return null;
        Identifier resId = Registries.ITEM.getId(targetItem);
        CustomRecipeData data = new CustomRecipeData(
                resId != null ? resId.getPath() : "recipe",
                resId != null ? resId.toString() : "minecraft:air",
                1,
                RecipeTypeEnum.SHAPED_CRAFTING
        );

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
            return data;
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            data.type = RecipeTypeEnum.SHAPED_CRAFTING;
            List<SlotDisplay> ings = shapeless.ingredients();
            for (int i = 0; i < Math.min(9, ings.size()); i++) {
                data.setSlotString(i, getSlotStringFromSlotDisplay(ings.get(i)));
            }
            return data;
        } else if (display instanceof FurnaceRecipeDisplay furnace) {
            data.type = RecipeTypeEnum.SMELTING;
            data.experience = furnace.experience();
            data.cookingTime = furnace.duration();
            data.setSlotString(0, getSlotStringFromSlotDisplay(furnace.ingredient()));
            return data;
        } else if (display instanceof StonecutterRecipeDisplay stonecutter) {
            data.type = RecipeTypeEnum.STONECUTTING;
            data.setSlotString(0, getSlotStringFromSlotDisplay(stonecutter.input()));
            return data;
        } else if (display instanceof SmithingRecipeDisplay smithing) {
            data.type = RecipeTypeEnum.SMITHING;
            data.setSlotString(0, getSlotStringFromSlotDisplay(smithing.template()));
            data.setSlotString(1, getSlotStringFromSlotDisplay(smithing.base()));
            data.setSlotString(2, getSlotStringFromSlotDisplay(smithing.addition()));
            return data;
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
            return TagResolver.resolveTag(tagDisplay.tag().id().toString());
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

    public static List<RecipeConflictInfo> findConflicts(CustomRecipeData candidate, Item currentTargetItem, World world, RecipeEditorConfig config) {
        if (candidate == null || currentTargetItem == null || currentTargetItem == Items.AIR) {
            return Collections.emptyList();
        }

        boolean hasIngredients = false;
        if (candidate.patternSlots != null) {
            for (String s : candidate.patternSlots) {
                if (s != null && !s.isEmpty() && !s.equals("minecraft:air")) {
                    hasIngredients = true;
                    break;
                }
            }
        }
        if (!hasIngredients) return Collections.emptyList();

        List<RecipeConflictInfo> conflicts = new ArrayList<>();
        Set<Item> seenConflictingItems = new HashSet<>();

        // 1. Check saved custom recipes for OTHER items
        if (config != null && config.recipes != null) {
            for (CustomRecipeData saved : config.recipes.values()) {
                if (!saved.enabled || saved.type != candidate.type) continue;
                Item savedItem = saved.getResultItem();
                if (savedItem != Items.AIR && savedItem != currentTargetItem) {
                    if (isPatternMatching(saved, candidate)) {
                        if (seenConflictingItems.add(savedItem)) {
                            String source = net.minecraft.text.Text.translatable("recipeeditor.gui.conflict_source_custom").getString();
                            conflicts.add(new RecipeConflictInfo(candidate, savedItem, source, candidate.type));
                        }
                    }
                }
            }
        }

        // 2. Check scanned Vanilla and Mod recipes
        if (!cacheInitialized) {
            initializeCache(world);
        }

        for (Item item : Registries.ITEM) {
            if (item == Items.AIR || item == currentTargetItem || seenConflictingItems.contains(item)) continue;

            List<CustomRecipeData> variants = getAllRecipeVariants(item, world);
            for (CustomRecipeData v : variants) {
                if (v.type == candidate.type && isPatternMatching(v, candidate)) {
                    if (seenConflictingItems.add(item)) {
                        Identifier id = Registries.ITEM.getId(item);
                        String source;
                        if (id != null && id.getNamespace().equals("minecraft")) {
                            source = net.minecraft.text.Text.translatable("recipeeditor.gui.conflict_source_vanilla").getString();
                        } else {
                            String modName = getFriendlyModName(id != null ? id.getNamespace() : "");
                            source = net.minecraft.text.Text.translatable("recipeeditor.gui.conflict_source_mod", modName).getString();
                        }
                        conflicts.add(new RecipeConflictInfo(candidate, item, source, candidate.type));
                        break;
                    }
                }
            }
        }

        return conflicts;
    }

    public static boolean isPatternMatching(CustomRecipeData a, CustomRecipeData b) {
        if (a == null || b == null || a.type != b.type) return false;

        if (a.type == RecipeTypeEnum.SMELTING || a.type == RecipeTypeEnum.BLASTING ||
            a.type == RecipeTypeEnum.SMOKING || a.type == RecipeTypeEnum.STONECUTTING ||
            a.type == RecipeTypeEnum.CAMPFIRE_COOKING) {
            Item itemA = a.getItemAt(0);
            Item itemB = b.getItemAt(0);
            return itemA != Items.AIR && itemA == itemB;
        }

        if (a.type == RecipeTypeEnum.SMITHING) {
            for (int i = 0; i < 3; i++) {
                Item itemA = a.getItemAt(i);
                Item itemB = b.getItemAt(i);
                if (itemA != itemB) return false;
            }
            return true;
        }

        // Shaped Crafting 3x3: compare slots directly and with horizontal mirror flip
        boolean directMatch = true;
        for (int i = 0; i < 9; i++) {
            if (a.getItemAt(i) != b.getItemAt(i)) {
                directMatch = false;
                break;
            }
        }
        if (directMatch) return true;

        boolean mirrorMatch = true;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (a.getItemAt(r * 3 + c) != b.getItemAt(r * 3 + (2 - c))) {
                    mirrorMatch = false;
                    break;
                }
            }
            if (!mirrorMatch) break;
        }
        return mirrorMatch;
    }

    public static String getFriendlyModName(String namespace) {
        if (namespace == null || namespace.isEmpty() || namespace.equals("minecraft")) {
            return "Minecraft";
        }
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(namespace);
        if (container.isPresent()) {
            return container.get().getMetadata().getName();
        }
        return Character.toUpperCase(namespace.charAt(0)) + namespace.substring(1);
    }
}
