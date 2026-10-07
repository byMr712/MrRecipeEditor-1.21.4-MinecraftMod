package com.recipeeditor.client.gui;

import com.recipeeditor.mixin.client.TextFieldWidgetAccessor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;

public class SelectableTextFieldWidget extends EditBox {
    private final Font font;
    private int dragAnchor = -1;
    private boolean isDraggingSelection = false;
    private long lastClickTime = 0L;
    private double lastClickX = -1;
    private double lastClickY = -1;

    public SelectableTextFieldWidget(Font font, int x, int y, int width, int height, Component text) {
        super(font, x, y, width, height, text);
        this.font = font;
    }

    public String getText() {
        return getValue();
    }

    public void setText(String text) {
        setValue(text);
    }

    public void setPlaceholder(Component hint) {
        setHint(hint);
    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setChangedListener(java.util.function.Consumer<String> responder) {
        setResponder(responder);
    }

    public boolean drawsBackground() {
        return isBordered();
    }

    public void setDrawsBackground(boolean draws) {
        setBordered(draws);
    }

    public int getCursor() {
        return getCursorPosition();
    }

    public void setCursor(int pos, boolean shiftKeyDown) {
        setCursorPosition(pos);
    }

    public void setSelectionEnd(int pos) {
        setHighlightPos(pos);
    }

    public void setCursorToStart(boolean shiftKeyDown) {
        moveCursorToStart(shiftKeyDown);
    }

    public void setCursorToEnd(boolean shiftKeyDown) {
        moveCursorToEnd(shiftKeyDown);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean result = super.mouseClicked(event, doubleClick);
        if (result && event.button() == 0) {
            long now = System.currentTimeMillis();
            if (now - lastClickTime < 300L && Math.abs(event.x() - lastClickX) < 5.0 && Math.abs(event.y() - lastClickY) < 5.0) {
                this.setCursorToStart(false);
                this.setSelectionEnd(this.getValue().length());
                this.dragAnchor = 0;
                this.isDraggingSelection = false;
                this.lastClickTime = 0L;
                return true;
            }
            this.lastClickTime = now;
            this.lastClickX = event.x();
            this.lastClickY = event.y();
            this.dragAnchor = this.getCursorPosition();
            this.isDraggingSelection = true;
        } else if (!this.isFocused()) {
            this.dragAnchor = -1;
            this.isDraggingSelection = false;
        }
        return result;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (handleMouseDragged(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    public boolean handleMouseDragged(double mouseX, double mouseY, int button) {
        if (button != 0 || !this.isFocused() || !this.isDraggingSelection || this.dragAnchor < 0) {
            return false;
        }
        int newCursor = getCharIndexAt(mouseX);
        this.setCursorPosition(newCursor);
        this.setHighlightPos(this.dragAnchor);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            this.isDraggingSelection = false;
        }
        return super.mouseReleased(event);
    }

    public void handleMouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDraggingSelection = false;
        }
    }

    public int getCharIndexAt(double mouseX) {
        String fullText = this.getValue();
        if (fullText.isEmpty()) {
            return 0;
        }
        int firstChar = 0;
        try {
            firstChar = ((TextFieldWidgetAccessor) this).getDisplayPos();
        } catch (Throwable ignored) {}

        int startX = this.getX() + (this.isBordered() ? 4 : 0);
        int relX = (int) Math.round(mouseX) - startX;

        if (relX <= 0) {
            return mouseX < this.getX() ? 0 : firstChar;
        }

        int innerWidth = this.getInnerWidth();
        String visibleText = fullText.substring(firstChar);
        String trimmed = this.font.plainSubstrByWidth(visibleText, innerWidth);

        int currentWidth = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            int charW = this.font.width(String.valueOf(trimmed.charAt(i)));
            if (relX < currentWidth + charW / 2) {
                return firstChar + i;
            }
            currentWidth += charW;
        }

        return firstChar + trimmed.length();
    }
}
