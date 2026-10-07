package com.recipeeditor.mixin;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.level.Level;
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

@Mixin(value = RecipeManager.class, priority = 500)
public class ServerRecipeManagerMixin {

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatch(
            RecipeType<T> type,
            I input,
            Level world,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeHolder<T>> custom = CustomRecipeDispatcher.getCustomMatch(type, input, world);
        if (custom.isPresent()) {
            cir.setReturnValue(custom);
        }
    }

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatchReturn(
            RecipeType<T> type,
            I input,
            Level world,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeHolder<T>> res = cir.getReturnValue();
        if (res != null && res.isPresent() && CustomRecipeDispatcher.isRecipeOverridden(res.get())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatchWithLast(
            RecipeType<T> type,
            I input,
            Level world,
            RecipeHolder<T> last,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeHolder<T>> custom = CustomRecipeDispatcher.getCustomMatch(type, input, world);
        if (custom.isPresent()) {
            cir.setReturnValue(custom);
        }
    }

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatchWithLastReturn(
            RecipeType<T> type,
            I input,
            Level world,
            RecipeHolder<T> last,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeHolder<T>> res = cir.getReturnValue();
        if (res != null && res.isPresent() && CustomRecipeDispatcher.isRecipeOverridden(res.get())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatchWithKey(
            RecipeType<T> type,
            I input,
            Level world,
            ResourceKey<Recipe<?>> key,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeHolder<T>> custom = CustomRecipeDispatcher.getCustomMatch(type, input, world);
        if (custom.isPresent()) {
            cir.setReturnValue(custom);
        }
    }

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatchWithKeyReturn(
            RecipeType<T> type,
            I input,
            Level world,
            ResourceKey<Recipe<?>> key,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeHolder<T>> res = cir.getReturnValue();
        if (res != null && res.isPresent() && CustomRecipeDispatcher.isRecipeOverridden(res.get())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private SelectableRecipe.SingleInputSet<StonecutterRecipe> recipeeditor$cachedStonecutter = null;
    @org.spongepowered.asm.mixin.Unique
    private SelectableRecipe.SingleInputSet<StonecutterRecipe> recipeeditor$lastOriginalStonecutter = null;
    @org.spongepowered.asm.mixin.Unique
    private int recipeeditor$lastStonecutterVer = -1;

    @Inject(method = "stonecutterRecipes", at = @At("RETURN"), cancellable = true)
    private void onStonecutterRecipes(CallbackInfoReturnable<SelectableRecipe.SingleInputSet<StonecutterRecipe>> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        SelectableRecipe.SingleInputSet<StonecutterRecipe> original = cir.getReturnValue();
        com.recipeeditor.config.RecipeEditorConfig config = com.recipeeditor.config.RecipeEditorConfig.getInstance();
        int currentVer = config != null ? config.configVersion : 0;
        if (original == recipeeditor$lastOriginalStonecutter && recipeeditor$cachedStonecutter != null && recipeeditor$lastStonecutterVer == currentVer) {
            cir.setReturnValue(recipeeditor$cachedStonecutter);
            return;
        }

        SelectableRecipe.SingleInputSet<StonecutterRecipe> custom = CustomRecipeDispatcher.getCustomStonecutterGrouping();
        List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> combined = new ArrayList<>();
        if (custom != null && !custom.isEmpty()) {
            combined.addAll(custom.entries());
        }
        if (original != null && original.entries() != null) {
            for (SelectableRecipe.SingleInputEntry<StonecutterRecipe> entry : original.entries()) {
                if (entry.recipe() != null && entry.recipe().recipe().isPresent() && CustomRecipeDispatcher.isRecipeOverridden(entry.recipe().recipe().get())) {
                    continue;
                }
                combined.add(entry);
            }
        }
        SelectableRecipe.SingleInputSet<StonecutterRecipe> result = new SelectableRecipe.SingleInputSet<>(combined);
        recipeeditor$lastOriginalStonecutter = original;
        recipeeditor$lastStonecutterVer = currentVer;
        recipeeditor$cachedStonecutter = result;
        cir.setReturnValue(result);
    }

    @org.spongepowered.asm.mixin.Unique
    private SelectableRecipe.SingleInputSet<StonecutterRecipe> recipeeditor$cachedStonecutterSync = null;
    @org.spongepowered.asm.mixin.Unique
    private SelectableRecipe.SingleInputSet<StonecutterRecipe> recipeeditor$lastOriginalStonecutterSync = null;
    @org.spongepowered.asm.mixin.Unique
    private int recipeeditor$lastStonecutterSyncVer = -1;

    @Inject(method = "getSynchronizedStonecutterRecipes", at = @At("RETURN"), cancellable = true)
    private void onGetSynchronizedStonecutterRecipes(CallbackInfoReturnable<SelectableRecipe.SingleInputSet<StonecutterRecipe>> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        SelectableRecipe.SingleInputSet<StonecutterRecipe> original = cir.getReturnValue();
        com.recipeeditor.config.RecipeEditorConfig config = com.recipeeditor.config.RecipeEditorConfig.getInstance();
        int currentVer = config != null ? config.configVersion : 0;
        if (original == recipeeditor$lastOriginalStonecutterSync && recipeeditor$cachedStonecutterSync != null && recipeeditor$lastStonecutterSyncVer == currentVer) {
            cir.setReturnValue(recipeeditor$cachedStonecutterSync);
            return;
        }

        SelectableRecipe.SingleInputSet<StonecutterRecipe> custom = CustomRecipeDispatcher.getCustomStonecutterGrouping();
        List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> combined = new ArrayList<>();
        if (custom != null && !custom.isEmpty()) {
            combined.addAll(custom.entries());
        }
        if (original != null && original.entries() != null) {
            for (SelectableRecipe.SingleInputEntry<StonecutterRecipe> entry : original.entries()) {
                if (entry.recipe() != null && entry.recipe().recipe().isPresent() && CustomRecipeDispatcher.isRecipeOverridden(entry.recipe().recipe().get())) {
                    continue;
                }
                combined.add(entry);
            }
        }
        SelectableRecipe.SingleInputSet<StonecutterRecipe> result = new SelectableRecipe.SingleInputSet<>(combined);
        recipeeditor$lastOriginalStonecutterSync = original;
        recipeeditor$lastStonecutterSyncVer = currentVer;
        recipeeditor$cachedStonecutterSync = result;
        cir.setReturnValue(result);
    }

    @org.spongepowered.asm.mixin.Unique
    private Collection<RecipeHolder<?>> recipeeditor$cachedValues = null;
    @org.spongepowered.asm.mixin.Unique
    private Collection<RecipeHolder<?>> recipeeditor$lastOriginalValues = null;
    @org.spongepowered.asm.mixin.Unique
    private int recipeeditor$lastConfigVer = -1;

    @Inject(method = "getRecipes", at = @At("RETURN"), cancellable = true)
    private void onGetRecipes(CallbackInfoReturnable<Collection<RecipeHolder<?>>> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Collection<RecipeHolder<?>> original = cir.getReturnValue();
        com.recipeeditor.config.RecipeEditorConfig config = com.recipeeditor.config.RecipeEditorConfig.getInstance();
        int currentVer = config != null ? config.configVersion : 0;
        if (original == recipeeditor$lastOriginalValues && recipeeditor$cachedValues != null && recipeeditor$lastConfigVer == currentVer) {
            cir.setReturnValue(recipeeditor$cachedValues);
            return;
        }

        List<RecipeHolder<?>> filtered = new ArrayList<>();
        if (original != null) {
            for (RecipeHolder<?> entry : original) {
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

    @Inject(method = "byKey", at = @At("RETURN"), cancellable = true)
    private void onByKey(ResourceKey<Recipe<?>> key, CallbackInfoReturnable<Optional<RecipeHolder<?>>> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeHolder<?>> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) {
            Optional<RecipeHolder<?>> custom = CustomRecipeDispatcher.getCustomRecipeEntryByKey(key);
            if (custom.isPresent()) {
                cir.setReturnValue(custom);
            }
        }
    }

    @Inject(method = "getRecipeFromDisplay", at = @At("HEAD"), cancellable = true)
    private void onGetRecipeFromDisplay(RecipeDisplayId id, CallbackInfoReturnable<RecipeManager.ServerDisplayInfo> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        if (id != null && id.index() >= 1_000_000) {
            RecipeManager.ServerDisplayInfo custom = CustomRecipeDispatcher.getCustomServerRecipe(id);
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }

    @Inject(method = "getRecipeFromDisplay", at = @At("RETURN"), cancellable = true)
    private void onGetRecipeFromDisplayReturn(RecipeDisplayId id, CallbackInfoReturnable<RecipeManager.ServerDisplayInfo> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        RecipeManager.ServerDisplayInfo original = cir.getReturnValue();
        if (original != null && original.parent() != null && CustomRecipeDispatcher.isRecipeOverridden(original.parent())) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "listDisplaysForRecipe", at = @At("HEAD"), cancellable = true)
    private void onListDisplaysForRecipe(ResourceKey<Recipe<?>> key, Consumer<RecipeDisplayEntry> consumer, CallbackInfo ci) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        if (key != null && key.identifier() != null) {
            if (CustomRecipeDispatcher.isIdOverridden(key.identifier().toString())) {
                ci.cancel();
                return;
            }
            if ("recipeeditor".equals(key.identifier().getNamespace())) {
                List<RecipeManager.ServerDisplayInfo> list = CustomRecipeDispatcher.getCustomServerRecipesByKey(key);
                if (list != null) {
                    for (RecipeManager.ServerDisplayInfo r : list) {
                        consumer.accept(r.display());
                    }
                }
                ci.cancel();
            }
        }
    }

    @Inject(method = "finalizeRecipeLoading", at = @At("RETURN"))
    private void onFinalizeRecipeLoading(net.minecraft.world.flag.FeatureFlagSet features, CallbackInfo ci) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        CustomRecipeDispatcher.invalidateRecipeBookCache();
        CustomRecipeDispatcher.ensureRecipeBookEntriesUpToDate();
    }
}
