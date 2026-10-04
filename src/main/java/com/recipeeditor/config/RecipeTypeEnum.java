package com.recipeeditor.config;

import net.minecraft.text.Text;

public enum RecipeTypeEnum {
    SHAPED_CRAFTING("block.minecraft.crafting_table", "recipeeditor.gui.tooltip_shaped"),
    SMITHING("block.minecraft.smithing_table", "recipeeditor.gui.tooltip_smithing"),
    SMELTING("block.minecraft.furnace", "recipeeditor.gui.tooltip_smelting"),
    BLASTING("block.minecraft.blast_furnace", "recipeeditor.gui.tooltip_blasting"),
    SMOKING("block.minecraft.smoker", "recipeeditor.gui.tooltip_smoking"),
    STONECUTTING("block.minecraft.stonecutter", "recipeeditor.gui.tooltip_stonecutting"),
    CAMPFIRE_COOKING("block.minecraft.campfire", "recipeeditor.gui.tooltip_campfire");

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
