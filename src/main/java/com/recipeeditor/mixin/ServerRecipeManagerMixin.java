package com.recipeeditor.mixin;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.recipe.display.CuttingRecipeDisplay;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.recipe.StonecuttingRecipe;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Mixin(value = ServerRecipeManager.class, priority = 500)
public class ServerRecipeManagerMixin {

    @Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatch(
            RecipeType<T> type,
            I input,
            World world,
            CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeEntry<T>> custom = CustomRecipeDispatcher.getCustomMatch(type, input, world);
        if (custom.isPresent()) {
            cir.setReturnValue(custom);
        }
    }

    @Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatchReturn(
            RecipeType<T> type,
            I input,
            World world,
            CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeEntry<T>> res = cir.getReturnValue();
        if (res != null && res.isPresent() && CustomRecipeDispatcher.isRecipeOverridden(res.get())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;Lnet/minecraft/recipe/RecipeEntry;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatchWithLast(
            RecipeType<T> type,
            I input,
            World world,
            RecipeEntry<T> last,
            CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeEntry<T>> custom = CustomRecipeDispatcher.getCustomMatch(type, input, world);
        if (custom.isPresent()) {
            cir.setReturnValue(custom);
        }
    }

    @Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;Lnet/minecraft/recipe/RecipeEntry;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatchWithLastReturn(
            RecipeType<T> type,
            I input,
            World world,
            RecipeEntry<T> last,
            CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeEntry<T>> res = cir.getReturnValue();
        if (res != null && res.isPresent() && CustomRecipeDispatcher.isRecipeOverridden(res.get())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;Lnet/minecraft/registry/RegistryKey;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatchWithKey(
            RecipeType<T> type,
            I input,
            World world,
            RegistryKey<Recipe<?>> key,
            CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeEntry<T>> custom = CustomRecipeDispatcher.getCustomMatch(type, input, world);
        if (custom.isPresent()) {
            cir.setReturnValue(custom);
        }
    }

    @Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;Lnet/minecraft/registry/RegistryKey;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatchWithKeyReturn(
            RecipeType<T> type,
            I input,
            World world,
            RegistryKey<Recipe<?>> key,
            CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeEntry<T>> res = cir.getReturnValue();
        if (res != null && res.isPresent() && CustomRecipeDispatcher.isRecipeOverridden(res.get())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private CuttingRecipeDisplay.Grouping<StonecuttingRecipe> recipeeditor$cachedStonecutter = null;
    @org.spongepowered.asm.mixin.Unique
    private CuttingRecipeDisplay.Grouping<StonecuttingRecipe> recipeeditor$lastOriginalStonecutter = null;
    @org.spongepowered.asm.mixin.Unique
    private int recipeeditor$lastStonecutterVer = -1;

    @Inject(method = "getStonecutterRecipes", at = @At("RETURN"), cancellable = true)
    private void onGetStonecutterRecipes(CallbackInfoReturnable<CuttingRecipeDisplay.Grouping<StonecuttingRecipe>> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        CuttingRecipeDisplay.Grouping<StonecuttingRecipe> original = cir.getReturnValue();
        com.recipeeditor.config.RecipeEditorConfig config = com.recipeeditor.config.RecipeEditorConfig.getInstance();
        int currentVer = config != null ? config.configVersion : 0;
        if (original == recipeeditor$lastOriginalStonecutter && recipeeditor$cachedStonecutter != null && recipeeditor$lastStonecutterVer == currentVer) {
            cir.setReturnValue(recipeeditor$cachedStonecutter);
            return;
        }

        CuttingRecipeDisplay.Grouping<StonecuttingRecipe> custom = CustomRecipeDispatcher.getCustomStonecutterGrouping();
        List<CuttingRecipeDisplay.GroupEntry<StonecuttingRecipe>> combined = new ArrayList<>();
        if (custom != null && !custom.isEmpty()) {
            combined.addAll(custom.entries());
        }
        if (original != null && original.entries() != null) {
            for (CuttingRecipeDisplay.GroupEntry<StonecuttingRecipe> entry : original.entries()) {
                if (entry.recipe() != null && entry.recipe().recipe().isPresent() && CustomRecipeDispatcher.isRecipeOverridden(entry.recipe().recipe().get())) {
                    continue;
                }
                combined.add(entry);
            }
        }
        CuttingRecipeDisplay.Grouping<StonecuttingRecipe> result = new CuttingRecipeDisplay.Grouping<>(combined);
        recipeeditor$lastOriginalStonecutter = original;
        recipeeditor$lastStonecutterVer = currentVer;
        recipeeditor$cachedStonecutter = result;
        cir.setReturnValue(result);
    }

    @org.spongepowered.asm.mixin.Unique
    private CuttingRecipeDisplay.Grouping<StonecuttingRecipe> recipeeditor$cachedStonecutterSync = null;
    @org.spongepowered.asm.mixin.Unique
    private CuttingRecipeDisplay.Grouping<StonecuttingRecipe> recipeeditor$lastOriginalStonecutterSync = null;
    @org.spongepowered.asm.mixin.Unique
    private int recipeeditor$lastStonecutterSyncVer = -1;

    @Inject(method = "getStonecutterRecipeForSync", at = @At("RETURN"), cancellable = true)
    private void onGetStonecutterRecipeForSync(CallbackInfoReturnable<CuttingRecipeDisplay.Grouping<StonecuttingRecipe>> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        CuttingRecipeDisplay.Grouping<StonecuttingRecipe> original = cir.getReturnValue();
        com.recipeeditor.config.RecipeEditorConfig config = com.recipeeditor.config.RecipeEditorConfig.getInstance();
        int currentVer = config != null ? config.configVersion : 0;
        if (original == recipeeditor$lastOriginalStonecutterSync && recipeeditor$cachedStonecutterSync != null && recipeeditor$lastStonecutterSyncVer == currentVer) {
            cir.setReturnValue(recipeeditor$cachedStonecutterSync);
            return;
        }

        CuttingRecipeDisplay.Grouping<StonecuttingRecipe> custom = CustomRecipeDispatcher.getCustomStonecutterGrouping();
        List<CuttingRecipeDisplay.GroupEntry<StonecuttingRecipe>> combined = new ArrayList<>();
        if (custom != null && !custom.isEmpty()) {
            combined.addAll(custom.entries());
        }
        if (original != null && original.entries() != null) {
            for (CuttingRecipeDisplay.GroupEntry<StonecuttingRecipe> entry : original.entries()) {
                if (entry.recipe() != null && entry.recipe().recipe().isPresent() && CustomRecipeDispatcher.isRecipeOverridden(entry.recipe().recipe().get())) {
                    continue;
                }
                combined.add(entry);
            }
        }
        CuttingRecipeDisplay.Grouping<StonecuttingRecipe> result = new CuttingRecipeDisplay.Grouping<>(combined);
        recipeeditor$lastOriginalStonecutterSync = original;
        recipeeditor$lastStonecutterSyncVer = currentVer;
        recipeeditor$cachedStonecutterSync = result;
        cir.setReturnValue(result);
    }

    @org.spongepowered.asm.mixin.Unique
    private Collection<RecipeEntry<?>> recipeeditor$cachedValues = null;
    @org.spongepowered.asm.mixin.Unique
    private Collection<RecipeEntry<?>> recipeeditor$lastOriginalValues = null;
    @org.spongepowered.asm.mixin.Unique
    private int recipeeditor$lastConfigVer = -1;

    @Inject(method = "values", at = @At("RETURN"), cancellable = true)
    private void onValues(CallbackInfoReturnable<Collection<RecipeEntry<?>>> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Collection<RecipeEntry<?>> original = cir.getReturnValue();
        com.recipeeditor.config.RecipeEditorConfig config = com.recipeeditor.config.RecipeEditorConfig.getInstance();
        int currentVer = config != null ? config.configVersion : 0;
        if (original == recipeeditor$lastOriginalValues && recipeeditor$cachedValues != null && recipeeditor$lastConfigVer == currentVer) {
            cir.setReturnValue(recipeeditor$cachedValues);
            return;
        }

        List<RecipeEntry<?>> filtered = new ArrayList<>();
        if (original != null) {
            for (RecipeEntry<?> entry : original) {
                if (!CustomRecipeDispatcher.isRecipeOverridden(entry)) {
                    filtered.add(entry);
                }
            }
        }
        filtered.addAll(CustomRecipeDispatcher.getAllCustomRecipes());
        recipeeditor$lastOriginalValues = original;
        recipeeditor$lastConfigVer = currentVer;
        recipeeditor$cachedValues = java.util.Collections.unmodifiableList(filtered);
        cir.setReturnValue(recipeeditor$cachedValues);
    }

    @Inject(method = "get(Lnet/minecraft/registry/RegistryKey;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private void onGet(RegistryKey<Recipe<?>> key, CallbackInfoReturnable<Optional<RecipeEntry<?>>> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeEntry<?>> original = cir.getReturnValue();
        if (original != null && original.isPresent()) {
            if (CustomRecipeDispatcher.isRecipeOverridden(original.get())) {
                cir.setReturnValue(Optional.empty());
                return;
            }
        }
        if (original == null || original.isEmpty()) {
            Optional<RecipeEntry<?>> custom = CustomRecipeDispatcher.getCustomRecipeEntryByKey(key);
            if (custom.isPresent()) {
                cir.setReturnValue(custom);
            }
        }
    }

    @Inject(method = "get(Lnet/minecraft/recipe/NetworkRecipeId;)Lnet/minecraft/recipe/ServerRecipeManager$ServerRecipe;", at = @At("HEAD"), cancellable = true)
    private void onGetNetworkRecipe(net.minecraft.recipe.NetworkRecipeId id, CallbackInfoReturnable<ServerRecipeManager.ServerRecipe> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        if (id != null && id.index() >= 1_000_000) {
            ServerRecipeManager.ServerRecipe custom = CustomRecipeDispatcher.getCustomServerRecipe(id);
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }

    @Inject(method = "get(Lnet/minecraft/recipe/NetworkRecipeId;)Lnet/minecraft/recipe/ServerRecipeManager$ServerRecipe;", at = @At("RETURN"), cancellable = true)
    private void onGetNetworkRecipeReturn(net.minecraft.recipe.NetworkRecipeId id, CallbackInfoReturnable<ServerRecipeManager.ServerRecipe> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        ServerRecipeManager.ServerRecipe original = cir.getReturnValue();
        if (original != null && original.parent() != null && CustomRecipeDispatcher.isRecipeOverridden(original.parent())) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "forEachRecipeDisplay", at = @At("HEAD"), cancellable = true)
    private void onForEachRecipeDisplay(RegistryKey<Recipe<?>> key, Consumer<RecipeDisplayEntry> consumer, CallbackInfo ci) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        if (key != null && key.getValue() != null) {
            if (CustomRecipeDispatcher.isIdOverridden(key.getValue().toString())) {
                ci.cancel();
                return;
            }
            if ("recipeeditor".equals(key.getValue().getNamespace())) {
                List<ServerRecipeManager.ServerRecipe> list = CustomRecipeDispatcher.getCustomServerRecipesByKey(key);
                if (list != null) {
                    for (ServerRecipeManager.ServerRecipe r : list) {
                        consumer.accept(r.display());
                    }
                }
                ci.cancel();
            }
        }
    }

    @Inject(method = "initialize", at = @At("RETURN"))
    private void onInitialize(net.minecraft.resource.featuretoggle.FeatureSet features, CallbackInfo ci) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        CustomRecipeDispatcher.invalidateRecipeBookCache();
        CustomRecipeDispatcher.ensureRecipeBookEntriesUpToDate();
    }
}
