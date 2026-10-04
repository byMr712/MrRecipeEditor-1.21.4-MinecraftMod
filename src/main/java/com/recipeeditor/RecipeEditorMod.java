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
                RecipeEditorConfig config = RecipeEditorConfig.getInstance();
                ServerPlayNetworking.send(handler.getPlayer(), new SyncRecipesS2CPacket(config.toJson()));
                com.recipeeditor.recipe.CustomRecipeDispatcher.sendCustomRecipeBookEntries(handler.getPlayer());
            } catch (Exception e) {
                LOGGER.error("Failed to sync RecipeEditor recipes for player", e);
            }
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            RecipeEditorConfig.getInstance().invalidateAllRecipeCaches();
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                RecipeEditorConfig.getInstance().invalidateAllRecipeCaches();
                com.recipeeditor.recipe.CustomRecipeDispatcher.syncRecipeBookToPlayers(server.getPlayerManager().getPlayerList());
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
                        if (data != null && data.type != null && data.getResultItem() != net.minecraft.item.Items.AIR) {
                            if (data.patternSlots == null || data.patternSlots.length != 9) {
                                String[] newSlots = new String[9];
                                java.util.Arrays.fill(newSlots, "minecraft:air");
                                if (data.patternSlots != null) {
                                    System.arraycopy(data.patternSlots, 0, newSlots, 0, Math.min(data.patternSlots.length, 9));
                                }
                                data.patternSlots = newSlots;
                            }
                            for (int i = 0; i < 9; i++) {
                                if (data.patternSlots[i] == null) data.patternSlots[i] = "minecraft:air";
                            }
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
                    case UpdateRecipeC2SPacket.ACTION_DELETE_ALL_FOR_ITEM -> {
                        String itemId = payload.payload();
                        if (itemId != null && !itemId.isEmpty()) {
                            java.util.List<CustomRecipeData> toRemove = new java.util.ArrayList<>();
                            for (CustomRecipeData r : config.recipes.values()) {
                                if (itemId.equals(r.resultItemId)) {
                                    toRemove.add(r);
                                }
                            }
                            for (CustomRecipeData r : toRemove) {
                                config.removeRecipe(r);
                            }
                            if (!toRemove.isEmpty()) {
                                changed = true;
                            }
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
                    com.recipeeditor.recipe.CustomRecipeDispatcher.syncRecipeBookToPlayers(context.server().getPlayerManager().getPlayerList());

                    // Dynamically refresh stonecutter recipes for connected players
                    try {
                        var recipeManager = context.server().getRecipeManager();
                        var stonecutterPacket = new net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket(
                                recipeManager.getPropertySets(),
                                recipeManager.getStonecutterRecipeForSync()
                        );
                        for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
                            p.networkHandler.sendPacket(stonecutterPacket);
                        }
                    } catch (Throwable t) {
                        LOGGER.warn("Failed to synchronize stonecutter recipes: {}", t.getMessage());
                    }
                }
            });
        });

        LOGGER.info("Recipe Editor initialized! Dynamic multi-recipe engine and network sync active.");
    }
}
