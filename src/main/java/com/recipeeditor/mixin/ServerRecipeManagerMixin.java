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
        Optional<RecipeEntry<T>> res = cir.getReturnValue();
        if (res != null && res.isPresent() && CustomRecipeDispatcher.isRecipeOverridden(res.get())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "getStonecutterRecipes", at = @At("RETURN"), cancellable = true)
    private void onGetStonecutterRecipes(CallbackInfoReturnable<CuttingRecipeDisplay.Grouping<StonecuttingRecipe>> cir) {
        CuttingRecipeDisplay.Grouping<StonecuttingRecipe> custom = CustomRecipeDispatcher.getCustomStonecutterGrouping();
        if (!custom.isEmpty()) {
            List<CuttingRecipeDisplay.GroupEntry<StonecuttingRecipe>> combined = new ArrayList<>(custom.entries());
            CuttingRecipeDisplay.Grouping<StonecuttingRecipe> original = cir.getReturnValue();
            if (original != null && original.entries() != null) {
                combined.addAll(original.entries());
            }
            cir.setReturnValue(new CuttingRecipeDisplay.Grouping<>(combined));
        }
    }

    @Inject(method = "getStonecutterRecipeForSync", at = @At("RETURN"), cancellable = true)
    private void onGetStonecutterRecipeForSync(CallbackInfoReturnable<CuttingRecipeDisplay.Grouping<StonecuttingRecipe>> cir) {
        CuttingRecipeDisplay.Grouping<StonecuttingRecipe> custom = CustomRecipeDispatcher.getCustomStonecutterGrouping();
        if (!custom.isEmpty()) {
            List<CuttingRecipeDisplay.GroupEntry<StonecuttingRecipe>> combined = new ArrayList<>(custom.entries());
            CuttingRecipeDisplay.Grouping<StonecuttingRecipe> original = cir.getReturnValue();
            if (original != null && original.entries() != null) {
                combined.addAll(original.entries());
            }
            cir.setReturnValue(new CuttingRecipeDisplay.Grouping<>(combined));
        }
    }

    @Inject(method = "values", at = @At("RETURN"), cancellable = true)
    private void onValues(CallbackInfoReturnable<Collection<RecipeEntry<?>>> cir) {
        Collection<RecipeEntry<?>> original = cir.getReturnValue();
        List<RecipeEntry<?>> filtered = new ArrayList<>();
        if (original != null) {
            for (RecipeEntry<?> entry : original) {
                if (!CustomRecipeDispatcher.isRecipeOverridden(entry)) {
                    filtered.add(entry);
                }
            }
        }
        filtered.addAll(CustomRecipeDispatcher.getAllCustomRecipes());
        cir.setReturnValue(filtered);
    }

    @Inject(method = "get(Lnet/minecraft/registry/RegistryKey;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private void onGet(RegistryKey<Recipe<?>> key, CallbackInfoReturnable<Optional<RecipeEntry<?>>> cir) {
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

    @Inject(method = "forEachRecipeDisplay", at = @At("HEAD"), cancellable = true)
    private void onForEachRecipeDisplay(RegistryKey<Recipe<?>> key, Consumer<RecipeDisplayEntry> consumer, CallbackInfo ci) {
        if (key != null && key.getValue() != null) {
            if (CustomRecipeDispatcher.isIdOverridden(key.getValue().toString())) {
                ci.cancel();
            }
        }
    }
}

