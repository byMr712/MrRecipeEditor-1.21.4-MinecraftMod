package com.recipeeditor.mixin;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ServerRecipeManager.class)
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
}
