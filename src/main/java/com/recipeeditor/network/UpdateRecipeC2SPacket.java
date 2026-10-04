package com.recipeeditor.network;

import com.recipeeditor.RecipeEditorMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record UpdateRecipeC2SPacket(int action, String payload) implements CustomPayload {
    public static final int ACTION_SAVE_RECIPE = 1;
    public static final int ACTION_DELETE_RECIPE = 2;
    public static final int ACTION_RESET_DEFAULTS = 3;
    public static final int ACTION_TOGGLE_ENABLED = 4;
    public static final int ACTION_DELETE_ALL_FOR_ITEM = 5;

    public static final Id<UpdateRecipeC2SPacket> ID = new Id<>(Identifier.of(RecipeEditorMod.MOD_ID, "update_recipe"));
    public static final PacketCodec<RegistryByteBuf, UpdateRecipeC2SPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, UpdateRecipeC2SPacket::action,
            PacketCodecs.string(1048576), UpdateRecipeC2SPacket::payload,
            UpdateRecipeC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
