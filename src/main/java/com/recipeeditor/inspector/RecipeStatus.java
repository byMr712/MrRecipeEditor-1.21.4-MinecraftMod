package com.recipeeditor.inspector;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public enum RecipeStatus {
    UNCRAFTABLE("recipeeditor.gui.status_uncraftable", Formatting.RED),
    VANILLA_OR_MODDED("recipeeditor.gui.status_vanilla", Formatting.GOLD),
    CUSTOM("recipeeditor.gui.status_custom", Formatting.GREEN);

    private final String translationKey;
    private final Formatting formatting;

    RecipeStatus(String translationKey, Formatting formatting) {
        this.translationKey = translationKey;
        this.formatting = formatting;
    }

    public Text getDisplayText() {
        return Text.translatable(translationKey).formatted(formatting, Formatting.BOLD);
    }

    public Formatting getFormatting() {
        return formatting;
    }
}
