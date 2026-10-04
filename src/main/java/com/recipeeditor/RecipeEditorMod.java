package com.recipeeditor;

import com.mojang.serialization.MapCodec;
import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.network.SyncRecipesS2CPacket;
import com.recipeeditor.network.UpdateRecipeC2SPacket;
import com.recipeeditor.recipe.CustomDynamicCraftingRecipe;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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

        // Register custom networking payloads
        PayloadTypeRegistry.playS2C().register(SyncRecipesS2CPacket.ID, SyncRecipesS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateRecipeC2SPacket.ID, UpdateRecipeC2SPacket.CODEC);

        // Player join sync
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                RegistryKey<Recipe<?>> key = RegistryKey.of(RegistryKeys.RECIPE, Identifier.of(MOD_ID, "custom_crafting"));
                handler.getPlayer().unlockRecipes(List.of(key));

                RecipeEditorConfig config = RecipeEditorConfig.getInstance();
                ServerPlayNetworking.send(handler.getPlayer(), new SyncRecipesS2CPacket(config.toJson()));
            } catch (Exception e) {
                LOGGER.error("Failed to sync RecipeEditor recipes for player", e);
            }
        });

        // Server-side packet handler
        ServerPlayNetworking.registerGlobalReceiver(UpdateRecipeC2SPacket.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                boolean isHost = context.server().isHost(player.getGameProfile());
                if (!isHost && !player.hasPermissionLevel(2)) {
                    LOGGER.warn("Player {} attempted to edit recipes without permission", player.getName().getString());
                    player.sendMessage(Text.literal("§cУ вас нет прав для изменения рецептов."), false);
                    return;
                }

                if (payload.payload() != null && payload.payload().length() > 65536) {
                    LOGGER.warn("Player {} sent oversized recipe payload ({} chars)", player.getName().getString(), payload.payload().length());
                    return;
                }

                RecipeEditorConfig config = RecipeEditorConfig.getInstance();
                boolean changed = false;

                switch (payload.action()) {
                    case UpdateRecipeC2SPacket.ACTION_SAVE_RECIPE -> {
                        CustomRecipeData data = CustomRecipeData.fromJson(payload.payload());
                        if (data != null && data.getResultItem() != net.minecraft.item.Items.AIR) {
                            data.resultCount = Math.max(1, Math.min(1000, data.resultCount));
                            data.cookingTime = Math.max(1, Math.min(72000, data.cookingTime));
                            data.experience = Math.max(0.0f, Math.min(100.0f, data.experience));
                            config.addOrUpdateRecipe(data);
                            changed = true;
                        }
                    }
                    case UpdateRecipeC2SPacket.ACTION_DELETE_RECIPE -> {
                        CustomRecipeData data = CustomRecipeData.fromJson(payload.payload());
                        if (data != null) {
                            config.removeRecipe(data);
                            changed = true;
                        } else if (payload.payload() != null && !payload.payload().isEmpty()) {
                            config.removeRecipeByKey(payload.payload());
                            changed = true;
                        }
                    }
                    case UpdateRecipeC2SPacket.ACTION_RESET_DEFAULTS -> {
                        config.initDefaults();
                        changed = true;
                    }
                    case UpdateRecipeC2SPacket.ACTION_TOGGLE_ENABLED -> {
                        config.modEnabled = Boolean.parseBoolean(payload.payload());
                        changed = true;
                    }
                    default -> LOGGER.warn("Received unknown recipe packet action: {}", payload.action());
                }

                if (changed) {
                    config.save();
                    SyncRecipesS2CPacket syncPacket = new SyncRecipesS2CPacket(config.toJson());
                    for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
                        ServerPlayNetworking.send(p, syncPacket);
                    }
                }
            });
        });

        LOGGER.info("Recipe Editor initialized! Dynamic multi-recipe engine and network sync active.");
    }
}
