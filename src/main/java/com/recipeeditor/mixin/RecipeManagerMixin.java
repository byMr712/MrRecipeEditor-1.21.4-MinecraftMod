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

    @Inject(method = "get(Lnet/minecraft/util/Identifier;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private void onGet(Identifier id, CallbackInfoReturnable<Optional<RecipeEntry<?>>> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        Optional<RecipeEntry<?>> original = cir.getReturnValue();
        if (original != null && original.isPresent()) {
            if (CustomRecipeDispatcher.isRecipeOverridden(original.get())) {
                cir.setReturnValue(Optional.empty());
                return;
            }
        }
        if (original == null || original.isEmpty()) {
            Optional<RecipeEntry<?>> custom = CustomRecipeDispatcher.getCustomRecipeEntryById(id);
            if (custom.isPresent()) {
                cir.setReturnValue(custom);
            }
        }
    }
}
