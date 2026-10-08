package com.recipeeditor.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onSynchronizeRecipes", at = @At("RETURN"))
    private void onSynchronizeRecipesReturn(SynchronizeRecipesS2CPacket packet, CallbackInfo ci) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) {
            return;
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null && client.player.getRecipeBook() != null) {
                ClientRecipeBook book = client.player.getRecipeBook();
                book.getOrderedResults().forEach(r -> r.initialize(book));
                if (client.currentScreen instanceof net.minecraft.client.gui.screen.recipebook.RecipeBookProvider provider) {
                    provider.refreshRecipeBook();
                }
            }
        } catch (Throwable ignored) {}
    }
}
