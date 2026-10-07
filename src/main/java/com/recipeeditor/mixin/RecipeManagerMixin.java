package com.recipeeditor.mixin;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Mixin(value = RecipeManager.class, priority = 500)
public class RecipeManagerMixin {

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

    @Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;Lnet/minecraft/util/Identifier;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatchWithId(
            RecipeType<T> type,
            I input,
            World world,
            Identifier id,
            CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeEntry<T>> custom = CustomRecipeDispatcher.getCustomMatch(type, input, world);
        if (custom.isPresent()) {
            cir.setReturnValue(custom);
        }
    }

    @Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;Lnet/minecraft/util/Identifier;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatchWithIdReturn(
            RecipeType<T> type,
            I input,
            World world,
            Identifier id,
            CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeEntry<T>> res = cir.getReturnValue();
        if (res != null && res.isPresent() && CustomRecipeDispatcher.isRecipeOverridden(res.get())) {
            cir.setReturnValue(Optional.empty());
        }
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

    @Inject(method = "getAllMatches", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetAllMatches(
            RecipeType<T> type,
            I input,
            World world,
            CallbackInfoReturnable<List<RecipeEntry<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        List<RecipeEntry<T>> original = cir.getReturnValue();
        List<RecipeEntry<T>> combined = new ArrayList<>();
        List<RecipeEntry<T>> custom = CustomRecipeDispatcher.getCustomMatches(type, input, world);
        if (custom != null && !custom.isEmpty()) {
            combined.addAll(custom);
        }
        if (original != null) {
            for (RecipeEntry<T> entry : original) {
                if (!CustomRecipeDispatcher.isRecipeOverridden(entry)) {
                    combined.add(entry);
                }
            }
        }
        cir.setReturnValue(combined);
    }

    @Inject(method = "listAllOfType", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onListAllOfType(
            RecipeType<T> type,
            CallbackInfoReturnable<List<RecipeEntry<T>>> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        List<RecipeEntry<T>> original = cir.getReturnValue();
        List<RecipeEntry<T>> combined = new ArrayList<>();
        List<RecipeEntry<T>> custom = CustomRecipeDispatcher.getAllCustomRecipesOfType(type);
        if (custom != null && !custom.isEmpty()) {
            combined.addAll(custom);
        }
        if (original != null) {
            for (RecipeEntry<T> entry : original) {
                if (!CustomRecipeDispatcher.isRecipeOverridden(entry)) {
                    combined.add(entry);
                }
            }
        }
        cir.setReturnValue(combined);
    }

    @Inject(method = "get(Lnet/minecraft/util/Identifier;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private void onGet(Identifier id, CallbackInfoReturnable<Optional<RecipeEntry<?>>> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeEntry<?>> original = cir.getReturnValue();
        if (original != null && original.isPresent()) {
            if (CustomRecipeDispatcher.isRecipeOverridden(original.get())) {
                Optional<RecipeEntry<?>> custom = CustomRecipeDispatcher.getCustomRecipeForOverridden(id);
                cir.setReturnValue(custom);
                return;
            }
        } else {
            Optional<RecipeEntry<?>> custom = CustomRecipeDispatcher.getCustomRecipeEntryById(id);
            if (custom.isPresent()) {
                cir.setReturnValue(custom);
            }
        }
    }

    @Inject(method = "get(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/util/Identifier;)Lnet/minecraft/recipe/RecipeEntry;", at = @At("RETURN"), cancellable = true)
    private void onGetWithType(RecipeType<?> type, Identifier id, CallbackInfoReturnable<RecipeEntry<?>> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        RecipeEntry<?> original = cir.getReturnValue();
        if (original != null && CustomRecipeDispatcher.isRecipeOverridden(original)) {
            Optional<RecipeEntry<?>> custom = CustomRecipeDispatcher.getCustomRecipeEntryById(id);
            cir.setReturnValue(custom.orElse(null));
            return;
        }
        if (original == null) {
            Optional<RecipeEntry<?>> custom = CustomRecipeDispatcher.getCustomRecipeEntryById(id);
            if (custom.isPresent()) {
                cir.setReturnValue(custom.get());
            }
        }
    }
}
