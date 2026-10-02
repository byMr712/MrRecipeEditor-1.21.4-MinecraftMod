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
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.display.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.*;

public class RecipeInspector {
    private static final Map<Item, List<RecipeDisplay>> RECIPE_CACHE = new HashMap<>();
    private static boolean cacheInitialized = false;

    public static void invalidateCache() {
        RECIPE_CACHE.clear();
        cacheInitialized = false;
    }

    public static void initializeCache(World world) {
        RECIPE_CACHE.clear();
        MinecraftClient client = MinecraftClient.getInstance();

        // 1. Scan IntegratedServer / DedicatedServer if available (singleplayer world / host)
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
                                RECIPE_CACHE.computeIfAbsent(resultItem, k -> new ArrayList<>()).add(display);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                cacheInitialized = true;
                return;
            }

            // 2. Scan ClientRecipeBook if connected to remote server
            if (client.player != null) {
                ClientRecipeBook recipeBook = client.player.getRecipeBook();
                if (recipeBook != null) {
                    for (RecipeResultCollection collection : recipeBook.getOrderedResults()) {
                        for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                            RecipeDisplay display = entry.display();
                            Item resultItem = getItemFromSlotDisplay(display.result());
                            if (resultItem != Items.AIR) {
                                RECIPE_CACHE.computeIfAbsent(resultItem, k -> new ArrayList<>()).add(display);
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
        List<RecipeDisplay> entries = RECIPE_CACHE.get(item);
        if (entries != null && !entries.isEmpty()) {
            return RecipeStatus.VANILLA_OR_MODDED;
        }
        return RecipeStatus.UNCRAFTABLE;
    }

    public static boolean hasExistingRecipe(Item item, World world) {
        if (item == null || item == Items.AIR) return false;
        if (!cacheInitialized) {
            initializeCache(world);
        }
        List<RecipeDisplay> entries = RECIPE_CACHE.get(item);
        return entries != null && !entries.isEmpty();
    }

    public static CustomRecipeData decompileRecipe(Item targetItem, World world) {
        if (targetItem == null || targetItem == Items.AIR) return null;
        if (!cacheInitialized) {
            initializeCache(world);
        }
        List<RecipeDisplay> displays = RECIPE_CACHE.get(targetItem);
        if (displays == null || displays.isEmpty()) {
            return null;
        }

        RecipeDisplay display = displays.get(0);
        Identifier targetId = Registries.ITEM.getId(targetItem);
        CustomRecipeData data = new CustomRecipeData(
                targetId != null ? targetId.getPath() : "recipe",
                targetId != null ? targetId.toString() : "minecraft:air",
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

    public static Item getItemFromSlotDisplay(SlotDisplay display) {
        if (display == null) return Items.AIR;
        if (display instanceof SlotDisplay.ItemSlotDisplay itemDisplay) {
            return itemDisplay.item().value();
        } else if (display instanceof SlotDisplay.StackSlotDisplay stackDisplay) {
            return stackDisplay.stack().getItem();
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
