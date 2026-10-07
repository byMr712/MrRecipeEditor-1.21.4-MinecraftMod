package com.recipeeditor;

import com.mojang.serialization.MapCodec;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.recipe.CustomDynamicCraftingRecipe;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecipeEditorMod implements ModInitializer {
    public static final String MOD_ID = "recipeeditor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static boolean isDedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }

    public static final RecipeSerializer<CustomDynamicCraftingRecipe> CUSTOM_CRAFTING_SERIALIZER = Registry.register(
            BuiltInRegistries.RECIPE_SERIALIZER,
            Identifier.fromNamespaceAndPath(MOD_ID, "custom_crafting"),
            new RecipeSerializer<>(
                    MapCodec.unit(() -> new CustomDynamicCraftingRecipe(CraftingBookCategory.MISC)),
                    StreamCodec.unit(new CustomDynamicCraftingRecipe(CraftingBookCategory.MISC))
            )
    );

    @Override
    public void onInitialize() {
        if (isDedicatedServer()) {
            System.out.println(" ==========[MR Recipe Editor]============");
            System.out.println("Notice: MR Recipe Editor has detected that it is running on a server; unfortunately, this is not supported.");
            System.out.println("The mod will not work, but this will not affect your server's startup — enjoy the game!");
            System.out.println(" ==========[MR Recipe Editor]============");
            LOGGER.info("==========[MR Recipe Editor]============");
            LOGGER.info("Notice: MR Recipe Editor has detected that it is running on a server; unfortunately, this is not supported.");
            LOGGER.info("The mod will not work, but this will not affect your server's startup — enjoy the game!");
            LOGGER.info("==========[MR Recipe Editor]============");
            return;
        }

        RecipeEditorConfig.getInstance();

        // Player join recipe book sync on local integrated server
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                com.recipeeditor.recipe.CustomRecipeDispatcher.sendCustomRecipeBookEntries(handler.getPlayer());
            } catch (Exception e) {
                LOGGER.error("Failed to sync RecipeEditor recipes for player", e);
            }
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            RecipeEditorConfig.getInstance().invalidateAllRecipeCaches();
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD
                .register((server, resourceManager, success) -> {
                    if (success) {
                        RecipeEditorConfig.getInstance().invalidateAllRecipeCaches();
                        com.recipeeditor.recipe.CustomRecipeDispatcher
                                .syncRecipeBookToPlayers(server.getPlayerList().getPlayers());
                    }
                });
    }
}
