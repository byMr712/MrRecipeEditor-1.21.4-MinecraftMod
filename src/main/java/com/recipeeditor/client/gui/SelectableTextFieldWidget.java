package com.recipeeditor.client.gui;

import com.recipeeditor.mixin.client.TextFieldWidgetAccessor;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

public class SelectableTextFieldWidget extends TextFieldWidget {
    private final TextRenderer textRenderer;
    private int dragAnchor = -1;
    private boolean isDraggingSelection = false;
    private long lastClickTime = 0L;
    private double lastClickX = -1;
    private double lastClickY = -1;

    public SelectableTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text text) {
        super(textRenderer, x, y, width, height, text);
        this.textRenderer = textRenderer;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean result = super.mouseClicked(mouseX, mouseY, button);
        if (result && button == 0) {
            long now = Util.getMeasuringTimeMs();
            if (now - lastClickTime < 300L && Math.abs(mouseX - lastClickX) < 5.0 && Math.abs(mouseY - lastClickY) < 5.0) {
                // Double click: select all text in the field
                this.setCursorToStart(false);
                this.setSelectionEnd(this.getText().length());
                this.dragAnchor = 0;
                this.isDraggingSelection = false;
                this.lastClickTime = 0L;
                return true;
            }
            this.lastClickTime = now;
            this.lastClickX = mouseX;
            this.lastClickY = mouseY;
            this.dragAnchor = this.getCursor();
            this.isDraggingSelection = true;
        } else if (!this.isFocused()) {
            this.dragAnchor = -1;
            this.isDraggingSelection = false;
        }
        return result;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (handleMouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    public boolean handleMouseDragged(double mouseX, double mouseY, int button) {
        if (button != 0 || !this.isFocused() || !this.isDraggingSelection || this.dragAnchor < 0) {
            return false;
        }
        int newCursor = getCharIndexAt(mouseX);
        this.setCursor(newCursor, true);
        this.setSelectionEnd(this.dragAnchor);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDraggingSelection = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    public void handleMouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDraggingSelection = false;
        }
    }

    public int getCharIndexAt(double mouseX) {
        String fullText = this.getText();
        if (fullText.isEmpty()) {
            return 0;
        }
        int firstChar = 0;
        try {
            firstChar = ((TextFieldWidgetAccessor) this).getFirstCharacterIndex();
        } catch (Throwable ignored) {}

        int startX = this.getX() + (this.drawsBackground() ? 4 : 0);
        int relX = (int) Math.round(mouseX) - startX;

        if (relX <= 0) {
            return mouseX < this.getX() ? 0 : firstChar;
        }

        int innerWidth = this.getInnerWidth();
        if (relX >= innerWidth || mouseX >= this.getX() + this.getWidth()) {
            return fullText.length();
        }

        String visibleText = fullText.substring(Math.min(firstChar, fullText.length()));
        String trimmed = this.textRenderer.trimToWidth(visibleText, relX);
        int charOffset = trimmed.length();
        if (charOffset < visibleText.length()) {
            int w1 = this.textRenderer.getWidth(visibleText.substring(0, charOffset));
            int w2 = this.textRenderer.getWidth(visibleText.substring(0, charOffset + 1));
            if (relX - w1 > (w2 - w1) / 2) {
                charOffset++;
            }
        }
        return MathHelper.clamp(firstChar + charOffset, 0, fullText.length());
    }
}
