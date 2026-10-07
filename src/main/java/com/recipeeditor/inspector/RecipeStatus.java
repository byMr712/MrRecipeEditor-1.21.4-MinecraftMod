package com.recipeeditor.inspector;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum RecipeStatus {
    UNCRAFTABLE("recipeeditor.gui.status_uncraftable", ChatFormatting.RED),
    VANILLA_OR_MODDED("recipeeditor.gui.status_vanilla", ChatFormatting.GOLD),
    CUSTOM("recipeeditor.gui.status_custom", ChatFormatting.GREEN);

    private final String translationKey;
    private final ChatFormatting formatting;

    RecipeStatus(String translationKey, ChatFormatting formatting) {
        this.translationKey = translationKey;
        this.formatting = formatting;
    }

    public Component getDisplayText() {
        return Component.translatable(translationKey).withStyle(formatting, ChatFormatting.BOLD);
    }

    public ChatFormatting getFormatting() {
        return formatting;
    }
}
