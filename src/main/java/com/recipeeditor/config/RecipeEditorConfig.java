package com.recipeeditor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.recipeeditor.RecipeEditorMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RecipeEditorConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile RecipeEditorConfig INSTANCE;

    public volatile boolean modEnabled = true;
    public volatile int configVersion = 0;
    public Map<String, CustomRecipeData> recipes = new ConcurrentHashMap<>();
    private transient final Set<String> enabledResultIds = ConcurrentHashMap.newKeySet();
    private transient volatile List<CustomRecipeData> sortedCraftingRecipes = null;
    private transient volatile int sortedCraftingRecipesVersion = -1;

    public static RecipeEditorConfig getInstance() {
        RecipeEditorConfig instance = INSTANCE;
        if (instance == null) {
            synchronized (RecipeEditorConfig.class) {
                instance = INSTANCE;
                if (instance == null) {
                    INSTANCE = instance = load();
                }
            }
        }
        return instance;
    }

    public static synchronized void reloadLocal() {
        INSTANCE = load();
    }

    public static Path getConfigPath() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path newPath = configDir.resolve("mrrecipeeditor.json");
        Path oldPath = configDir.resolve("recipeeditor.json");
        if (!java.nio.file.Files.exists(newPath) && java.nio.file.Files.exists(oldPath)) {
            try {
                java.nio.file.Files.copy(oldPath, newPath);
            } catch (Exception ignored) {}
        }
        return newPath;
    }

    public static RecipeEditorConfig load() {
        Path configPath = getConfigPath();
        File configFile = configPath.toFile();
        if (configFile.exists()) {
            try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(configPath, java.nio.charset.StandardCharsets.UTF_8)) {
                RecipeEditorConfig config = GSON.fromJson(reader, RecipeEditorConfig.class);
                if (config != null) {
                    config.validate();
                    INSTANCE = config;
                    com.recipeeditor.recipe.CustomRecipeDispatcher.invalidateOverriddenCache();
                    return config;
                }
            } catch (Exception e) {
                RecipeEditorMod.LOGGER.error("Failed to load RecipeEditor config, backing up corrupted file", e);
                try {
                    Path backupPath = configPath.resolveSibling("recipeeditor.json.bak");
                    java.nio.file.Files.copy(configPath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
        }

        RecipeEditorConfig config = new RecipeEditorConfig();
        config.initDefaults();
        config.save();
        INSTANCE = config;
        return config;
    }

    public void initDefaults() {
        modEnabled = true;
        recipes.clear(); // Clean slate by default - no pre-added recipes
        rebuildEnabledCache();
    }

    public void rebuildEnabledCache() {
        enabledResultIds.clear();
        sortedCraftingRecipes = null;
        sortedCraftingRecipesVersion = -1;
        if (recipes != null) {
            for (CustomRecipeData r : recipes.values()) {
                if (r.enabled && r.resultItemId != null && !r.resultItemId.isEmpty()) {
                    enabledResultIds.add(r.resultItemId);
                }
            }
        }
    }

    public void validate() {
        if (recipes == null) {
            recipes = new ConcurrentHashMap<>();
        }
        Map<String, CustomRecipeData> rekeyed = new ConcurrentHashMap<>();
        for (CustomRecipeData recipe : recipes.values()) {
            if (recipe.patternSlots == null || recipe.patternSlots.length != 9) {
                recipe.patternSlots = new String[9];
                Arrays.fill(recipe.patternSlots, "minecraft:air");
            }
            if (recipe.resultCount < 1) recipe.resultCount = 1;
            if (recipe.resultCount > 1000) recipe.resultCount = 1000;
            if (recipe.type == null) recipe.type = RecipeTypeEnum.SHAPED_CRAFTING;
            if (recipe.typeCounts == null) {
                recipe.typeCounts = new HashMap<>();
            }
            rekeyed.put(recipe.getKey(), recipe);
        }
        this.recipes = rekeyed;
        rebuildEnabledCache();
    }

    public synchronized void save() {
        try {
            Path configPath = getConfigPath();
            Path tmpPath = configPath.resolveSibling("recipeeditor.json.tmp");
            File parent = configPath.toFile().getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(tmpPath, java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }

            try {
                java.nio.file.Files.move(tmpPath, configPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(tmpPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            configVersion++;
            rebuildEnabledCache();
            com.recipeeditor.recipe.CustomDynamicCraftingRecipe.invalidateDisplayCache();
            com.recipeeditor.recipe.CustomRecipeDispatcher.clearSyntheticCache();
            com.recipeeditor.recipe.CustomRecipeDispatcher.invalidateOverriddenCache();
        } catch (IOException e) {
            RecipeEditorMod.LOGGER.error("Failed to save RecipeEditor config atomically", e);
        }
    }

    public void invalidateAllRecipeCaches() {
        configVersion++;
        if (recipes != null) {
            for (CustomRecipeData r : recipes.values()) {
                r.invalidateCache();
            }
        }
        rebuildEnabledCache();
        com.recipeeditor.recipe.CustomDynamicCraftingRecipe.invalidateDisplayCache();
        com.recipeeditor.recipe.CustomRecipeDispatcher.clearSyntheticCache();
        com.recipeeditor.recipe.CustomRecipeDispatcher.invalidateOverriddenCache();
    }

    public boolean hasAnyCustomRecipes() {
        return recipes != null && !recipes.isEmpty();
    }

    public boolean hasCustomRecipe(Item item) {
        if (!modEnabled || item == null || item == Items.AIR) return false;
        Identifier id = Registries.ITEM.getId(item);
        return id != null && hasCustomRecipe(id.toString());
    }

    public boolean hasCustomRecipe(String itemId) {
        if (!modEnabled || itemId == null) return false;
        return enabledResultIds.contains(itemId);
    }

    public List<CustomRecipeData> getRecipesFor(Item item) {
        if (item == null || item == Items.AIR) return Collections.emptyList();
        Identifier id = Registries.ITEM.getId(item);
        if (id == null) return Collections.emptyList();
        String targetId = id.toString();

        List<CustomRecipeData> result = new ArrayList<>();
        if (recipes != null) {
            for (CustomRecipeData r : recipes.values()) {
                if (targetId.equals(r.resultItemId)) {
                    result.add(r.copy());
                }
            }
        }
        return result;
    }

    public List<CustomRecipeData> getRecipesFor(Item item, RecipeTypeEnum type) {
        List<CustomRecipeData> all = getRecipesFor(item);
        if (type == null) return all;
        List<CustomRecipeData> filtered = new ArrayList<>();
        for (CustomRecipeData r : all) {
            if (r.type == type) {
                filtered.add(r);
            }
        }
        return filtered;
    }

    public CustomRecipeData getRecipeFor(Item item) {
        List<CustomRecipeData> list = getRecipesFor(item);
        return !list.isEmpty() ? list.get(0) : null;
    }

    public List<CustomRecipeData> getSortedCraftingRecipes() {
        if (!modEnabled || recipes == null || recipes.isEmpty()) {
            return Collections.emptyList();
        }
        int curVer = configVersion;
        List<CustomRecipeData> cached = sortedCraftingRecipes;
        if (cached != null && sortedCraftingRecipesVersion == curVer) {
            return cached;
        }
        List<CustomRecipeData> list = new ArrayList<>();
        for (CustomRecipeData r : recipes.values()) {
            if (r.enabled && r.type == RecipeTypeEnum.SHAPED_CRAFTING) {
                list.add(r);
            }
        }
        list.sort((a, b) -> {
            // 1. Shaped recipes before Shapeless recipes
            if (a.isShapeless != b.isShapeless) {
                return a.isShapeless ? 1 : -1;
            }
            // 2. For shapeless recipes: larger non-empty ingredient count first (more specific)
            if (a.isShapeless) {
                int countA = a.getNonEmptyIngredients().size();
                int countB = b.getNonEmptyIngredients().size();
                if (countA != countB) {
                    return Integer.compare(countB, countA);
                }
            }
            // 3. Deterministic key comparison
            return a.getKey().compareTo(b.getKey());
        });
        List<CustomRecipeData> unmodifiable = Collections.unmodifiableList(list);
        sortedCraftingRecipes = unmodifiable;
        sortedCraftingRecipesVersion = curVer;
        return unmodifiable;
    }

    public void addOrUpdateRecipe(CustomRecipeData recipe) {
        if (recipe != null && recipe.resultItemId != null && !recipe.resultItemId.isEmpty() && !recipe.resultItemId.equals("minecraft:air")) {
            if (recipes == null) {
                recipes = new ConcurrentHashMap<>();
            }
            recipes.put(recipe.getKey(), recipe);
            configVersion++;
            if (recipe.enabled) {
                enabledResultIds.add(recipe.resultItemId);
            } else {
                rebuildEnabledCache();
            }
            com.recipeeditor.recipe.CustomRecipeDispatcher.invalidateOverriddenCache();
        }
    }

    public void removeRecipe(CustomRecipeData recipe) {
        if (recipes != null && recipe != null) {
            recipes.remove(recipe.getKey());
            configVersion++;
            rebuildEnabledCache();
            com.recipeeditor.recipe.CustomRecipeDispatcher.invalidateOverriddenCache();
        }
    }

    public void removeRecipeByKey(String key) {
        if (recipes != null && key != null) {
            recipes.remove(key);
            configVersion++;
            rebuildEnabledCache();
            com.recipeeditor.recipe.CustomRecipeDispatcher.invalidateOverriddenCache();
        }
    }

    public void removeRecipesFor(Item item) {
        if (item == null || item == Items.AIR || recipes == null) return;
        Identifier id = Registries.ITEM.getId(item);
        if (id == null) return;
        String targetId = id.toString();
        recipes.values().removeIf(r -> targetId.equals(r.resultItemId));
        configVersion++;
        rebuildEnabledCache();
        com.recipeeditor.recipe.CustomRecipeDispatcher.invalidateOverriddenCache();
    }

    public RecipeEditorConfig copy() {
        RecipeEditorConfig copy = new RecipeEditorConfig();
        copy.modEnabled = this.modEnabled;
        copy.configVersion = this.configVersion;
        if (this.recipes != null) {
            for (Map.Entry<String, CustomRecipeData> entry : this.recipes.entrySet()) {
                copy.recipes.put(entry.getKey(), entry.getValue().copy());
            }
        }
        copy.rebuildEnabledCache();
        return copy;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static RecipeEditorConfig fromJson(String json) {
        if (json == null || json.isEmpty() || !json.trim().startsWith("{")) return null;
        try {
            RecipeEditorConfig config = GSON.fromJson(json, RecipeEditorConfig.class);
            if (config != null) {
                config.validate();
            }
            return config;
        } catch (Exception e) {
            RecipeEditorMod.LOGGER.error("Failed to parse RecipeEditorConfig from JSON", e);
            return null;
        }
    }
}
