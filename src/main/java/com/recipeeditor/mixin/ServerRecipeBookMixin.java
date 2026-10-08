package com.recipeeditor.mixin;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerRecipeBook;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerRecipeBook.class)
public class ServerRecipeBookMixin {

    @Inject(method = "sendInitRecipesPacket", at = @At("HEAD"))
    private void onSendInitRecipesPacketHead(ServerPlayerEntity player, CallbackInfo ci) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) {
            return;
        }
        try {
            ServerRecipeBook book = (ServerRecipeBook) (Object) this;
            for (net.minecraft.recipe.RecipeEntry<?> entry : CustomRecipeDispatcher.getAllCustomRecipes()) {
                book.add(entry);
                book.display(entry);
            }
        } catch (Throwable ignored) {}
    }

    @Inject(method = "sendInitRecipesPacket", at = @At("RETURN"))
    private void onSendInitRecipesPacket(ServerPlayerEntity player, CallbackInfo ci) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) {
            return;
        }
        CustomRecipeDispatcher.sendCustomRecipeBookEntries(player);
    }

    @Redirect(
            method = "handleList",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;)V"
            )
    )
    private void recipeeditor$silenceUnrecognizedRecipeLog(Logger logger, String message, Object arg) {
        if (arg instanceof Identifier id && "recipeeditor".equals(id.getNamespace())) {
            // Silently ignore custom recipes that were deleted, modified, or not saved to config
            return;
        }
        logger.error(message, arg);
    }
}
