package com.recipeeditor.config;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.*;

public class CustomRecipeData {
    public String id = "";
    public String resultItemId = "minecraft:totem_of_undying";
    public int resultCount = 1;
    public RecipeTypeEnum type = RecipeTypeEnum.SHAPED_CRAFTING;
    public String[] patternSlots = new String[9];
    public float experience = 0.1f;
    public int cookingTime = 200;
    public boolean enabled = true;
    public boolean overrideExisting = false;

    private transient RawShapedRecipe cachedRawRecipe = null;
    private transient int cachedHash = 0;

    public CustomRecipeData() {
        Arrays.fill(patternSlots, "minecraft:air");
    }

    public CustomRecipeData(String id, String resultItemId, int resultCount, RecipeTypeEnum type) {
        this.id = id;
        this.resultItemId = resultItemId;
        this.resultCount = Math.max(1, Math.min(64, resultCount));
        this.type = type != null ? type : RecipeTypeEnum.SHAPED_CRAFTING;
        this.patternSlots = new String[9];
        Arrays.fill(this.patternSlots, "minecraft:air");
    }

    public void invalidateCache() {
        cachedRawRecipe = null;
        cachedHash = 0;
    }

    public Item getResultItem() {
        if (resultItemId == null || resultItemId.isEmpty()) {
            return Items.AIR;
        }
        Identifier id = Identifier.tryParse(resultItemId);
        if (id == null || !Registries.ITEM.containsId(id)) {
            return Items.AIR;
        }
        return Registries.ITEM.get(id);
    }

    public void setResultItem(Item item) {
        if (item == null || item == Items.AIR) {
            this.resultItemId = "minecraft:air";
        } else {
            Identifier id = Registries.ITEM.getId(item);
            this.resultItemId = id != null ? id.toString() : "minecraft:air";
        }
    }

    public Item getItemAt(int slot) {
        if (slot < 0 || slot >= 9 || patternSlots == null || slot >= patternSlots.length) {
            return Items.AIR;
        }
        String idStr = patternSlots[slot];
        if (idStr == null || idStr.isEmpty() || idStr.equals("minecraft:air") || idStr.startsWith("#")) {
            return Items.AIR;
        }
        Identifier id = Identifier.tryParse(idStr);
        if (id == null || !Registries.ITEM.containsId(id)) {
            return Items.AIR;
        }
        return Registries.ITEM.get(id);
    }

    public String getSlotString(int slot) {
        if (slot < 0 || slot >= 9 || patternSlots == null || slot >= patternSlots.length) {
            return "minecraft:air";
        }
        return patternSlots[slot] != null ? patternSlots[slot] : "minecraft:air";
    }

    public void setItemAt(int slot, Item item) {
        if (slot >= 0 && slot < 9) {
            if (patternSlots == null || patternSlots.length != 9) {
                patternSlots = new String[9];
                Arrays.fill(patternSlots, "minecraft:air");
            }
            if (item == null || item == Items.AIR) {
                patternSlots[slot] = "minecraft:air";
            } else {
                Identifier id = Registries.ITEM.getId(item);
                patternSlots[slot] = id != null ? id.toString() : "minecraft:air";
            }
            invalidateCache();
        }
    }

    public void setSlotString(int slot, String val) {
        if (slot >= 0 && slot < 9) {
            if (patternSlots == null || patternSlots.length != 9) {
                patternSlots = new String[9];
                Arrays.fill(patternSlots, "minecraft:air");
            }
            patternSlots[slot] = val != null ? val : "minecraft:air";
            invalidateCache();
        }
    }

    public Optional<Ingredient> createIngredientForSlot(int slot) {
        String slotStr = getSlotString(slot);
        if (slotStr.equals("minecraft:air") || slotStr.isEmpty()) {
            return Optional.empty();
        }
        if (slotStr.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(slotStr.substring(1));
            if (tagId != null) {
                TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, tagId);
                return Optional.of(Ingredient.fromTag(Registries.ITEM.getOrThrow(tagKey)));
            }
        }
        Item item = getItemAt(slot);
        if (item != Items.AIR) {
            return Optional.of(Ingredient.ofItem(item));
        }
        return Optional.empty();
    }

    public RawShapedRecipe getRawRecipe() {
        int hash = Arrays.hashCode(patternSlots);
        if (cachedRawRecipe == null || cachedHash != hash) {
            List<Optional<Ingredient>> ingredients = new ArrayList<>(9);
            for (int i = 0; i < 9; i++) {
                ingredients.add(createIngredientForSlot(i));
            }
            cachedRawRecipe = new RawShapedRecipe(3, 3, ingredients, Optional.empty());
            cachedHash = hash;
        }
        return cachedRawRecipe;
    }

    public List<Ingredient> getShapelessIngredients() {
        List<Ingredient> list = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            Optional<Ingredient> ing = createIngredientForSlot(i);
            ing.ifPresent(list::add);
        }
        return list;
    }

    public CustomRecipeData copy() {
        CustomRecipeData copy = new CustomRecipeData();
        copy.id = this.id;
        copy.resultItemId = this.resultItemId;
        copy.resultCount = this.resultCount;
        copy.type = this.type;
        copy.patternSlots = patternSlots != null ? Arrays.copyOf(this.patternSlots, 9) : new String[9];
        copy.experience = this.experience;
        copy.cookingTime = this.cookingTime;
        copy.enabled = this.enabled;
        copy.overrideExisting = this.overrideExisting;
        return copy;
    }
}
