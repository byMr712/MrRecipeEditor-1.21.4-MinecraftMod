package com.recipeeditor.mixin;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerRecipeBook.class)
public class ServerRecipeBookMixin {

    @Inject(method = "sendInitRecipesPacket", at = @At("RETURN"))
    private void onSendInitRecipesPacket(ServerPlayerEntity player, CallbackInfo ci) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) {
            return;
        }
        CustomRecipeDispatcher.sendCustomRecipeBookEntries(player);
    }
}
