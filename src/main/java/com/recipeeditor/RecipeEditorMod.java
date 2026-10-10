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
        try {
            return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
        } catch (Throwable t) {
            return false;
        }
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
        ensureItemComponentsBound();

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

    public static void ensureItemComponentsBound() {
        try {
            if (!net.minecraft.world.item.Items.STONE.builtInRegistryHolder().areComponentsBound()) {
                net.minecraft.core.HolderLookup.Provider baseProvider = net.minecraft.core.HolderLookup.Provider.create(
                        BuiltInRegistries.REGISTRY.stream().map(r -> (net.minecraft.core.HolderLookup.RegistryLookup<?>) r)
                );
                net.minecraft.core.HolderLookup.Provider safeProvider = new net.minecraft.core.HolderLookup.Provider() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> java.util.Optional<net.minecraft.core.HolderLookup.RegistryLookup<T>> lookup(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<? extends T>> key) {
                        java.util.Optional<net.minecraft.core.HolderLookup.RegistryLookup<T>> res =
                                (java.util.Optional<net.minecraft.core.HolderLookup.RegistryLookup<T>>) (java.util.Optional<?>) baseProvider.lookup(key);
                        if (res.isPresent()) return res;
                        return java.util.Optional.of(new net.minecraft.core.HolderLookup.RegistryLookup<T>() {
                            @Override
                            public net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<? extends T>> key() {
                                return key;
                            }

                            @Override
                            public com.mojang.serialization.Lifecycle registryLifecycle() {
                                return com.mojang.serialization.Lifecycle.stable();
                            }

                            @Override
                            public java.util.stream.Stream<net.minecraft.core.HolderSet.Named<T>> listTags() {
                                return java.util.stream.Stream.empty();
                            }

                            @Override
                            public java.util.stream.Stream<net.minecraft.core.Holder.Reference<T>> listElements() {
                                return java.util.stream.Stream.empty();
                            }

                            @Override
                            public java.util.Optional<net.minecraft.core.Holder.Reference<T>> get(net.minecraft.resources.ResourceKey<T> resourceKey) {
                                return java.util.Optional.of(net.minecraft.core.Holder.Reference.createStandAlone(new net.minecraft.core.HolderOwner<T>() {}, resourceKey));
                            }

                            @Override
                            public java.util.Optional<net.minecraft.core.HolderSet.Named<T>> get(net.minecraft.tags.TagKey<T> tagKey) {
                                return java.util.Optional.of(net.minecraft.core.HolderSet.emptyNamed(new net.minecraft.core.HolderOwner<T>() {}, tagKey));
                            }
                        });
                    }

                    @Override
                    public java.util.stream.Stream<net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>>> listRegistryKeys() {
                        return baseProvider.listRegistryKeys();
                    }

                    @Override
                    public <T> java.util.Optional<net.minecraft.core.HolderSet.Named<T>> get(net.minecraft.tags.TagKey<T> tagKey) {
                        java.util.Optional<net.minecraft.core.HolderSet.Named<T>> res = baseProvider.get(tagKey);
                        if (res.isPresent()) return res;
                        return java.util.Optional.of(net.minecraft.core.HolderSet.emptyNamed(new net.minecraft.core.HolderOwner<T>() {}, tagKey));
                    }

                    @Override
                    public <T> net.minecraft.core.HolderSet.Named<T> getOrThrow(net.minecraft.tags.TagKey<T> tagKey) {
                        return get(tagKey).get();
                    }

                    @Override
                    public <T> java.util.Optional<net.minecraft.core.Holder.Reference<T>> get(net.minecraft.resources.ResourceKey<T> resourceKey) {
                        java.util.Optional<net.minecraft.core.Holder.Reference<T>> res = baseProvider.get(resourceKey);
                        if (res.isPresent()) return res;
                        return java.util.Optional.of(net.minecraft.core.Holder.Reference.createStandAlone(new net.minecraft.core.HolderOwner<T>() {}, resourceKey));
                    }

                    @Override
                    public <T> net.minecraft.core.Holder.Reference<T> getOrThrow(net.minecraft.resources.ResourceKey<T> resourceKey) {
                        return get(resourceKey).get();
                    }
                };
                java.util.List<net.minecraft.core.component.DataComponentInitializers.PendingComponents<?>> pending =
                        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(safeProvider);
                for (net.minecraft.core.component.DataComponentInitializers.PendingComponents<?> p : pending) {
                    p.apply();
                }
                LOGGER.info("Successfully bound default item data components for title screen menu");
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to bind item components: " + t.getMessage(), t);
        }
    }
}
