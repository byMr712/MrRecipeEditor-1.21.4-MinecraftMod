package com.recipeeditor.client.gui;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeTypeEnum;
import com.recipeeditor.inspector.RecipeConflictInfo;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class RecipeConflictScreen extends Screen {
    private final Screen parent;
    private final List<RecipeConflictInfo> conflicts;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int dialogWidth = 340;
    private int dialogHeight = 230;

    public RecipeConflictScreen(Screen parent, List<RecipeConflictInfo> conflicts) {
        super(Text.translatable("recipeeditor.gui.conflict_title"));
        this.parent = parent;
        this.conflicts = conflicts;
    }

    @Override
    protected void init() {
        dialogWidth = Math.min(340, Math.max(260, this.width - 24));
        dialogHeight = Math.min(230, Math.max(160, this.height - 24));
        int dialogX = (this.width - dialogWidth) / 2;
        int dialogY = (this.height - dialogHeight) / 2;
        int btnWidth = Math.min(100, dialogWidth - 30);
        int btnX = dialogX + (dialogWidth - btnWidth) / 2;
        int btnY = dialogY + dialogHeight - 24;

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("recipeeditor.gui.conflict_dismiss"), btn -> this.close())
                .dimensions(btnX, btnY, btnWidth, 18)
                .build());
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0) {
            if (verticalAmount > 0) {
                scrollOffset = Math.max(0, scrollOffset - 24);
                return true;
            } else if (verticalAmount < 0) {
                scrollOffset = Math.min(maxScroll, scrollOffset + 24);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Semi-transparent background dim
        context.fill(0, 0, this.width, this.height, 0xAA000000);

        int dialogX = (this.width - dialogWidth) / 2;
        int dialogY = (this.height - dialogHeight) / 2;

        // Dialog Box Background & Border
        context.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xF0151515);
        context.drawBorder(dialogX, dialogY, dialogWidth, dialogHeight, 0xFFFF3333);

        // Red Bold Title
        Text title = Text.translatable("recipeeditor.gui.conflict_title").formatted(Formatting.RED, Formatting.BOLD);
        context.drawCenteredTextWithShadow(this.textRenderer, title, this.width / 2, dialogY + 8, 0xFFFF3333);

        // Description text
        Text desc = Text.translatable("recipeeditor.gui.conflict_desc").formatted(Formatting.GRAY);
        context.drawCenteredTextWithShadow(this.textRenderer, desc, this.width / 2, dialogY + 22, 0xFFAAAAAA);

        // List Viewport
        int listX = dialogX + 12;
        int listY = dialogY + 36;
        int listW = dialogWidth - 24;
        int listH = dialogHeight - 64;

        // Enable Scissor for clean clipping
        context.enableScissor(listX, listY, listX + listW, listY + listH);

        int totalContentHeight = 0;
        int currentY = listY - scrollOffset;
        ItemStack hoveredStack = ItemStack.EMPTY;

        for (int i = 0; i < conflicts.size(); i++) {
            RecipeConflictInfo info = conflicts.get(i);
            CustomRecipeData rec = info.attemptedRecipe;
            RecipeTypeEnum wsType = info.workstationType;

            int entryStartY = currentY;

            // Header: "Место крафта: [Название станка]"
            Text wsText = Text.translatable("recipeeditor.gui.conflict_workstation", wsType.getDisplayName().getString()).formatted(Formatting.GOLD, Formatting.BOLD);
            context.drawTextWithShadow(this.textRenderer, wsText, listX + 4, entryStartY, 0xFFFFAA00);

            int gridY = entryStartY + 12;
            int arrowX;
            int resX;

            // Render mini-grid based on workstation type
            if (wsType == RecipeTypeEnum.SMELTING || wsType == RecipeTypeEnum.BLASTING ||
                wsType == RecipeTypeEnum.SMOKING || wsType == RecipeTypeEnum.STONECUTTING ||
                wsType == RecipeTypeEnum.CAMPFIRE_COOKING) {
                // 1 Slot
                int slotX = listX + 10;
                drawMiniSlot(context, slotX, gridY);
                if (rec != null) {
                    Item inItem = rec.getItemAt(0);
                    if (inItem != Items.AIR) {
                        ItemStack st = new ItemStack(inItem);
                        context.drawItem(st, slotX + 1, gridY + 1);
                        if (mouseX >= slotX && mouseX <= slotX + 18 && mouseY >= gridY && mouseY <= gridY + 18 && mouseY >= listY && mouseY <= listY + listH) {
                            hoveredStack = st;
                        }
                    }
                }
                arrowX = slotX + 24;
                resX = arrowX + 20;
            } else if (wsType == RecipeTypeEnum.SMITHING) {
                // 3 Slots
                int startSlotX = listX + 10;
                for (int s = 0; s < 3; s++) {
                    int slotX = startSlotX + s * 20;
                    drawMiniSlot(context, slotX, gridY);
                    if (rec != null) {
                        Item inItem = rec.getItemAt(s);
                        if (inItem != Items.AIR) {
                            ItemStack st = new ItemStack(inItem);
                            context.drawItem(st, slotX + 1, gridY + 1);
                            if (mouseX >= slotX && mouseX <= slotX + 18 && mouseY >= gridY && mouseY <= gridY + 18 && mouseY >= listY && mouseY <= listY + listH) {
                                hoveredStack = st;
                            }
                        }
                    }
                }
                arrowX = startSlotX + 64;
                resX = arrowX + 20;
            } else {
                // 3x3 Grid
                int startSlotX = listX + 10;
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        int slotIdx = r * 3 + c;
                        int slotX = startSlotX + c * 19;
                        int sY = gridY + r * 19;
                        drawMiniSlot(context, slotX, sY);
                        if (rec != null) {
                            Item inItem = rec.getItemAt(slotIdx);
                            if (inItem != Items.AIR) {
                                ItemStack st = new ItemStack(inItem);
                                context.drawItem(st, slotX + 1, sY + 1);
                                if (mouseX >= slotX && mouseX <= slotX + 18 && mouseY >= sY && mouseY <= sY + 18 && mouseY >= listY && mouseY <= listY + listH) {
                                    hoveredStack = st;
                                }
                            }
                        }
                    }
                }
                arrowX = startSlotX + 3 * 19 + 6;
                resX = arrowX + 20;
            }

            // Arrow & Conflicting Result item
            int arrowY = (wsType == RecipeTypeEnum.SHAPED_CRAFTING) ? (gridY + 19) : (gridY + 3);
            int resY = (wsType == RecipeTypeEnum.SHAPED_CRAFTING) ? (gridY + 18) : gridY;

            context.drawTextWithShadow(this.textRenderer, Text.literal("➡").formatted(Formatting.GOLD, Formatting.BOLD), arrowX, arrowY, 0xFFFFAA00);
            drawMiniSlot(context, resX, resY);

            if (info.conflictingItem != null && info.conflictingItem != Items.AIR) {
                ItemStack resSt = new ItemStack(info.conflictingItem);
                context.drawItem(resSt, resX + 1, resY + 1);
                context.drawStackOverlay(this.textRenderer, resSt, resX + 1, resY + 1);
                if (mouseX >= resX && mouseX <= resX + 18 && mouseY >= resY && mouseY <= resY + 18 && mouseY >= listY && mouseY <= listY + listH) {
                    hoveredStack = resSt;
                }
            }

            // Conflicting Info Details Text
            int textY = (wsType == RecipeTypeEnum.SHAPED_CRAFTING) ? (gridY + 60) : (gridY + 22);

            Text usedByText = Text.translatable("recipeeditor.gui.conflict_used_by", info.getConflictingItemName().getString()).formatted(Formatting.RED);
            context.drawTextWithShadow(this.textRenderer, usedByText, listX + 4, textY, 0xFFFF5555);

            Text sourceText = Text.translatable("recipeeditor.gui.conflict_source", info.sourceName).formatted(Formatting.YELLOW);
            context.drawTextWithShadow(this.textRenderer, sourceText, listX + 4, textY + 11, 0xFFFFAA00);

            int entryHeight = (wsType == RecipeTypeEnum.SHAPED_CRAFTING) ? 96 : 58;
            currentY += entryHeight;
            totalContentHeight += entryHeight;

            // Separator line between entries
            if (i < conflicts.size() - 1) {
                context.fill(listX + 4, currentY - 4, listX + listW - 8, currentY - 3, 0x44FFFFFF);
            }
        }

        context.disableScissor();

        // Calculate max scroll
        maxScroll = Math.max(0, totalContentHeight - listH);

        // Scrollbar
        if (maxScroll > 0) {
            int scrollbarH = Math.max(16, (int) ((float) listH / (float) totalContentHeight * listH));
            int scrollbarY = listY + (int) ((float) scrollOffset / (float) maxScroll * (listH - scrollbarH));
            int scrollbarX = listX + listW - 4;
            context.fill(scrollbarX, listY, scrollbarX + 3, listY + listH, 0x44000000);
            context.fill(scrollbarX, scrollbarY, scrollbarX + 3, scrollbarY + scrollbarH, 0xAAFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);

        // Tooltips
        if (!hoveredStack.isEmpty()) {
            context.drawItemTooltip(this.textRenderer, hoveredStack, mouseX, mouseY);
        }
    }

    private void drawMiniSlot(DrawContext context, int x, int y) {
        context.fill(x, y, x + 18, y + 18, 0x88000000);
        context.drawBorder(x, y, 18, 18, 0xFF444444);
    }
}
