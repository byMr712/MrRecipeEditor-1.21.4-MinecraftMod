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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class RecipeEditorConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static RecipeEditorConfig INSTANCE;

    public Map<String, CustomRecipeData> recipes = new LinkedHashMap<>();

    public static RecipeEditorConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("recipeeditor.json");
    }

    public static RecipeEditorConfig load() {
        File configFile = getConfigPath().toFile();
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                RecipeEditorConfig config = GSON.fromJson(reader, RecipeEditorConfig.class);
                if (config != null) {
                    config.validate();
                    INSTANCE = config;
                    return config;
                }
            } catch (Exception e) {
                RecipeEditorMod.LOGGER.error("Failed to load RecipeEditor config, using clean state", e);
            }
        }

        RecipeEditorConfig config = new RecipeEditorConfig();
        config.initDefaults();
        config.save();
        INSTANCE = config;
        return config;
    }

    public void initDefaults() {
        recipes.clear(); // Clean slate by default - no pre-added recipes
    }

    public void validate() {
        if (recipes == null) {
            recipes = new LinkedHashMap<>();
        }
        Map<String, CustomRecipeData> rekeyed = new LinkedHashMap<>();
        for (CustomRecipeData recipe : recipes.values()) {
            if (recipe.patternSlots == null || recipe.patternSlots.length != 9) {
                recipe.patternSlots = new String[9];
                Arrays.fill(recipe.patternSlots, "minecraft:air");
            }
            if (recipe.resultCount < 1) recipe.resultCount = 1;
            if (recipe.resultCount > 1000) recipe.resultCount = 1000;
            if (recipe.type == null) recipe.type = RecipeTypeEnum.SHAPED_CRAFTING;
            rekeyed.put(recipe.getKey(), recipe);
        }
        this.recipes = rekeyed;
    }

    public void save() {
        try {
            File configFile = getConfigPath().toFile();
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            RecipeEditorMod.LOGGER.error("Failed to save RecipeEditor config", e);
        }
    }

    public boolean hasCustomRecipe(Item item) {
        if (item == null || item == Items.AIR) return false;
        Identifier id = Registries.ITEM.getId(item);
        return id != null && hasCustomRecipe(id.toString());
    }

    public boolean hasCustomRecipe(String itemId) {
        if (recipes == null || itemId == null) return false;
        for (CustomRecipeData r : recipes.values()) {
            if (r.enabled && itemId.equals(r.resultItemId)) {
                return true;
            }
        }
        return false;
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

    public void addOrUpdateRecipe(CustomRecipeData recipe) {
        if (recipe != null && recipe.resultItemId != null && !recipe.resultItemId.isEmpty() && !recipe.resultItemId.equals("minecraft:air")) {
            if (recipes == null) {
                recipes = new LinkedHashMap<>();
            }
            recipes.put(recipe.getKey(), recipe);
        }
    }

    public void removeRecipe(CustomRecipeData recipe) {
        if (recipes != null && recipe != null) {
            recipes.remove(recipe.getKey());
        }
    }

    public void removeRecipesFor(Item item) {
        if (item == null || item == Items.AIR || recipes == null) return;
        Identifier id = Registries.ITEM.getId(item);
        if (id == null) return;
        String targetId = id.toString();
        recipes.values().removeIf(r -> targetId.equals(r.resultItemId));
    }

    public RecipeEditorConfig copy() {
        RecipeEditorConfig copy = new RecipeEditorConfig();
        if (this.recipes != null) {
            for (Map.Entry<String, CustomRecipeData> entry : this.recipes.entrySet()) {
                copy.recipes.put(entry.getKey(), entry.getValue().copy());
            }
        }
        return copy;
    }
}
