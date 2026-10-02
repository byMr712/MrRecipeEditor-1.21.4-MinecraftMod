package com.recipeeditor.network;

import com.recipeeditor.RecipeEditorMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SyncRecipesS2CPacket(String jsonConfig) implements CustomPayload {
    public static final Id<SyncRecipesS2CPacket> ID = new Id<>(Identifier.of(RecipeEditorMod.MOD_ID, "sync_recipes"));
    public static final PacketCodec<RegistryByteBuf, SyncRecipesS2CPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.string(1048576), SyncRecipesS2CPacket::jsonConfig,
            SyncRecipesS2CPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
