package com.recipeeditor.inspector;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal Tag Resolver for Minecraft 1.21.4
 * Resolves item tags (e.g. #minecraft:planks, #minecraft:stone_tool_materials, #c:iron_ingots)
 * to actual Item instances across all environments:
 * 1. Active world dynamic RegistryManager
 * 2. Static Registries.ITEM
 * 3. JAR/DataPack indexed tags
 * 4. Comprehensive built-in fallbacks & heuristic keyword matching
 */
public class TagResolver {
    private static final Map<String, List<String>> TAG_ITEMS = new ConcurrentHashMap<>();
    private static final Map<String, Item> RESOLVE_CACHE = new ConcurrentHashMap<>();

    private static final Map<String, String> STATIC_FALLBACKS = new HashMap<>();

    static {
        // Vanilla standard tags
        STATIC_FALLBACKS.put("minecraft:planks", "minecraft:oak_planks");
        STATIC_FALLBACKS.put("minecraft:stone_tool_materials", "minecraft:cobblestone");
        STATIC_FALLBACKS.put("minecraft:stone_crafting_materials", "minecraft:cobblestone");
        STATIC_FALLBACKS.put("minecraft:logs", "minecraft:oak_log");
        STATIC_FALLBACKS.put("minecraft:logs_that_burn", "minecraft:oak_log");
        STATIC_FALLBACKS.put("minecraft:wool", "minecraft:white_wool");
        STATIC_FALLBACKS.put("minecraft:wool_carpets", "minecraft:white_carpet");
        STATIC_FALLBACKS.put("minecraft:coals", "minecraft:coal");
        STATIC_FALLBACKS.put("minecraft:sand", "minecraft:sand");
        STATIC_FALLBACKS.put("minecraft:smelts_to_glass", "minecraft:sand");
        STATIC_FALLBACKS.put("minecraft:wooden_buttons", "minecraft:oak_button");
        STATIC_FALLBACKS.put("minecraft:wooden_doors", "minecraft:oak_door");
        STATIC_FALLBACKS.put("minecraft:wooden_trapdoors", "minecraft:oak_trapdoor");
        STATIC_FALLBACKS.put("minecraft:wooden_fences", "minecraft:oak_fence");
        STATIC_FALLBACKS.put("minecraft:wooden_slabs", "minecraft:oak_slab");
        STATIC_FALLBACKS.put("minecraft:wooden_stairs", "minecraft:oak_stairs");
        STATIC_FALLBACKS.put("minecraft:wooden_pressure_plates", "minecraft:oak_pressure_plate");
        STATIC_FALLBACKS.put("minecraft:saplings", "minecraft:oak_sapling");
        STATIC_FALLBACKS.put("minecraft:leaves", "minecraft:oak_leaves");
        STATIC_FALLBACKS.put("minecraft:boats", "minecraft:oak_boat");
        STATIC_FALLBACKS.put("minecraft:chest_boats", "minecraft:oak_chest_boat");
        STATIC_FALLBACKS.put("minecraft:signs", "minecraft:oak_sign");
        STATIC_FALLBACKS.put("minecraft:hanging_signs", "minecraft:oak_hanging_sign");
        STATIC_FALLBACKS.put("minecraft:iron_tool_materials", "minecraft:iron_ingot");
        STATIC_FALLBACKS.put("minecraft:gold_tool_materials", "minecraft:gold_ingot");
        STATIC_FALLBACKS.put("minecraft:diamond_tool_materials", "minecraft:diamond");
        STATIC_FALLBACKS.put("minecraft:netherite_tool_materials", "minecraft:netherite_ingot");
        STATIC_FALLBACKS.put("minecraft:trim_materials", "minecraft:diamond");

        // Conventional / Fabric / Common tags
        STATIC_FALLBACKS.put("c:iron_ingots", "minecraft:iron_ingot");
        STATIC_FALLBACKS.put("c:gold_ingots", "minecraft:gold_ingot");
        STATIC_FALLBACKS.put("c:copper_ingots", "minecraft:copper_ingot");
        STATIC_FALLBACKS.put("c:netherite_ingots", "minecraft:netherite_ingot");
        STATIC_FALLBACKS.put("c:diamonds", "minecraft:diamond");
        STATIC_FALLBACKS.put("c:emeralds", "minecraft:emerald");
        STATIC_FALLBACKS.put("c:wooden_rods", "minecraft:stick");
        STATIC_FALLBACKS.put("c:rods/wooden", "minecraft:stick");
        STATIC_FALLBACKS.put("c:rods", "minecraft:stick");
        STATIC_FALLBACKS.put("c:stones", "minecraft:stone");
        STATIC_FALLBACKS.put("c:cobblestones", "minecraft:cobblestone");
        STATIC_FALLBACKS.put("c:glass_blocks", "minecraft:glass");
        STATIC_FALLBACKS.put("c:glass_panes", "minecraft:glass_pane");
        STATIC_FALLBACKS.put("c:chests", "minecraft:chest");
        STATIC_FALLBACKS.put("c:wooden_chests", "minecraft:chest");
        STATIC_FALLBACKS.put("c:strings", "minecraft:string");
        STATIC_FALLBACKS.put("c:feathers", "minecraft:feather");
        STATIC_FALLBACKS.put("c:leather", "minecraft:leather");
        STATIC_FALLBACKS.put("c:raw_iron_ores", "minecraft:raw_iron");
        STATIC_FALLBACKS.put("c:raw_gold_ores", "minecraft:raw_gold");
        STATIC_FALLBACKS.put("c:raw_copper_ores", "minecraft:raw_copper");
        STATIC_FALLBACKS.put("c:iron_ores", "minecraft:iron_ore");
        STATIC_FALLBACKS.put("c:gold_ores", "minecraft:gold_ore");
        STATIC_FALLBACKS.put("c:copper_ores", "minecraft:copper_ore");
        STATIC_FALLBACKS.put("c:coal_ores", "minecraft:coal_ore");
        STATIC_FALLBACKS.put("c:diamond_ores", "minecraft:diamond_ore");
        STATIC_FALLBACKS.put("c:emerald_ores", "minecraft:emerald_ore");
        STATIC_FALLBACKS.put("c:lapis_ores", "minecraft:lapis_ore");
        STATIC_FALLBACKS.put("c:redstone_ores", "minecraft:redstone_ore");
        STATIC_FALLBACKS.put("c:dyes", "minecraft:red_dye");
        STATIC_FALLBACKS.put("c:red_dyes", "minecraft:red_dye");
        STATIC_FALLBACKS.put("c:blue_dyes", "minecraft:blue_dye");
        STATIC_FALLBACKS.put("c:green_dyes", "minecraft:green_dye");
        STATIC_FALLBACKS.put("c:yellow_dyes", "minecraft:yellow_dye");
        STATIC_FALLBACKS.put("c:black_dyes", "minecraft:black_dye");
        STATIC_FALLBACKS.put("c:white_dyes", "minecraft:white_dye");
    }

    private static final Map<String, List<String>> STATIC_TAG_EXPANSIONS = new HashMap<>();
    static {
        STATIC_TAG_EXPANSIONS.put("minecraft:planks", List.of(
                "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks",
                "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks",
                "minecraft:mangrove_planks", "minecraft:cherry_planks", "minecraft:bamboo_planks",
                "minecraft:crimson_planks", "minecraft:warped_planks"
        ));
        STATIC_TAG_EXPANSIONS.put("minecraft:logs", List.of(
                "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log",
                "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:dark_oak_log",
                "minecraft:mangrove_log", "minecraft:cherry_log", "minecraft:bamboo_block",
                "minecraft:crimson_stem", "minecraft:warped_stem"
        ));
        STATIC_TAG_EXPANSIONS.put("minecraft:logs_that_burn", List.of(
                "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log",
                "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:dark_oak_log",
                "minecraft:mangrove_log", "minecraft:cherry_log"
        ));
        STATIC_TAG_EXPANSIONS.put("minecraft:wool", List.of(
                "minecraft:white_wool", "minecraft:orange_wool", "minecraft:magenta_wool",
                "minecraft:light_blue_wool", "minecraft:yellow_wool", "minecraft:lime_wool",
                "minecraft:pink_wool", "minecraft:gray_wool", "minecraft:light_gray_wool",
                "minecraft:cyan_wool", "minecraft:purple_wool", "minecraft:blue_wool",
                "minecraft:brown_wool", "minecraft:green_wool", "minecraft:red_wool", "minecraft:black_wool"
        ));
        STATIC_TAG_EXPANSIONS.put("minecraft:coals", List.of("minecraft:coal", "minecraft:charcoal"));
        STATIC_TAG_EXPANSIONS.put("minecraft:stone_tool_materials", List.of("minecraft:cobblestone", "minecraft:blackstone", "minecraft:cobbled_deepslate"));
        STATIC_TAG_EXPANSIONS.put("minecraft:stone_crafting_materials", List.of("minecraft:cobblestone", "minecraft:blackstone", "minecraft:cobbled_deepslate"));
        STATIC_TAG_EXPANSIONS.put("minecraft:sand", List.of("minecraft:sand", "minecraft:red_sand"));
        STATIC_TAG_EXPANSIONS.put("minecraft:smelts_to_glass", List.of("minecraft:sand", "minecraft:red_sand"));
    }

    public static void clearWorldCache() {
        RESOLVE_CACHE.clear();
    }

    public static void clearCache() {
        RESOLVE_CACHE.clear();
        TAG_ITEMS.clear();
    }

    /**
     * Returns all distinct items matching a given tag (e.g. all 11 wood planks for #minecraft:planks).
     */
    public static List<Item> getAllItemsForTag(String tagString) {
        if (tagString == null || tagString.isEmpty()) return Collections.emptyList();
        String tagId = tagString.startsWith("#") ? tagString.substring(1) : tagString;
        Set<Item> items = new LinkedHashSet<>();

        // 1. World dynamic registry entries (if in-world)
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                Identifier id = Identifier.tryParse(tagId);
                if (id != null) {
                    TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, id);
                    var entryList = client.world.getRegistryManager().getOrThrow(RegistryKeys.ITEM).getOptional(tagKey);
                    if (entryList.isPresent()) {
                        for (var entry : entryList.get()) {
                            Item item = entry.value();
                            if (item != null && item != Items.AIR) {
                                items.add(item);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. Static Registries.ITEM
        try {
            Identifier id = Identifier.tryParse(tagId);
            if (id != null) {
                TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, id);
                for (var entry : Registries.ITEM.iterateEntries(tagKey)) {
                    if (entry.value() != null && entry.value() != Items.AIR) {
                        items.add(entry.value());
                    }
                }
            }
        } catch (Exception ignored) {}

        // 3. Scanned Mod TAG_ITEMS JSON
        List<String> values = TAG_ITEMS.get(tagId);
        if (values != null && !values.isEmpty()) {
            for (String val : values) {
                if (val.startsWith("#")) {
                    items.addAll(getAllItemsForTag(val));
                } else {
                    Identifier id = Identifier.tryParse(val);
                    if (id != null && Registries.ITEM.containsId(id)) {
                        Item item = Registries.ITEM.get(id);
                        if (item != Items.AIR) items.add(item);
                    }
                }
            }
        }

        // 4. Static expansion table (e.g. all vanilla plank types before world load)
        List<String> staticList = STATIC_TAG_EXPANSIONS.get(tagId);
        if (staticList != null) {
            for (String idStr : staticList) {
                Identifier id = Identifier.tryParse(idStr);
                if (id != null && Registries.ITEM.containsId(id)) {
                    Item item = Registries.ITEM.get(id);
                    if (item != Items.AIR) items.add(item);
                }
            }
        }

        // 5. Fallback single resolve
        if (items.isEmpty()) {
            Item single = resolveTag(tagId);
            if (single != Items.AIR) {
                items.add(single);
            }
        }

        return new ArrayList<>(items);
    }

    /**
     * Parses a tag JSON file located in data/<namespace>/tags/item/<path>.json
     */
    public static void parseTagJson(Path path) {
        try {
            String norm = path.toString().replace('\\', '/');
            int dataIdx = norm.indexOf("/data/");
            if (dataIdx == -1) {
                if (norm.startsWith("data/")) dataIdx = 0;
                else return;
            } else {
                norm = norm.substring(dataIdx + 1); // remove leading slash before data
            }

            // norm is now "data/<namespace>/tags/item/<subpath>.json" or "data/<namespace>/tags/items/<subpath>.json"
            String[] parts = norm.split("/");
            if (parts.length < 5) return;

            String namespace = parts[1];
            // find where tags/item starts
            int tagItemIdx = -1;
            for (int i = 2; i < parts.length - 1; i++) {
                if (parts[i].equals("item") || parts[i].equals("items")) {
                    tagItemIdx = i + 1;
                    break;
                }
            }
            if (tagItemIdx == -1 || tagItemIdx >= parts.length) return;

            StringBuilder subPath = new StringBuilder();
            for (int i = tagItemIdx; i < parts.length; i++) {
                if (subPath.length() > 0) subPath.append('/');
                subPath.append(parts[i]);
            }
            String tagPathStr = subPath.toString();
            if (tagPathStr.endsWith(".json")) {
                tagPathStr = tagPathStr.substring(0, tagPathStr.length() - 5);
            }

            String tagId = namespace + ":" + tagPathStr;

            try (Reader reader = Files.newBufferedReader(path)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                if (obj.has("values")) {
                    JsonArray arr = obj.getAsJsonArray("values");
                    List<String> entries = new ArrayList<>();
                    for (JsonElement el : arr) {
                        if (el.isJsonPrimitive()) {
                            entries.add(el.getAsString());
                        } else if (el.isJsonObject()) {
                            JsonObject valObj = el.getAsJsonObject();
                            if (valObj.has("id")) {
                                entries.add(valObj.get("id").getAsString());
                            }
                        }
                    }
                    if (!entries.isEmpty()) {
                        TAG_ITEMS.put(tagId, entries);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Resolves a tag string (e.g. "#minecraft:planks" or "minecraft:planks") to a concrete Item.
     */
    public static Item resolveTag(String tagString) {
        if (tagString == null || tagString.isEmpty()) return Items.AIR;
        String tagId = tagString.startsWith("#") ? tagString.substring(1) : tagString;

        if (RESOLVE_CACHE.containsKey(tagId)) {
            return RESOLVE_CACHE.get(tagId);
        }

        Item resolved = doResolveTag(tagId, new HashSet<>());
        if (resolved != Items.AIR) {
            RESOLVE_CACHE.put(tagId, resolved);
        }
        return resolved;
    }

    private static Item doResolveTag(String tagId, Set<String> visited) {
        if (visited.contains(tagId)) return Items.AIR;
        visited.add(tagId);

        // 1. Try world registry (when inside active world)
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                Identifier id = Identifier.tryParse(tagId);
                if (id != null) {
                    TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, id);
                    var entryList = client.world.getRegistryManager().getOrThrow(RegistryKeys.ITEM).getOptional(tagKey);
                    if (entryList.isPresent() && entryList.get().size() > 0) {
                        Item item = entryList.get().get(0).value();
                        if (item != null && item != Items.AIR) {
                            return item;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. Try static Registries.ITEM if entries are available
        try {
            Identifier id = Identifier.tryParse(tagId);
            if (id != null) {
                TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, id);
                for (var entry : Registries.ITEM.iterateEntries(tagKey)) {
                    if (entry.value() != null && entry.value() != Items.AIR) {
                        return entry.value();
                    }
                }
            }
        } catch (Exception ignored) {}

        // 3. Try scanned TAG_ITEMS map
        List<String> values = TAG_ITEMS.get(tagId);
        if (values != null && !values.isEmpty()) {
            for (String val : values) {
                if (val.startsWith("#")) {
                    Item sub = doResolveTag(val.substring(1), visited);
                    if (sub != Items.AIR) return sub;
                } else {
                    Identifier id = Identifier.tryParse(val);
                    if (id != null && Registries.ITEM.containsId(id)) {
                        Item item = Registries.ITEM.get(id);
                        if (item != Items.AIR) return item;
                    }
                }
            }
        }

        // 4. Try static exact fallback dictionary
        if (STATIC_FALLBACKS.containsKey(tagId)) {
            Identifier id = Identifier.tryParse(STATIC_FALLBACKS.get(tagId));
            if (id != null && Registries.ITEM.containsId(id)) {
                Item item = Registries.ITEM.get(id);
                if (item != Items.AIR) return item;
            }
        }

        // 5. Heuristic keyword matching for unknown/modded tags
        Item heuristic = matchHeuristic(tagId.toLowerCase(Locale.ROOT));
        if (heuristic != Items.AIR) {
            return heuristic;
        }

        return Items.AIR;
    }

    private static Item matchHeuristic(String s) {
        if (s.contains("plank")) return Items.OAK_PLANKS;
        if (s.contains("cobble")) return Items.COBBLESTONE;
        if (s.contains("stone")) return Items.STONE;
        if (s.contains("log") || s.contains("wood")) return Items.OAK_LOG;
        if (s.contains("wool")) return Items.WHITE_WOOL;
        if (s.contains("carpet")) return Items.WHITE_CARPET;
        if (s.contains("stick") || s.contains("rod")) return Items.STICK;
        if (s.contains("iron") && (s.contains("ingot") || s.contains("metal"))) return Items.IRON_INGOT;
        if (s.contains("gold") && s.contains("ingot")) return Items.GOLD_INGOT;
        if (s.contains("copper") && s.contains("ingot")) return Items.COPPER_INGOT;
        if (s.contains("netherite") && s.contains("ingot")) return Items.NETHERITE_INGOT;
        if (s.contains("diamond")) return Items.DIAMOND;
        if (s.contains("emerald")) return Items.EMERALD;
        if (s.contains("coal")) return Items.COAL;
        if (s.contains("glass_pane") || s.contains("panes")) return Items.GLASS_PANE;
        if (s.contains("glass")) return Items.GLASS;
        if (s.contains("sand")) return Items.SAND;
        if (s.contains("dirt")) return Items.DIRT;
        if (s.contains("gravel")) return Items.GRAVEL;
        if (s.contains("chest")) return Items.CHEST;
        if (s.contains("string")) return Items.STRING;
        if (s.contains("feather")) return Items.FEATHER;
        if (s.contains("leather")) return Items.LEATHER;
        if (s.contains("slab")) return Items.OAK_SLAB;
        if (s.contains("stair")) return Items.OAK_STAIRS;
        if (s.contains("button")) return Items.OAK_BUTTON;
        if (s.contains("door")) return Items.OAK_DOOR;
        if (s.contains("trapdoor")) return Items.OAK_TRAPDOOR;
        if (s.contains("fence")) return Items.OAK_FENCE;
        if (s.contains("sign")) return Items.OAK_SIGN;
        if (s.contains("boat")) return Items.OAK_BOAT;
        if (s.contains("flower")) return Items.DANDELION;
        if (s.contains("dye")) return Items.RED_DYE;
        if (s.contains("seed")) return Items.WHEAT_SEEDS;
        if (s.contains("sapling")) return Items.OAK_SAPLING;
        if (s.contains("leaf") || s.contains("leaves")) return Items.OAK_LEAVES;
        if (s.contains("brick")) return Items.BRICK;
        if (s.contains("paper")) return Items.PAPER;
        if (s.contains("book")) return Items.BOOK;
        if (s.contains("raw_iron")) return Items.RAW_IRON;
        if (s.contains("raw_gold")) return Items.RAW_GOLD;
        if (s.contains("raw_copper")) return Items.RAW_COPPER;
        if (s.contains("ore")) return Items.IRON_ORE;

        return Items.AIR;
    }
}
