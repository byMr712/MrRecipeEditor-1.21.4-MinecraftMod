package com.recipeeditor;

import com.mojang.serialization.MapCodec;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.recipe.CustomDynamicCraftingRecipe;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RecipeEditorMod implements ModInitializer {
    public static final String MOD_ID = "recipeeditor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final RecipeSerializer<CustomDynamicCraftingRecipe> RECIPE_SERIALIZER = Registry.register(
            Registries.RECIPE_SERIALIZER,
            Identifier.of(MOD_ID, "custom_crafting"),
            new RecipeSerializer<CustomDynamicCraftingRecipe>() {
                private final MapCodec<CustomDynamicCraftingRecipe> CODEC = MapCodec.unit(() -> new CustomDynamicCraftingRecipe(CraftingRecipeCategory.MISC));
                private final PacketCodec<RegistryByteBuf, CustomDynamicCraftingRecipe> PACKET_CODEC = PacketCodec.unit(new CustomDynamicCraftingRecipe(CraftingRecipeCategory.MISC));

                @Override
                public MapCodec<CustomDynamicCraftingRecipe> codec() {
                    return CODEC;
                }

                @Override
                public PacketCodec<RegistryByteBuf, CustomDynamicCraftingRecipe> packetCodec() {
                    return PACKET_CODEC;
                }
            }
    );

    @Override
    public void onInitialize() {
        RecipeEditorConfig.getInstance();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                RegistryKey<Recipe<?>> key = RegistryKey.of(RegistryKeys.RECIPE, Identifier.of(MOD_ID, "custom_crafting"));
                handler.getPlayer().unlockRecipes(List.of(key));
            } catch (Exception e) {
                LOGGER.error("Failed to unlock RecipeEditor recipes for player", e);
            }
        });

        LOGGER.info("Recipe Editor initialized! Dynamic multi-recipe engine active.");
    }
}
