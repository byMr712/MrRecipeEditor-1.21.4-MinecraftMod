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

    public static RecipeTypeEnum fromRecipeType(net.minecraft.recipe.RecipeType<?> type) {
        if (type == net.minecraft.recipe.RecipeType.SMELTING) return SMELTING;
        if (type == net.minecraft.recipe.RecipeType.BLASTING) return BLASTING;
        if (type == net.minecraft.recipe.RecipeType.SMOKING) return SMOKING;
        if (type == net.minecraft.recipe.RecipeType.CAMPFIRE_COOKING) return CAMPFIRE_COOKING;
        if (type == net.minecraft.recipe.RecipeType.STONECUTTING) return STONECUTTING;
        if (type == net.minecraft.recipe.RecipeType.SMITHING) return SMITHING;
        if (type == net.minecraft.recipe.RecipeType.CRAFTING) return SHAPED_CRAFTING;
        return null;
    }

    public net.minecraft.recipe.RecipeType<?> toRecipeType() {
        return switch (this) {
            case SMELTING -> net.minecraft.recipe.RecipeType.SMELTING;
            case BLASTING -> net.minecraft.recipe.RecipeType.BLASTING;
            case SMOKING -> net.minecraft.recipe.RecipeType.SMOKING;
            case CAMPFIRE_COOKING -> net.minecraft.recipe.RecipeType.CAMPFIRE_COOKING;
            case STONECUTTING -> net.minecraft.recipe.RecipeType.STONECUTTING;
            case SMITHING -> net.minecraft.recipe.RecipeType.SMITHING;
            case SHAPED_CRAFTING -> net.minecraft.recipe.RecipeType.CRAFTING;
        };
    }
}
