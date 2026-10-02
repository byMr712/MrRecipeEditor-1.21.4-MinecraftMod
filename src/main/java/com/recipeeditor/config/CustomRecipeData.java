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
    public Map<String, Integer> typeCounts = new HashMap<>();

    private transient RawShapedRecipe cachedRawRecipe = null;
    private transient int cachedHash = 0;
    private transient Ingredient[] cachedIngredients = null;
    private transient int cachedPatternHash = 0;

    public String getKey() {
        String res = resultItemId != null ? resultItemId : "minecraft:air";
        String t = type != null ? type.name() : "SHAPED_CRAFTING";
        return res + "#" + t + "#" + getPatternSignature();
    }

    public String getPatternSignature() {
        if (patternSlots == null) return "empty";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            if (i > 0) sb.append(',');
            sb.append(patternSlots[i] != null ? patternSlots[i] : "minecraft:air");
        }
        return sb.toString();
    }

    public int getResultCountForType(RecipeTypeEnum t) {
        if (t == null) t = this.type != null ? this.type : RecipeTypeEnum.SHAPED_CRAFTING;
        if (typeCounts != null && typeCounts.containsKey(t.name())) {
            return typeCounts.get(t.name());
        }
        return this.resultCount > 0 ? this.resultCount : 1;
    }

    public void setResultCountForType(RecipeTypeEnum t, int count) {
        if (t == null) t = this.type != null ? this.type : RecipeTypeEnum.SHAPED_CRAFTING;
        int clamped = Math.max(1, Math.min(1000, count));
        if (typeCounts == null) {
            typeCounts = new HashMap<>();
        }
        typeCounts.put(t.name(), clamped);
        if (this.type == t) {
            this.resultCount = clamped;
        }
    }

    public CustomRecipeData() {
        Arrays.fill(patternSlots, "minecraft:air");
    }

    public CustomRecipeData(String id, String resultItemId, int resultCount, RecipeTypeEnum type) {
        this.id = id;
        this.resultItemId = resultItemId;
        this.resultCount = Math.max(1, Math.min(1000, resultCount));
        this.type = type != null ? type : RecipeTypeEnum.SHAPED_CRAFTING;
        this.patternSlots = new String[9];
        Arrays.fill(this.patternSlots, "minecraft:air");
    }

    public void invalidateCache() {
        cachedRawRecipe = null;
        cachedHash = 0;
        cachedIngredients = null;
        cachedPatternHash = 0;
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
        if (idStr == null || idStr.isEmpty() || idStr.equals("minecraft:air")) {
            return Items.AIR;
        }
        if (idStr.startsWith("#")) {
            return com.recipeeditor.inspector.TagResolver.resolveTag(idStr);
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

    public Ingredient getIngredientAt(int slot) {
        if (slot < 0 || slot >= 9) return null;
        int currentHash = Arrays.hashCode(patternSlots);
        if (cachedIngredients == null || cachedPatternHash != currentHash) {
            cachedIngredients = new Ingredient[9];
            for (int i = 0; i < 9; i++) {
                cachedIngredients[i] = computeIngredientForSlot(i);
            }
            cachedPatternHash = currentHash;
        }
        return cachedIngredients[slot];
    }

    public Ingredient computeIngredientForSlot(int slot) {
        String slotStr = getSlotString(slot);
        if (slotStr.equals("minecraft:air") || slotStr.isEmpty()) {
            return null;
        }
        if (slotStr.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(slotStr.substring(1));
            if (tagId != null) {
                TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, tagId);
                var entryList = Registries.ITEM.getOptional(tagKey);
                if (entryList.isPresent() && entryList.get().size() > 0) {
                    return Ingredient.fromTag(entryList.get());
                }
            }
        }
        Item item = getItemAt(slot);
        if (item != Items.AIR) {
            return Ingredient.ofItem(item);
        }
        return null;
    }

    public Optional<Ingredient> createIngredientForSlot(int slot) {
        Ingredient ing = getIngredientAt(slot);
        return ing != null ? Optional.of(ing) : Optional.empty();
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
        if (this.typeCounts != null) {
            copy.typeCounts = new HashMap<>(this.typeCounts);
        }
        return copy;
    }
}
