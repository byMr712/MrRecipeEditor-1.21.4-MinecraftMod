package com.recipeeditor.mixin;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerRecipeBook.class)
public class ServerRecipeBookMixin {

    @Inject(method = "contains", at = @At("HEAD"), cancellable = true)
    private void onContains(ResourceKey<Recipe<?>> key, CallbackInfoReturnable<Boolean> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) {
            return;
        }
        if (key != null && key.identifier() != null) {
            if ("recipeeditor".equals(key.identifier().getNamespace())) {
                cir.setReturnValue(true);
                return;
            }
            if (CustomRecipeDispatcher.isIdentifierOverridden(key.identifier())) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

    @Inject(method = "sendInitialRecipeBook", at = @At("RETURN"))
    private void onSendInitialRecipeBook(ServerPlayer player, CallbackInfo ci) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) {
            return;
        }
        CustomRecipeDispatcher.sendCustomRecipeBookEntries(player);
    }
}
