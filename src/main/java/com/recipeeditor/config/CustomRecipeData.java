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
    public String overriddenId = null;
    public String overriddenKey = null;
    public boolean isShapeless = false;
    public Map<String, Integer> typeCounts = new HashMap<>();
    public transient String originalKey = null;

    private transient RawShapedRecipe cachedRawRecipe = null;
    private transient int cachedHash = 0;
    private transient Ingredient[] cachedIngredients = null;
    private transient int cachedPatternHash = 0;

    public String getKey() {
        String res = resultItemId != null ? resultItemId : "minecraft:air";
        String t = type != null ? type.name() : "SHAPED_CRAFTING";
        return res + "#" + t + (isShapeless ? "#SL#" : "#") + getPatternSignature();
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

    public synchronized void invalidateCache() {
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

    public synchronized Ingredient getIngredientAt(int slot) {
        if (slot < 0 || slot >= 9) return null;
        int currentHash = Arrays.hashCode(patternSlots);
        if (cachedIngredients == null || cachedPatternHash != currentHash) {
            Ingredient[] temp = new Ingredient[9];
            for (int i = 0; i < 9; i++) {
                temp[i] = computeIngredientForSlot(i);
            }
            cachedIngredients = temp;
            cachedPatternHash = currentHash;
        } else if (cachedIngredients[slot] == null || (cachedIngredients[slot].isEmpty() && getSlotString(slot).startsWith("#"))) {
            // If previously resolved to empty for a tag before registry was ready, retry now
            String slotStr = getSlotString(slot);
            if (slotStr != null && slotStr.startsWith("#")) {
                Ingredient retry = computeIngredientForSlot(slot);
                if (retry != null && !retry.isEmpty()) {
                    cachedIngredients[slot] = retry;
                }
            }
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
            // Tag is missing/unresolved: return an unmatchable empty ingredient instead of null (air)
            return Ingredient.ofItems();
        }
        Item item = getItemAt(slot);
        if (item != Items.AIR) {
            return Ingredient.ofItem(item);
        }
        // Unknown item identifier: return an unmatchable empty ingredient instead of null (air)
        return Ingredient.ofItems();
    }

    public Optional<Ingredient> createIngredientForSlot(int slot) {
        Ingredient ing = getIngredientAt(slot);
        return ing != null ? Optional.of(ing) : Optional.empty();
    }

    public RawShapedRecipe getRawRecipe() {
        int hash = Arrays.hashCode(patternSlots);
        if (cachedRawRecipe == null || cachedHash != hash) {
            int minRow = 3, maxRow = -1, minCol = 3, maxCol = -1;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int slot = r * 3 + c;
                    String s = patternSlots != null ? patternSlots[slot] : null;
                    if (s != null && !s.isEmpty() && !s.equals("minecraft:air")) {
                        if (r < minRow) minRow = r;
                        if (r > maxRow) maxRow = r;
                        if (c < minCol) minCol = c;
                        if (c > maxCol) maxCol = c;
                    }
                }
            }

            if (maxRow == -1) {
                // Completely empty
                cachedRawRecipe = new RawShapedRecipe(1, 1, List.of(Optional.empty()), Optional.empty());
            } else {
                int patternW = maxCol - minCol + 1;
                int patternH = maxRow - minRow + 1;
                List<Optional<Ingredient>> ingredients = new ArrayList<>(patternW * patternH);
                for (int r = minRow; r <= maxRow; r++) {
                    for (int c = minCol; c <= maxCol; c++) {
                        ingredients.add(createIngredientForSlot(r * 3 + c));
                    }
                }
                cachedRawRecipe = new RawShapedRecipe(patternW, patternH, ingredients, Optional.empty());
            }
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
        copy.overriddenId = this.overriddenId;
        copy.overriddenKey = this.overriddenKey;
        copy.isShapeless = this.isShapeless;
        if (this.typeCounts != null) {
            copy.typeCounts = new HashMap<>(this.typeCounts);
        }
        copy.originalKey = this.originalKey;
        return copy;
    }

    public List<Ingredient> getNonEmptyIngredients() {
        List<Ingredient> list = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            Ingredient ing = getIngredientAt(i);
            if (ing != null) {
                list.add(ing);
            }
        }
        return list;
    }

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    public String toJson() {
        return GSON.toJson(this);
    }

    public static CustomRecipeData fromJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return GSON.fromJson(json, CustomRecipeData.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomRecipeData that = (CustomRecipeData) o;
        return enabled == that.enabled &&
                resultCount == that.resultCount &&
                isShapeless == that.isShapeless &&
                overrideExisting == that.overrideExisting &&
                Float.compare(that.experience, experience) == 0 &&
                cookingTime == that.cookingTime &&
                Objects.equals(id, that.id) &&
                Objects.equals(resultItemId, that.resultItemId) &&
                Objects.equals(overriddenId, that.overriddenId) &&
                Objects.equals(overriddenKey, that.overriddenKey) &&
                type == that.type &&
                Arrays.equals(patternSlots, that.patternSlots) &&
                Objects.equals(typeCounts, that.typeCounts);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, resultItemId, resultCount, type, experience, cookingTime, enabled, overrideExisting, overriddenId, overriddenKey, isShapeless, typeCounts);
        result = 31 * result + Arrays.hashCode(patternSlots);
        return result;
    }
}
