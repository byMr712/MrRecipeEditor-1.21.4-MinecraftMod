package com.recipeeditor.inspector;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.*;
import net.minecraft.world.level.Level;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class RecipeInspector {
    private static final Gson GSON = new Gson();
    private static final Map<Item, List<RecipeHolder<?>>> RECIPE_ENTRIES = new ConcurrentHashMap<>();
    private static final Map<Item, List<RecipeDisplay>> RECIPE_DISPLAYS = new ConcurrentHashMap<>();
    private static final Set<Item> KNOWN_RECIPE_ITEMS = ConcurrentHashMap.newKeySet();
    private static final Map<Item, List<CustomRecipeData>> DECOMPILED_VARIANTS = new ConcurrentHashMap<>();
    private static final Map<Item, List<CustomRecipeData>> CACHED_ITEM_VARIANTS = new ConcurrentHashMap<>();
    private static final Set<Item> SYNTHETIC_ITEMS = ConcurrentHashMap.newKeySet();
    private static final Map<Item, Set<Item>> INGREDIENT_TO_ITEMS = new ConcurrentHashMap<>();
    /** Multilingual item name index: translationKey -> Set of lowercase names from all scanned lang files */
    private static final Map<String, Set<String>> ITEM_LANG_NAMES = new ConcurrentHashMap<>();
    private static volatile boolean cacheInitialized = false;
    private static volatile boolean jarsScanned = false;
    private static volatile boolean jarsScanning = false;
    private static volatile boolean langIndexedViaRM = false;

    public static synchronized void startJarScanAsync() {
        if (jarsScanned || jarsScanning) return;
        jarsScanning = true;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                scanFabricModJars();
                if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                    ClientLangHelper.indexDiskAssetLanguages();
                }
                indexCurrentLanguage();
                jarsScanned = true;
            } catch (Throwable ignored) {
            } finally {
                jarsScanning = false;
            }
        });
    }

    public static boolean isScanningJars() {
        return jarsScanning;
    }

    /** Indexes all launcher disk assets and ResourceManager translation storages async. */
    public static synchronized void startLangIndexViaRMAsync() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
        if (langIndexedViaRM) return;
        langIndexedViaRM = true;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                ClientLangHelper.indexDiskAssetLanguages();
                ClientLangHelper.indexResourceManagerLanguages();
            } catch (Throwable ignored) {}
        });
    }

    public static void invalidateWorldCache() {
        RECIPE_ENTRIES.clear();
        RECIPE_DISPLAYS.clear();
        CACHED_ITEM_VARIANTS.clear();
        INGREDIENT_TO_ITEMS.clear();
        SYNTHETIC_ITEMS.clear();
        TagResolver.clearWorldCache();
        clearItemNameCache();
        cacheInitialized = false;
    }

    public static void invalidateCache() {
        CACHED_ITEM_VARIANTS.clear();
        DECOMPILED_VARIANTS.clear();
        TagResolver.clearCache();
    }

    public static void clearAllCaches() {
        RECIPE_ENTRIES.clear();
        RECIPE_DISPLAYS.clear();
        KNOWN_RECIPE_ITEMS.clear();
        CACHED_ITEM_VARIANTS.clear();
        DECOMPILED_VARIANTS.clear();
        ITEM_LANG_NAMES.clear();
        TagResolver.clearCache();
        clearItemNameCache();
        cacheInitialized = false;
        jarsScanned = false;
        jarsScanning = false;
        langIndexedViaRM = false;
    }

    public static void initializeCache(Level world) {
        RECIPE_ENTRIES.clear();
        RECIPE_DISPLAYS.clear();
        CACHED_ITEM_VARIANTS.clear();

        // 1. Scan Fabric Mod & Vanilla JARs asynchronously if not already scanned or scanning
        if (!jarsScanned && !jarsScanning) {
            startJarScanAsync();
        }

        // 2. Scan active Server Recipe Manager (if ServerLevel available)
        if (world instanceof ServerLevel sw) {
            MinecraftServer server = sw.getServer();
            if (server != null && server.getRecipeManager() != null) {
                for (RecipeHolder<?> entry : server.getRecipeManager().getRecipes()) {
                    indexRecipeEntry(entry);
                }
                finishCacheInitialization();
                return;
            }
        }

        // 3. Scan Client Recipe Book / Integrated server (if on Client)
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            if (ClientLangHelper.initFromClient(world)) {
                finishCacheInitialization();
                return;
            }
        }

        finishCacheInitialization();
    }

    private static void finishCacheInitialization() {
        if (SYNTHETIC_ITEMS.isEmpty()) {
            for (Item item : BuiltInRegistries.ITEM) {
                if (!getSyntheticDynamicRecipes(item).isEmpty()) {
                    SYNTHETIC_ITEMS.add(item);
                }
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
                // Scan lang files (assets/<ns>/lang/*.json) for multilingual item name index
                Optional<Path> assetsDir = mod.findPath("assets");
                if (assetsDir.isPresent()) {
                    try (Stream<Path> stream = Files.walk(assetsDir.get())) {
                        stream.filter(p -> {
                            String s = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                            return s.endsWith(".json") && s.contains("/lang/");
                        }).forEach(RecipeInspector::parseLangJson);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Parse a lang JSON file and index item translation keys → localized name (lowercase).
     * This powers multilingual search: searching "stick" on RU client or "палка" on EN client.
     */
    private static void parseLangJson(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            Language.loadFromJson(is, (key, value) -> {
                // Only index item.* and block.* keys that correspond to registered items
                if (!key.startsWith("item.") && !key.startsWith("block.")) return;
                ITEM_LANG_NAMES.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                        .add(value.toLowerCase(Locale.ROOT));
            });
        } catch (Exception ignored) {}
    }

    private static final String EN_LAYOUT = "qwertyuiop[]asdfghjkl;'zxcvbnm,.QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>`~";
    private static final String RU_LAYOUT = "йцукенгшщзхъфывапролджэячсмитьбюЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮёЁ";
    private static final Map<Character, Character> KEYBOARD_FLIP_MAP = new HashMap<>(140);

    static {
        for (int i = 0; i < Math.min(EN_LAYOUT.length(), RU_LAYOUT.length()); i++) {
            char en = EN_LAYOUT.charAt(i);
            char ru = RU_LAYOUT.charAt(i);
            KEYBOARD_FLIP_MAP.put(en, ru);
            KEYBOARD_FLIP_MAP.put(ru, en);
        }
    }

    public static String flipKeyboardLayout(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Character flipped = KEYBOARD_FLIP_MAP.get(c);
            sb.append(flipped != null ? flipped : c);
        }
        return sb.toString();
    }

    private static final Map<Item, String> ITEM_NAME_LOWER_CACHE = new ConcurrentHashMap<>();
    private static final Map<Item, String> ITEM_ID_LOWER_CACHE = new ConcurrentHashMap<>();

    public static String getItemNameLower(Item item) {
        if (item == null) return "";
        return ITEM_NAME_LOWER_CACHE.computeIfAbsent(item, it -> it.getName(new ItemStack(it)).getString().toLowerCase(Locale.ROOT));
    }

    public static String getItemIdLower(Item item) {
        if (item == null) return "";
        return ITEM_ID_LOWER_CACHE.computeIfAbsent(item, it -> {
            Identifier id = BuiltInRegistries.ITEM.getKey(it);
            return id != null ? id.toString().toLowerCase(Locale.ROOT) : "";
        });
    }

    public static void clearItemNameCache() {
        ITEM_NAME_LOWER_CACHE.clear();
        ITEM_ID_LOWER_CACHE.clear();
    }

    public static boolean matchesSearch(Item item, String filter) {
        if (item == null || filter == null || filter.isEmpty()) return true;
        String query = filter.toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) return true;

        // 1. Direct match in client language item name
        String name = getItemNameLower(item);
        if (name.contains(query)) return true;

        // 2. Direct match in registry ID (e.g. "minecraft:stick")
        String id = getItemIdLower(item);
        if (id.contains(query)) return true;

        // 3. Multilingual search (e.g. searching "stick" on RU client, or "палка" on EN client)
        String transKey = item.getDescriptionId();
        Set<String> langNames = ITEM_LANG_NAMES.get(transKey);
        if (langNames != null) {
            for (String langName : langNames) {
                if (langName.contains(query)) return true;
            }
        }

        // 4. Flipped keyboard layout search (e.g. typed "njntv" instead of "тотем", or "ghjrev" instead of "проку")
        String flipped = flipKeyboardLayout(query);
        if (!flipped.isEmpty() && !flipped.equals(query)) {
            if (name.contains(flipped) || id.contains(flipped)) return true;
            if (langNames != null) {
                for (String langName : langNames) {
                    if (langName.contains(flipped)) return true;
                }
            }
        }

        return false;
    }

    public static boolean matchesMultilingual(Item item, String query, String flippedQuery, boolean hasFlipped) {
        if (item == null) return false;
        String transKey = item.getDescriptionId();
        Set<String> langNames = ITEM_LANG_NAMES.get(transKey);
        if (langNames != null) {
            for (String langName : langNames) {
                if (langName.contains(query)) return true;
                if (hasFlipped && langName.contains(flippedQuery)) return true;
            }
        }
        return false;
    }

    public static void indexCurrentLanguage() {
        Language lang = Language.getInstance();
        if (lang != null) {
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                String key = item.getDescriptionId();
                if (lang.has(key)) {
                    String val = lang.getOrDefault(key);
                    if (val != null && !val.equals(key)) {
                        ITEM_LANG_NAMES.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                                .add(val.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
    }

    @Environment(EnvType.CLIENT)
    private static class ClientLangHelper {
        static boolean initFromClient(Level world) {
            Minecraft client = Minecraft.getInstance();
            if (client == null) return false;

            MinecraftServer server = client.getSingleplayerServer();
            if (server != null && server.getRecipeManager() != null) {
                for (RecipeHolder<?> entry : server.getRecipeManager().getRecipes()) {
                    indexRecipeEntry(entry);
                }
                return true;
            }

            if (client.player != null) {
                net.minecraft.client.ClientRecipeBook recipeBook = client.player.getRecipeBook();
                if (recipeBook != null) {
                    for (RecipeCollection collection : recipeBook.getCollections()) {
                        for (RecipeDisplayEntry entry : collection.getRecipes()) {
                            if (entry.id().toString().contains("recipeeditor")) {
                                continue;
                            }
                            RecipeDisplay display = entry.display();
                            Set<Item> resultItems = getAllItemsFromSlotDisplay(display.result());
                            for (Item resultItem : resultItems) {
                                if (resultItem != Items.AIR) {
                                    RECIPE_DISPLAYS.computeIfAbsent(resultItem, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(display);
                                    KNOWN_RECIPE_ITEMS.add(resultItem);
                                    indexDisplayIngredients(display, resultItem);
                                }
                            }
                        }
                    }
                }
                return true;
            }
            return false;
        }

        static void indexDiskAssetLanguages() {
            try {
                Minecraft client = Minecraft.getInstance();
                List<Path> candidateDirs = new ArrayList<>();
                if (client != null && client.gameDirectory != null) {
                    Path run = client.gameDirectory.toPath().toAbsolutePath();
                    candidateDirs.add(run.resolve("assets"));
                    if (run.getParent() != null) {
                        candidateDirs.add(run.getParent().resolve("assets"));
                        if (run.getParent().getParent() != null) {
                            candidateDirs.add(run.getParent().getParent().resolve("assets"));
                        }
                    }
                }
                String appData = System.getenv("APPDATA");
                if (appData != null) {
                    candidateDirs.add(Path.of(appData, ".minecraft", "assets"));
                }
                String userHome = System.getProperty("user.home");
                if (userHome != null) {
                    candidateDirs.add(Path.of(userHome, ".minecraft", "assets"));
                }

                for (Path assetDir : candidateDirs) {
                    Path indexesDir = assetDir.resolve("indexes");
                    Path objectsDir = assetDir.resolve("objects");
                    if (Files.isDirectory(indexesDir) && Files.isDirectory(objectsDir)) {
                        try (Stream<Path> stream = Files.list(indexesDir)) {
                            List<Path> indexFiles = stream.filter(p -> p.toString().endsWith(".json"))
                                    .sorted((a, b) -> Long.compare(b.toFile().length(), a.toFile().length()))
                                    .toList();
                            for (Path indexFile : indexFiles) {
                                if (parseAssetIndexForLanguages(indexFile, objectsDir)) {
                                    return;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        static void indexResourceManagerLanguages() {
            Minecraft client = Minecraft.getInstance();
            if (client == null) return;
            ResourceManager rm = client.getResourceManager();
            if (rm == null) return;
            try {
                ClientLanguage storage = ClientLanguage.loadFrom(rm, List.of("ru_ru"), false);
                for (Item item : BuiltInRegistries.ITEM) {
                    if (item == Items.AIR) continue;
                    String key = item.getDescriptionId();
                    String name = storage.getOrDefault(key, null);
                    if (name != null && !name.equals(key)) {
                        ITEM_LANG_NAMES.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                                .add(name.toLowerCase(Locale.ROOT));
                    }
                }
            } catch (Throwable ignored) {}
        }

        private static boolean parseAssetIndexForLanguages(Path indexFile, Path objectsDir) {
            try (BufferedReader reader = Files.newBufferedReader(indexFile)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject objects = root.has("objects") && root.get("objects").isJsonObject()
                        ? root.getAsJsonObject("objects") : null;
                if (objects == null) return false;

                String[] targetLangs = {"ru_ru", "uk_ua", "be_by"};
                boolean loadedAny = false;
                for (String lang : targetLangs) {
                    String targetKey = "minecraft/lang/" + lang + ".json";
                    if (objects.has(targetKey) && objects.get(targetKey).isJsonObject()) {
                        JsonObject entry = objects.getAsJsonObject(targetKey);
                        if (entry.has("hash") && entry.get("hash").isJsonPrimitive()) {
                            String hash = entry.get("hash").getAsString();
                            if (hash != null && hash.length() >= 2) {
                                Path objPath = objectsDir.resolve(hash.substring(0, 2)).resolve(hash);
                                if (Files.isRegularFile(objPath)) {
                                    try (InputStream is = Files.newInputStream(objPath)) {
                                        Language.loadFromJson(is, (key, value) -> {
                                            if (!key.startsWith("item.") && !key.startsWith("block.")) return;
                                            ITEM_LANG_NAMES.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                                                    .add(value.toLowerCase(Locale.ROOT));
                                        });
                                        loadedAny = true;
                                    }
                                }
                            }
                        }
                    }
                }
                return loadedAny;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    private static void parseModRecipeJson(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);

            if (obj == null) return;

            Item resultItem = Items.AIR;
            int count = 1;
            String recipeIdStr = null;

            String s = path.toString().replace('\\', '/');
            int idx = s.indexOf("data/");
            if (idx != -1) {
                String sub = s.substring(idx + 5);
                int slash = sub.indexOf('/');
                if (slash != -1) {
                    String namespace = sub.substring(0, slash);
                    String rest = sub.substring(slash + 1);
                    if (rest.startsWith("recipes/")) rest = rest.substring(8);
                    else if (rest.startsWith("recipe/")) rest = rest.substring(7);
                    if (rest.endsWith(".json")) rest = rest.substring(0, rest.length() - 5);
                    recipeIdStr = namespace + ":" + rest;
                }
            }

            if (obj.has("result")) {
                JsonElement resElem = obj.get("result");
                if (resElem.isJsonPrimitive()) {
                    Identifier id = Identifier.tryParse(resElem.getAsString());
                    if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                        resultItem = BuiltInRegistries.ITEM.getValue(id);
                    }
                } else if (resElem.isJsonObject()) {
                    JsonObject resObj = resElem.getAsJsonObject();
                    if (resObj.has("id")) {
                        Identifier id = Identifier.tryParse(resObj.get("id").getAsString());
                        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                            resultItem = BuiltInRegistries.ITEM.getValue(id);
                        }
                    } else if (resObj.has("item")) {
                        Identifier id = Identifier.tryParse(resObj.get("item").getAsString());
                        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                            resultItem = BuiltInRegistries.ITEM.getValue(id);
                        }
                    }
                    if (resObj.has("count")) {
                        count = resObj.get("count").getAsInt();
                    }
                }
            }

            if (resultItem != Items.AIR) {
                KNOWN_RECIPE_ITEMS.add(resultItem);
                CustomRecipeData decompiled = buildDecompiledRecipeFromJson(obj, resultItem, count, recipeIdStr);
                if (decompiled != null) {
                    DECOMPILED_VARIANTS.computeIfAbsent(resultItem, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(decompiled);
                    indexDecompiledIngredients(decompiled, resultItem);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void indexDecompiledIngredients(CustomRecipeData data, Item resultItem) {
        if (data == null || resultItem == Items.AIR || data.patternSlots == null) return;
        for (int i = 0; i < 9; i++) {
            String s = data.getSlotString(i);
            if (s == null || s.isEmpty() || s.equals("minecraft:air")) continue;
            if (s.startsWith("#")) {
                List<Item> tagItems = TagResolver.getAllItemsForTag(s);
                for (Item it : tagItems) {
                    if (it != Items.AIR) {
                        INGREDIENT_TO_ITEMS.computeIfAbsent(it, k -> ConcurrentHashMap.newKeySet()).add(resultItem);
                    }
                }
            } else {
                Item it = data.getItemAt(i);
                if (it != Items.AIR) {
                    INGREDIENT_TO_ITEMS.computeIfAbsent(it, k -> ConcurrentHashMap.newKeySet()).add(resultItem);
                }
            }
        }
    }

    private static CustomRecipeData buildDecompiledRecipeFromJson(JsonObject obj, Item resultItem, int count, String recipeIdStr) {
        Identifier resId = BuiltInRegistries.ITEM.getKey(resultItem);
        CustomRecipeData data = new CustomRecipeData(
                recipeIdStr != null ? recipeIdStr : (resId != null ? resId.getPath() : "recipe"),
                resId != null ? resId.toString() : "minecraft:air",
                count > 0 ? count : 1,
                RecipeTypeEnum.SHAPED_CRAFTING
        );
        data.overriddenId = recipeIdStr;

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
        } else if (typeStr.contains("blasting")) {
            data.type = RecipeTypeEnum.BLASTING;
            if (obj.has("cookingtime")) data.cookingTime = obj.get("cookingtime").getAsInt();
            if (obj.has("experience")) data.experience = obj.get("experience").getAsFloat();
            if (obj.has("ingredient")) {
                data.setSlotString(0, parseIngredientElement(obj.get("ingredient")));
            }
        } else if (typeStr.contains("smoking")) {
            data.type = RecipeTypeEnum.SMOKING;
            if (obj.has("cookingtime")) data.cookingTime = obj.get("cookingtime").getAsInt();
            if (obj.has("experience")) data.experience = obj.get("experience").getAsFloat();
            if (obj.has("ingredient")) {
                data.setSlotString(0, parseIngredientElement(obj.get("ingredient")));
            }
        } else if (typeStr.contains("campfire")) {
            data.type = RecipeTypeEnum.CAMPFIRE_COOKING;
            if (obj.has("cookingtime")) data.cookingTime = obj.get("cookingtime").getAsInt();
            if (obj.has("experience")) data.experience = obj.get("experience").getAsFloat();
            if (obj.has("ingredient")) {
                data.setSlotString(0, parseIngredientElement(obj.get("ingredient")));
            }
        } else if (typeStr.contains("smelt") || obj.has("cookingtime")) {
            data.type = RecipeTypeEnum.SMELTING;
            if (obj.has("cookingtime")) data.cookingTime = obj.get("cookingtime").getAsInt();
            if (obj.has("experience")) data.experience = obj.get("experience").getAsFloat();
            if (obj.has("ingredient")) {
                data.setSlotString(0, parseIngredientElement(obj.get("ingredient")));
            }
        } else if (typeStr.contains("stonecutting")) {
            data.type = RecipeTypeEnum.STONECUTTING;
            if (obj.has("ingredient")) {
                data.setSlotString(0, parseIngredientElement(obj.get("ingredient")));
            }
        } else if (obj.has("ingredients")) {
            data.type = RecipeTypeEnum.SHAPED_CRAFTING;
            JsonArray ings = obj.getAsJsonArray("ingredients");
            for (int i = 0; i < Math.min(9, ings.size()); i++) {
                data.setSlotString(i, parseIngredientElement(ings.get(i)));
            }
        }
        data.originalKey = data.getKey();
        data.overriddenKey = data.getKey();
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

    private static void indexRecipeEntry(RecipeHolder<?> entry) {
        if (entry == null || entry.id() == null) return;
        if (entry.id().identifier().getNamespace().equals("recipeeditor")) return;
        Recipe<?> recipe = entry.value();
        if (recipe != null && recipe.getClass().getName().startsWith("com.recipeeditor.")) return;
        try {
            List<RecipeDisplay> displays = recipe.display();
            for (RecipeDisplay display : displays) {
                Set<Item> resultItems = getAllItemsFromSlotDisplay(display.result());
                for (Item resultItem : resultItems) {
                    if (resultItem != Items.AIR) {
                        RECIPE_ENTRIES.computeIfAbsent(resultItem, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(entry);
                        RECIPE_DISPLAYS.computeIfAbsent(resultItem, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(display);
                        KNOWN_RECIPE_ITEMS.add(resultItem);
                        indexDisplayIngredients(display, resultItem);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static void indexDisplayIngredients(RecipeDisplay display, Item resultItem) {
        if (display == null || resultItem == Items.AIR) return;
        List<SlotDisplay> ingredients = null;
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            ingredients = shaped.ingredients();
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            ingredients = shapeless.ingredients();
        } else if (display instanceof FurnaceRecipeDisplay furnace) {
            SlotDisplay ing = furnace.ingredient();
            if (ing != null) {
                for (Item it : getAllItemsFromSlotDisplay(ing)) {
                    if (it != Items.AIR) INGREDIENT_TO_ITEMS.computeIfAbsent(it, k -> ConcurrentHashMap.newKeySet()).add(resultItem);
                }
            }
        } else if (display instanceof StonecutterRecipeDisplay stonecutter) {
            SlotDisplay ing = stonecutter.input();
            if (ing != null) {
                for (Item it : getAllItemsFromSlotDisplay(ing)) {
                    if (it != Items.AIR) INGREDIENT_TO_ITEMS.computeIfAbsent(it, k -> ConcurrentHashMap.newKeySet()).add(resultItem);
                }
            }
        } else if (display instanceof SmithingRecipeDisplay smithing) {
            SlotDisplay[] smithingIngs = new SlotDisplay[]{smithing.template(), smithing.base(), smithing.addition()};
            for (SlotDisplay sd : smithingIngs) {
                if (sd != null) {
                    for (Item it : getAllItemsFromSlotDisplay(sd)) {
                        if (it != Items.AIR) INGREDIENT_TO_ITEMS.computeIfAbsent(it, k -> ConcurrentHashMap.newKeySet()).add(resultItem);
                    }
                }
            }
        }
        if (ingredients != null) {
            for (SlotDisplay sd : ingredients) {
                for (Item it : getAllItemsFromSlotDisplay(sd)) {
                    if (it != Items.AIR) INGREDIENT_TO_ITEMS.computeIfAbsent(it, k -> ConcurrentHashMap.newKeySet()).add(resultItem);
                }
            }
        }
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
        } else if (display instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay) {
            items.add(stackDisplay.stack().item().value());
        } else if (display instanceof SlotDisplay.WithRemainder withRemainder) {
            collectItemsFromSlotDisplay(withRemainder.input(), items);
        } else if (display instanceof SlotDisplay.TagSlotDisplay tagDisplay) {
            for (var entry : tagDisplay.tag()) {
                items.add(entry.value());
            }
        } else if (display instanceof SlotDisplay.Composite composite) {
            for (SlotDisplay child : composite.contents()) {
                collectItemsFromSlotDisplay(child, items);
            }
        }
    }

    public static RecipeStatus getStatus(Item item, Level world, RecipeEditorConfig config) {
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
        boolean hasEntry = KNOWN_RECIPE_ITEMS.contains(item) || SYNTHETIC_ITEMS.contains(item);
        if (hasEntry) {
            return RecipeStatus.VANILLA_OR_MODDED;
        }
        return RecipeStatus.UNCRAFTABLE;
    }

    public static boolean hasExistingRecipe(Item item, Level world) {
        if (item == null || item == Items.AIR) return false;
        if (!cacheInitialized) {
            initializeCache(world);
        }
        return KNOWN_RECIPE_ITEMS.contains(item) || SYNTHETIC_ITEMS.contains(item);
    }

    public static List<CustomRecipeData> getAllRecipeVariants(Item targetItem, Level world) {
        if (targetItem == null || targetItem == Items.AIR) return Collections.emptyList();
        if (!cacheInitialized) {
            initializeCache(world);
        }

        List<CustomRecipeData> cached = CACHED_ITEM_VARIANTS.get(targetItem);
        if (cached != null) {
            return cached;
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
        List<RecipeHolder<?>> entries = RECIPE_ENTRIES.get(targetItem);
        if (entries != null) {
            for (RecipeHolder<?> entry : entries) {
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

        List<CustomRecipeData> unmodifiable = Collections.unmodifiableList(variants);
        CACHED_ITEM_VARIANTS.put(targetItem, unmodifiable);
        return unmodifiable;
    }

    public static List<CustomRecipeData> getAllRecipeVariantsOfType(Item targetItem, RecipeTypeEnum type, Level world) {
        if (targetItem == null || targetItem == Items.AIR || type == null) return Collections.emptyList();
        List<CustomRecipeData> all = getAllRecipeVariants(targetItem, world);
        if (all.isEmpty()) return Collections.emptyList();
        List<CustomRecipeData> filtered = new ArrayList<>();
        for (CustomRecipeData d : all) {
            if (d.type == type) {
                filtered.add(d);
            }
        }
        return filtered;
    }

    public static boolean isCookingInputOverridden(RecipeTypeEnum typeEnum, Item inputItem, Level world) {
        if (inputItem == null || inputItem == Items.AIR || typeEnum == null) return false;
        if (!cacheInitialized) {
            initializeCache(world);
        }
        Set<Item> candidates = INGREDIENT_TO_ITEMS.get(inputItem);
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        boolean hasAnyRecipe = false;
        boolean allOverridden = true;

        for (Item resultItem : candidates) {
            List<CustomRecipeData> variants = getAllRecipeVariantsOfType(resultItem, typeEnum, world);
            for (CustomRecipeData v : variants) {
                if (v.type != typeEnum) continue;
                String slotStr = v.getSlotString(0);
                boolean matches = false;
                if (slotStr != null && slotStr.startsWith("#")) {
                    List<Item> tagItems = TagResolver.getAllItemsForTag(slotStr);
                    matches = tagItems.contains(inputItem);
                } else if (v.getItemAt(0) == inputItem) {
                    matches = true;
                }
                if (matches) {
                    hasAnyRecipe = true;
                    String id = v.overriddenId != null ? v.overriddenId : v.id;
                    if (!CustomRecipeDispatcher.isIdOverridden(id)) {
                        allOverridden = false;
                        break;
                    }
                }
            }
            if (!allOverridden) break;
        }

        return hasAnyRecipe && allOverridden;
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
                        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
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
                    Identifier id = BuiltInRegistries.ITEM.getKey(res);
                    r.setSlotString(i, id != null ? id.toString() : "minecraft:air");
                }
            }
        }

        return currentLevel;
    }

    public static List<CustomRecipeData> getSyntheticDynamicRecipes(Item targetItem) {
        if (targetItem == null || targetItem == Items.AIR) return Collections.emptyList();
        Identifier id = BuiltInRegistries.ITEM.getKey(targetItem);
        if (id == null) return Collections.emptyList();

        String path = id.getPath();
        List<CustomRecipeData> list = new ArrayList<>();

        // 1. Netherite Smithing Upgrades (Helmet, Chestplate, Leggings, Boots, Sword, Shovel, Pickaxe, Axe, Hoe)
        if (path.startsWith("netherite_") && id.getNamespace().equals("minecraft")) {
            String baseEquip = path.replace("netherite_", "diamond_");
            Identifier baseId = Identifier.fromNamespaceAndPath("minecraft", baseEquip);
            if (BuiltInRegistries.ITEM.containsKey(baseId)) {
                CustomRecipeData smithing = new CustomRecipeData(path, id.toString(), 1, RecipeTypeEnum.SMITHING);
                smithing.setSlotString(0, "minecraft:netherite_upgrade_smithing_template");
                smithing.setSlotString(1, "minecraft:" + baseEquip);
                smithing.setSlotString(2, "minecraft:netherite_ingot");
                list.add(smithing);
                return finishSyntheticList(list);
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
                    return finishSyntheticList(list);
                }
            }
        }

        return finishSyntheticList(list);
    }

    private static List<CustomRecipeData> finishSyntheticList(List<CustomRecipeData> list) {
        if (list != null) {
            for (CustomRecipeData syn : list) {
                if (syn.originalKey == null) syn.originalKey = syn.getKey();
                if (syn.overriddenKey == null) syn.overriddenKey = syn.getKey();
            }
        }
        return list;
    }

    private static String getRecipeSignature(CustomRecipeData d) {
        if (d == null) return "";
        return d.getKey();
    }

    public static CustomRecipeData decompileRecipe(Item targetItem, Level world) {
        List<CustomRecipeData> variants = getAllRecipeVariants(targetItem, world);
        if (!variants.isEmpty()) {
            return variants.get(0).copy();
        }
        return null;
    }

    public static CustomRecipeData decompileFromEntry(RecipeHolder<?> entry, Item targetItem) {
        if (entry == null || targetItem == null || entry.id() == null) return null;
        if (entry.id().identifier().getNamespace().equals("recipeeditor")) return null;
        if (entry.value() != null && entry.value().getClass().getName().startsWith("com.recipeeditor.")) return null;
        Identifier resId = BuiltInRegistries.ITEM.getKey(targetItem);
        String recipeIdStr = entry.id().identifier().toString();
        CustomRecipeData data = new CustomRecipeData(
                recipeIdStr,
                resId != null ? resId.toString() : "minecraft:air",
                1,
                RecipeTypeEnum.SHAPED_CRAFTING
        );
        data.overriddenId = recipeIdStr;

        Recipe<?> recipe = entry.value();
        CustomRecipeData result = null;
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
            result = data;
        } else if (recipe instanceof ShapelessRecipe) {
            data.type = RecipeTypeEnum.SHAPED_CRAFTING;
            data.isShapeless = true;
            List<RecipeDisplay> displays = recipe.display();
            if (!displays.isEmpty() && displays.get(0) instanceof ShapelessCraftingRecipeDisplay disp) {
                List<SlotDisplay> ings = disp.ingredients();
                for (int i = 0; i < Math.min(9, ings.size()); i++) {
                    data.setSlotString(i, getSlotStringFromSlotDisplay(ings.get(i)));
                }
            }
            result = data;
        } else if (recipe instanceof SmithingTransformRecipe smithing) {
            data.type = RecipeTypeEnum.SMITHING;
            smithing.templateIngredient().ifPresent(ing -> data.setSlotString(0, getSlotStringFromIngredient(ing)));
            Ingredient baseIng = smithing.baseIngredient();
            if (baseIng != null && !baseIng.isEmpty()) data.setSlotString(1, getSlotStringFromIngredient(baseIng));
            smithing.additionIngredient().ifPresent(ing -> data.setSlotString(2, getSlotStringFromIngredient(ing)));
            result = data;
        } else if (recipe instanceof BlastingRecipe blasting) {
            data.type = RecipeTypeEnum.BLASTING;
            data.setSlotString(0, getSlotStringFromIngredient(blasting.input()));
            result = data;
        } else if (recipe instanceof SmokingRecipe smoking) {
            data.type = RecipeTypeEnum.SMOKING;
            data.setSlotString(0, getSlotStringFromIngredient(smoking.input()));
            result = data;
        } else if (recipe instanceof CampfireCookingRecipe campfire) {
            data.type = RecipeTypeEnum.CAMPFIRE_COOKING;
            data.setSlotString(0, getSlotStringFromIngredient(campfire.input()));
            result = data;
        } else if (recipe instanceof SmeltingRecipe smelting) {
            data.type = RecipeTypeEnum.SMELTING;
            data.setSlotString(0, getSlotStringFromIngredient(smelting.input()));
            result = data;
        } else if (recipe instanceof StonecutterRecipe stonecutting) {
            data.type = RecipeTypeEnum.STONECUTTING;
            data.setSlotString(0, getSlotStringFromIngredient(stonecutting.input()));
            result = data;
        } else if (recipe instanceof SingleItemRecipe singleStack) {
            RecipeTypeEnum mappedType = CustomRecipeDispatcher.getEnumForType(singleStack.getType());
            data.type = mappedType != null ? mappedType : RecipeTypeEnum.STONECUTTING;
            data.setSlotString(0, getSlotStringFromIngredient(singleStack.input()));
            result = data;
        }

        if (result != null) {
            result.originalKey = result.getKey();
            result.overriddenKey = result.getKey();
        }
        return result;
    }

    public static CustomRecipeData decompileFromDisplay(RecipeDisplay display, Item targetItem) {
        if (display == null || targetItem == null) return null;
        Identifier resId = BuiltInRegistries.ITEM.getKey(targetItem);
        CustomRecipeData data = new CustomRecipeData(
                resId != null ? resId.getPath() : "recipe",
                resId != null ? resId.toString() : "minecraft:air",
                1,
                RecipeTypeEnum.SHAPED_CRAFTING
        );

        CustomRecipeData result = null;
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
            result = data;
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            data.type = RecipeTypeEnum.SHAPED_CRAFTING;
            data.isShapeless = true;
            List<SlotDisplay> ings = shapeless.ingredients();
            for (int i = 0; i < Math.min(9, ings.size()); i++) {
                data.setSlotString(i, getSlotStringFromSlotDisplay(ings.get(i)));
            }
            result = data;
        } else if (display instanceof FurnaceRecipeDisplay furnace) {
            data.type = RecipeTypeEnum.SMELTING;
            data.experience = furnace.experience();
            data.cookingTime = furnace.duration();
            data.setSlotString(0, getSlotStringFromSlotDisplay(furnace.ingredient()));
            result = data;
        } else if (display instanceof StonecutterRecipeDisplay stonecutter) {
            data.type = RecipeTypeEnum.STONECUTTING;
            data.setSlotString(0, getSlotStringFromSlotDisplay(stonecutter.input()));
            result = data;
        } else if (display instanceof SmithingRecipeDisplay smithing) {
            data.type = RecipeTypeEnum.SMITHING;
            data.setSlotString(0, getSlotStringFromSlotDisplay(smithing.template()));
            data.setSlotString(1, getSlotStringFromSlotDisplay(smithing.base()));
            data.setSlotString(2, getSlotStringFromSlotDisplay(smithing.addition()));
            result = data;
        }

        if (result != null) {
            result.originalKey = result.getKey();
            result.overriddenKey = result.getKey();
        }
        return result;
    }

    public static String getSlotStringFromIngredient(Ingredient ing) {
        if (ing == null) return "minecraft:air";
        SlotDisplay display = ing.display();
        return getSlotStringFromSlotDisplay(display);
    }

    public static Item getItemFromSlotDisplay(SlotDisplay display) {
        if (display == null) return Items.AIR;
        if (display instanceof SlotDisplay.ItemSlotDisplay itemDisplay) {
            return itemDisplay.item().value();
        } else if (display instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay) {
            return stackDisplay.stack().item().value();
        } else if (display instanceof SlotDisplay.WithRemainder withRemainder) {
            return getItemFromSlotDisplay(withRemainder.input());
        } else if (display instanceof SlotDisplay.TagSlotDisplay tagDisplay) {
            return tagDisplay.tag().unwrapKey()
                    .map(k -> TagResolver.resolveTag(k.location().toString()))
                    .orElseGet(() -> {
                        if (tagDisplay.tag().size() > 0) {
                            return tagDisplay.tag().get(0).value();
                        }
                        return Items.AIR;
                    });
        } else if (display instanceof SlotDisplay.Composite composite) {
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
            Identifier id = BuiltInRegistries.ITEM.getKey(itemDisplay.item().value());
            return id != null ? id.toString() : "minecraft:air";
        } else if (display instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay) {
            Identifier id = BuiltInRegistries.ITEM.getKey(stackDisplay.stack().item().value());
            return id != null ? id.toString() : "minecraft:air";
        } else if (display instanceof SlotDisplay.WithRemainder withRemainder) {
            return getSlotStringFromSlotDisplay(withRemainder.input());
        } else if (display instanceof SlotDisplay.TagSlotDisplay tagDisplay) {
            return tagDisplay.tag().unwrapKey()
                    .map(k -> "#" + k.location().toString())
                    .orElseGet(() -> {
                        if (tagDisplay.tag().size() > 0) {
                            Identifier id = BuiltInRegistries.ITEM.getKey(tagDisplay.tag().get(0).value());
                            return id != null ? id.toString() : "minecraft:air";
                        }
                        return "minecraft:air";
                    });
        } else if (display instanceof SlotDisplay.Composite composite) {
            for (SlotDisplay child : composite.contents()) {
                String str = getSlotStringFromSlotDisplay(child);
                if (!str.equals("minecraft:air")) return str;
            }
        }
        return "minecraft:air";
    }

    public static List<RecipeConflictInfo> findConflicts(CustomRecipeData candidate, Item currentTargetItem, Level world, RecipeEditorConfig config) {
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
                            String source = Component.translatable("recipeeditor.gui.conflict_source_custom").getString();
                            conflicts.add(new RecipeConflictInfo(candidate, savedItem, source, candidate.type, saved.overriddenId, saved.getKey()));
                        }
                    }
                }
            }
        }

        // 2. Check scanned Vanilla and Mod recipes (only for items known to have recipes!)
        if (!cacheInitialized) {
            initializeCache(world);
        }

        Set<Item> candidateIngredients = new HashSet<>();
        if (candidate.patternSlots != null) {
            for (int i = 0; i < 9; i++) {
                String s = candidate.getSlotString(i);
                if (s != null && s.startsWith("#")) {
                    List<Item> tagItems = TagResolver.getAllItemsForTag(s);
                    candidateIngredients.addAll(tagItems);
                } else {
                    Item it = candidate.getItemAt(i);
                    if (it != null && it != Items.AIR) {
                        candidateIngredients.add(it);
                    }
                }
            }
        }

        Set<Item> itemsToCheck = new HashSet<>();
        if (!INGREDIENT_TO_ITEMS.isEmpty() && !candidateIngredients.isEmpty()) {
            for (Item ing : candidateIngredients) {
                Set<Item> resItems = INGREDIENT_TO_ITEMS.get(ing);
                if (resItems != null) {
                    itemsToCheck.addAll(resItems);
                }
            }
        }
        if (itemsToCheck.isEmpty() && INGREDIENT_TO_ITEMS.isEmpty()) {
            itemsToCheck.addAll(KNOWN_RECIPE_ITEMS);
            itemsToCheck.addAll(RECIPE_ENTRIES.keySet());
            itemsToCheck.addAll(RECIPE_DISPLAYS.keySet());
            itemsToCheck.addAll(DECOMPILED_VARIANTS.keySet());
        }

        for (Item item : itemsToCheck) {
            if (item == Items.AIR || item == currentTargetItem || seenConflictingItems.contains(item)) continue;

            List<CustomRecipeData> variants = getAllRecipeVariantsOfType(item, candidate.type, world);
            for (CustomRecipeData v : variants) {
                if (v.type == candidate.type && isPatternMatching(v, candidate)) {
                    if (seenConflictingItems.add(item)) {
                        Identifier id = BuiltInRegistries.ITEM.getKey(item);
                        String source;
                        if (id != null && id.getNamespace().equals("minecraft")) {
                            source = Component.translatable("recipeeditor.gui.conflict_source_vanilla").getString();
                        } else {
                            String modName = getFriendlyModName(id != null ? id.getNamespace() : "");
                            source = Component.translatable("recipeeditor.gui.conflict_source_mod", modName).getString();
                        }
                        String recId = (v.overriddenId != null && !v.overriddenId.isEmpty()) ? v.overriddenId : v.id;
                        conflicts.add(new RecipeConflictInfo(candidate, item, source, candidate.type, recId, v.getKey()));
                        break;
                    }
                }
            }
            if (conflicts.size() >= 10) break;
        }

        return conflicts;
    }

    public static boolean isPatternMatching(CustomRecipeData a, CustomRecipeData b) {
        if (a == null || b == null || a.type != b.type) return false;

        if (a.type == RecipeTypeEnum.SMELTING || a.type == RecipeTypeEnum.BLASTING ||
            a.type == RecipeTypeEnum.SMOKING || a.type == RecipeTypeEnum.STONECUTTING ||
            a.type == RecipeTypeEnum.CAMPFIRE_COOKING) {
            return matchesSlot(a, b, 0, 0);
        }

        if (a.type == RecipeTypeEnum.SMITHING) {
            for (int i = 0; i < 3; i++) {
                if (!matchesSlot(a, b, i, i)) return false;
            }
            return true;
        }

        // Shapeless Crafting: match multiset of non-empty slots
        if (a.isShapeless || b.isShapeless) {
            List<Integer> slotsA = new ArrayList<>();
            List<Integer> slotsB = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                String sA = a.getSlotString(i);
                if (sA != null && !sA.isEmpty() && !sA.equals("minecraft:air")) slotsA.add(i);
                String sB = b.getSlotString(i);
                if (sB != null && !sB.isEmpty() && !sB.equals("minecraft:air")) slotsB.add(i);
            }
            if (slotsA.size() != slotsB.size()) return false;
            boolean[] used = new boolean[slotsB.size()];
            return matchShapelessSlots(a, b, slotsA, slotsB, 0, used);
        }

        // Shaped Crafting: compute normalized bounding boxes for translation/mirror invariance
        int minRowA = 3, maxRowA = -1, minColA = 3, maxColA = -1;
        int minRowB = 3, maxRowB = -1, minColB = 3, maxColB = -1;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int slot = r * 3 + c;
                String sA = a.getSlotString(slot);
                if (sA != null && !sA.isEmpty() && !sA.equals("minecraft:air")) {
                    if (r < minRowA) minRowA = r;
                    if (r > maxRowA) maxRowA = r;
                    if (c < minColA) minColA = c;
                    if (c > maxColA) maxColA = c;
                }
                String sB = b.getSlotString(slot);
                if (sB != null && !sB.isEmpty() && !sB.equals("minecraft:air")) {
                    if (r < minRowB) minRowB = r;
                    if (r > maxRowB) maxRowB = r;
                    if (c < minColB) minColB = c;
                    if (c > maxColB) maxColB = c;
                }
            }
        }

        if (maxRowA == -1 || maxRowB == -1) return false;

        int wA = maxColA - minColA + 1;
        int hA = maxRowA - minRowA + 1;
        int wB = maxColB - minColB + 1;
        int hB = maxRowB - minRowB + 1;

        if (wA != wB || hA != hB) return false;

        // 1. Direct normalized match
        boolean directMatch = true;
        for (int r = 0; r < hA; r++) {
            for (int c = 0; c < wA; c++) {
                int slotA = (minRowA + r) * 3 + (minColA + c);
                int slotB = (minRowB + r) * 3 + (minColB + c);
                if (!matchesSlot(a, b, slotA, slotB)) {
                    directMatch = false;
                    break;
                }
            }
            if (!directMatch) break;
        }
        if (directMatch) return true;

        // 2. Horizontal mirrored normalized match
        boolean mirrorMatch = true;
        for (int r = 0; r < hA; r++) {
            for (int c = 0; c < wA; c++) {
                int slotA = (minRowA + r) * 3 + (minColA + c);
                int slotB = (minRowB + r) * 3 + (maxColB - c);
                if (!matchesSlot(a, b, slotA, slotB)) {
                    mirrorMatch = false;
                    break;
                }
            }
            if (!mirrorMatch) break;
        }
        return mirrorMatch;
    }

    private static boolean matchShapelessSlots(CustomRecipeData a, CustomRecipeData b,
                                               List<Integer> slotsA, List<Integer> slotsB,
                                               int idxA, boolean[] usedB) {
        if (idxA >= slotsA.size()) return true;
        int slotA = slotsA.get(idxA);
        for (int i = 0; i < slotsB.size(); i++) {
            if (!usedB[i] && matchesSlot(a, b, slotA, slotsB.get(i))) {
                usedB[i] = true;
                if (matchShapelessSlots(a, b, slotsA, slotsB, idxA + 1, usedB)) return true;
                usedB[i] = false;
            }
        }
        return false;
    }

    private static boolean matchesSlot(CustomRecipeData a, CustomRecipeData b, int slotA, int slotB) {
        String strA = a.getSlotString(slotA);
        String strB = b.getSlotString(slotB);

        boolean aEmpty = strA.isEmpty() || strA.equals("minecraft:air");
        boolean bEmpty = strB.isEmpty() || strB.equals("minecraft:air");
        if (aEmpty && bEmpty) return true;
        if (aEmpty != bEmpty) return false;

        if (strA.equals(strB)) return true;

        Item itemA = a.getItemAt(slotA);
        Item itemB = b.getItemAt(slotB);
        if (itemA != Items.AIR && itemA == itemB) return true;

        // If one or both is a tag, check if the other item is part of the tag
        if (strA.startsWith("#") && itemB != Items.AIR) {
            List<Item> tagItems = TagResolver.getAllItemsForTag(strA);
            if (tagItems.contains(itemB)) return true;
        }
        if (strB.startsWith("#") && itemA != Items.AIR) {
            List<Item> tagItems = TagResolver.getAllItemsForTag(strB);
            if (tagItems.contains(itemA)) return true;
        }
        if (strA.startsWith("#") && strB.startsWith("#")) {
            List<Item> itemsA = TagResolver.getAllItemsForTag(strA);
            List<Item> itemsB = TagResolver.getAllItemsForTag(strB);
            for (Item itm : itemsA) {
                if (itemsB.contains(itm)) return true;
            }
        }

        return false;
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
