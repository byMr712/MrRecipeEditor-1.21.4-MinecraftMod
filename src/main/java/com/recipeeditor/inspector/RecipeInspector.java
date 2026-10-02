package com.recipeeditor.inspector;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
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

import java.util.*;

public class RecipeInspector {
    private static final Map<Item, List<RecipeEntry<?>>> RECIPE_ENTRIES = new HashMap<>();
    private static final Map<Item, List<RecipeDisplay>> RECIPE_DISPLAYS = new HashMap<>();
    private static boolean cacheInitialized = false;

    public static void invalidateCache() {
        RECIPE_ENTRIES.clear();
        RECIPE_DISPLAYS.clear();
        cacheInitialized = false;
    }

    public static void initializeCache(World world) {
        RECIPE_ENTRIES.clear();
        RECIPE_DISPLAYS.clear();
        MinecraftClient client = MinecraftClient.getInstance();

        if (client != null) {
            MinecraftServer server = client.getServer();
            if (server != null && server.getRecipeManager() != null) {
                for (RecipeEntry<?> entry : server.getRecipeManager().values()) {
                    Recipe<?> recipe = entry.value();
                    try {
                        List<RecipeDisplay> displays = recipe.getDisplays();
                        for (RecipeDisplay display : displays) {
                            Item resultItem = getItemFromSlotDisplay(display.result());
                            if (resultItem != Items.AIR) {
                                RECIPE_ENTRIES.computeIfAbsent(resultItem, k -> new ArrayList<>()).add(entry);
                                RECIPE_DISPLAYS.computeIfAbsent(resultItem, k -> new ArrayList<>()).add(display);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                cacheInitialized = true;
                return;
            }

            // Fallback for remote server connection via ClientRecipeBook
            if (client.player != null) {
                ClientRecipeBook recipeBook = client.player.getRecipeBook();
                if (recipeBook != null) {
                    for (RecipeResultCollection collection : recipeBook.getOrderedResults()) {
                        for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                            RecipeDisplay display = entry.display();
                            Item resultItem = getItemFromSlotDisplay(display.result());
                            if (resultItem != Items.AIR) {
                                RECIPE_DISPLAYS.computeIfAbsent(resultItem, k -> new ArrayList<>()).add(display);
                            }
                        }
                    }
                }
            }
        }
        cacheInitialized = true;
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
        boolean hasEntry = RECIPE_ENTRIES.containsKey(item) || RECIPE_DISPLAYS.containsKey(item);
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
        return RECIPE_ENTRIES.containsKey(item) || RECIPE_DISPLAYS.containsKey(item);
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
