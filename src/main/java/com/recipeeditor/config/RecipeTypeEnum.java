package com.recipeeditor.config;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeType;

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

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }

    public Component getTooltip() {
        return Component.translatable(tooltipKey);
    }

    public static RecipeTypeEnum fromRecipeType(RecipeType<?> type) {
        if (type == RecipeType.SMELTING) return SMELTING;
        if (type == RecipeType.BLASTING) return BLASTING;
        if (type == RecipeType.SMOKING) return SMOKING;
        if (type == RecipeType.CAMPFIRE_COOKING) return CAMPFIRE_COOKING;
        if (type == RecipeType.STONECUTTING) return STONECUTTING;
        if (type == RecipeType.SMITHING) return SMITHING;
        if (type == RecipeType.CRAFTING) return SHAPED_CRAFTING;
        return null;
    }

    public RecipeType<?> toRecipeType() {
        return switch (this) {
            case SMELTING -> RecipeType.SMELTING;
            case BLASTING -> RecipeType.BLASTING;
            case SMOKING -> RecipeType.SMOKING;
            case CAMPFIRE_COOKING -> RecipeType.CAMPFIRE_COOKING;
            case STONECUTTING -> RecipeType.STONECUTTING;
            case SMITHING -> RecipeType.SMITHING;
            case SHAPED_CRAFTING -> RecipeType.CRAFTING;
        };
    }
}
