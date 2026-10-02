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
        for (CustomRecipeData recipe : recipes.values()) {
            if (recipe.patternSlots == null || recipe.patternSlots.length != 9) {
                recipe.patternSlots = new String[9];
                Arrays.fill(recipe.patternSlots, "minecraft:air");
            }
            if (recipe.resultCount < 1) recipe.resultCount = 1;
            if (recipe.resultCount > 1000) recipe.resultCount = 1000;
            if (recipe.type == null) recipe.type = RecipeTypeEnum.SHAPED_CRAFTING;
        }
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
        return recipes != null && recipes.containsKey(itemId);
    }

    public CustomRecipeData getRecipeFor(Item item) {
        if (item == null || item == Items.AIR) return null;
        Identifier id = Registries.ITEM.getId(item);
        return id != null ? getRecipeFor(id.toString()) : null;
    }

    public CustomRecipeData getRecipeFor(String itemId) {
        return recipes != null ? recipes.get(itemId) : null;
    }

    public void addOrUpdateRecipe(CustomRecipeData recipe) {
        if (recipe != null && recipe.resultItemId != null && !recipe.resultItemId.isEmpty() && !recipe.resultItemId.equals("minecraft:air")) {
            if (recipes == null) {
                recipes = new LinkedHashMap<>();
            }
            recipes.put(recipe.resultItemId, recipe);
        }
    }

    public void removeRecipe(String itemId) {
        if (recipes != null) {
            recipes.remove(itemId);
        }
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
