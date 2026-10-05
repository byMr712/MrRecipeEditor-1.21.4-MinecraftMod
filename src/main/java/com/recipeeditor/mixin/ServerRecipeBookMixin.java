package com.recipeeditor.mixin;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerRecipeBook.class)
public class ServerRecipeBookMixin {

    @Inject(method = "isUnlocked", at = @At("HEAD"), cancellable = true)
    private void onIsUnlocked(RegistryKey<Recipe<?>> key, CallbackInfoReturnable<Boolean> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) {
            return;
        }
        if (key != null && key.getValue() != null) {
            if ("recipeeditor".equals(key.getValue().getNamespace())) {
                cir.setReturnValue(true);
                return;
            }
            if (CustomRecipeDispatcher.isIdentifierOverridden(key.getValue())) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

    @Inject(method = "sendInitRecipesPacket", at = @At("RETURN"))
    private void onSendInitRecipesPacket(ServerPlayerEntity player, CallbackInfo ci) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) {
            return;
        }
        CustomRecipeDispatcher.sendCustomRecipeBookEntries(player);
    }
}
