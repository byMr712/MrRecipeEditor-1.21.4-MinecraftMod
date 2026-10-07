package com.recipeeditor.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * Adapter bridging legacy DrawContext calls to Minecraft 26.x GuiGraphicsExtractor.
 */
public class DrawContext {
    private final GuiGraphicsExtractor extractor;

    public DrawContext(GuiGraphicsExtractor extractor) {
        this.extractor = extractor;
    }

    public GuiGraphicsExtractor getExtractor() {
        return extractor;
    }

    public Matrix3x2fStack getMatrices() {
        return extractor.pose();
    }

    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        extractor.fill(minX, minY, maxX, maxY, color);
    }

    public void fillGradient(int minX, int minY, int maxX, int maxY, int colorStart, int colorEnd) {
        extractor.fillGradient(minX, minY, maxX, maxY, colorStart, colorEnd);
    }

    public void drawItem(ItemStack stack, int x, int y) {
        extractor.item(stack, x, y);
    }

    public void drawItem(ItemStack stack, int x, int y, int seed) {
        extractor.item(stack, x, y, seed);
    }

    public void drawStackOverlay(Font font, ItemStack stack, int x, int y) {
        extractor.itemDecorations(font, stack, x, y);
    }

    public void drawStackOverlay(Font font, ItemStack stack, int x, int y, String text) {
        extractor.itemDecorations(font, stack, x, y, text);
    }

    public void drawTextWithShadow(Font font, Component text, int x, int y, int color) {
        extractor.text(font, text, x, y, color, true);
    }

    public void drawTextWithShadow(Font font, net.minecraft.util.FormattedCharSequence text, int x, int y, int color) {
        extractor.text(font, text, x, y, color, true);
    }

    public void drawTextWithShadow(Font font, String text, int x, int y, int color) {
        extractor.text(font, text, x, y, color, true);
    }

    public void drawCenteredTextWithShadow(Font font, Component text, int centerX, int y, int color) {
        extractor.centeredText(font, text, centerX, y, color);
    }

    public void drawCenteredTextWithShadow(Font font, String text, int centerX, int y, int color) {
        extractor.centeredText(font, text, centerX, y, color);
    }

    public void drawItemTooltip(Font font, ItemStack stack, int x, int y) {
        extractor.setTooltipForNextFrame(font, stack, x, y);
    }

    public void drawTooltip(Font font, Component text, int x, int y) {
        extractor.setTooltipForNextFrame(text, x, y);
    }

    public void drawTooltip(Font font, List<Component> text, int x, int y) {
        extractor.setComponentTooltipForNextFrame(font, text, x, y);
    }

    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        extractor.enableScissor(minX, minY, maxX, maxY);
    }

    public void disableScissor() {
        extractor.disableScissor();
    }
}
