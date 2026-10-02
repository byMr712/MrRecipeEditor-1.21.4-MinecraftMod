package com.recipeeditor.config;

import net.minecraft.text.Text;

public enum RecipeTypeEnum {
    SHAPED_CRAFTING("recipeeditor.gui.type_shaped"),
    SHAPELESS_CRAFTING("recipeeditor.gui.type_shapeless"),
    SMELTING("recipeeditor.gui.type_smelting"),
    STONECUTTING("recipeeditor.gui.type_stonecutting");

    private final String translationKey;

    RecipeTypeEnum(String translationKey) {
        this.translationKey = translationKey;
    }

    public Text getDisplayName() {
        return Text.translatable(translationKey);
    }
}
