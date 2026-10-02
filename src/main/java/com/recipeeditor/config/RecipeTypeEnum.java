package com.recipeeditor.config;

import net.minecraft.text.Text;

public enum RecipeTypeEnum {
    SHAPED_CRAFTING("recipeeditor.gui.type_shaped", "recipeeditor.gui.tooltip_shaped"),
    SMITHING("recipeeditor.gui.type_smithing", "recipeeditor.gui.tooltip_smithing"),
    SMELTING("recipeeditor.gui.type_smelting", "recipeeditor.gui.tooltip_smelting"),
    BLASTING("recipeeditor.gui.type_blasting", "recipeeditor.gui.tooltip_blasting"),
    SMOKING("recipeeditor.gui.type_smoking", "recipeeditor.gui.tooltip_smoking"),
    STONECUTTING("recipeeditor.gui.type_stonecutting", "recipeeditor.gui.tooltip_stonecutting"),
    CAMPFIRE_COOKING("recipeeditor.gui.type_campfire", "recipeeditor.gui.tooltip_campfire");

    private final String translationKey;
    private final String tooltipKey;

    RecipeTypeEnum(String translationKey, String tooltipKey) {
        this.translationKey = translationKey;
        this.tooltipKey = tooltipKey;
    }

    public Text getDisplayName() {
        return Text.translatable(translationKey);
    }

    public Text getTooltip() {
        return Text.translatable(tooltipKey);
    }
}
