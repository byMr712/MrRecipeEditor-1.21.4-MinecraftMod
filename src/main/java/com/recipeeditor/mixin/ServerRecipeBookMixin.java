package com.recipeeditor.mixin;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.book.RecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBook.class)
public class ServerRecipeBookMixin {

    @Inject(method = "contains(Lnet/minecraft/recipe/RecipeEntry;)Z", at = @At("HEAD"), cancellable = true)
    private void onContains(RecipeEntry<?> entry, CallbackInfoReturnable<Boolean> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) {
            return;
        }
        if (entry != null && entry.id() != null) {
            if ("recipeeditor".equals(entry.id().getNamespace())) {
                cir.setReturnValue(true);
                return;
            }
            if (CustomRecipeDispatcher.isRecipeOverridden(entry)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
