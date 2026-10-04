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
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class RecipeConflictScreen extends Screen {
    private final Screen parent;
    private final List<RecipeConflictInfo> conflicts;
    private final Runnable onConfirmOverride;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int dialogWidth = 420;
    private int dialogHeight = 260;

    public RecipeConflictScreen(Screen parent, List<RecipeConflictInfo> conflicts, Runnable onConfirmOverride) {
        super(Text.translatable("recipeeditor.gui.conflict_title"));
        this.parent = parent;
        this.conflicts = conflicts;
        this.onConfirmOverride = onConfirmOverride;
    }

    public RecipeConflictScreen(Screen parent, List<RecipeConflictInfo> conflicts) {
        this(parent, conflicts, null);
    }

    @Override
    protected void init() {
        dialogWidth = Math.min(430, Math.max(300, this.width - 24));
        dialogHeight = Math.min(270, Math.max(180, this.height - 24));
        int dialogX = (this.width - dialogWidth) / 2;
        int dialogY = (this.height - dialogHeight) / 2;

        int btnY = dialogY + dialogHeight - 24;

        if (onConfirmOverride != null) {
            int overrideBtnW = 140;
            int cancelBtnW = 90;
            int totalBtnW = overrideBtnW + 8 + cancelBtnW;
            int startBtnX = dialogX + (dialogWidth - totalBtnW) / 2;

            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("recipeeditor.gui.conflict_override").formatted(Formatting.GOLD),
                    btn -> {
                        this.close();
                        if (onConfirmOverride != null) {
                            onConfirmOverride.run();
                        }
                    })
                    .dimensions(startBtnX, btnY, overrideBtnW, 18)
                    .build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("recipeeditor.gui.conflict_cancel"),
                    btn -> this.close())
                    .dimensions(startBtnX + overrideBtnW + 8, btnY, cancelBtnW, 18)
                    .build());
        } else {
            int btnWidth = 110;
            int btnX = dialogX + (dialogWidth - btnWidth) / 2;
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("recipeeditor.gui.conflict_dismiss"),
                    btn -> this.close())
                    .dimensions(btnX, btnY, btnWidth, 18)
                    .build());
        }
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
        // Render parent screen underneath if present
        if (this.parent != null) {
            this.parent.render(context, -1, -1, delta);
        }

        // Elegant semi-transparent dim overlay
        context.fill(0, 0, this.width, this.height, 0xB0000000);

        int dialogX = (this.width - dialogWidth) / 2;
        int dialogY = (this.height - dialogHeight) / 2;

        // Dialog Panel Background & Borders
        context.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xF5181818);
        context.drawBorder(dialogX, dialogY, dialogWidth, dialogHeight, 0xFF4A4A4A);

        // Header Banner
        context.fill(dialogX + 1, dialogY + 1, dialogX + dialogWidth - 1, dialogY + 30, 0xFF242424);
        context.fill(dialogX + 1, dialogY + 29, dialogX + dialogWidth - 1, dialogY + 30, 0xFFE08020);

        // Header Title
        Text titleText = Text.literal("⚠️ ").append(Text.translatable("recipeeditor.gui.conflict_title")).formatted(Formatting.GOLD, Formatting.BOLD);
        context.drawCenteredTextWithShadow(this.textRenderer, titleText, this.width / 2, dialogY + 6, 0xFFFFAA00);

        // Subtitle
        Text desc = Text.translatable("recipeeditor.gui.conflict_desc").formatted(Formatting.GRAY);
        context.drawCenteredTextWithShadow(this.textRenderer, desc, this.width / 2, dialogY + 18, 0xFFAAAAAA);

        // List Viewport bounds
        int listX = dialogX + 10;
        int listY = dialogY + 34;
        int listW = dialogWidth - 20;
        int listH = dialogHeight - 64;

        context.enableScissor(listX, listY, listX + listW, listY + listH);

        int totalContentHeight = 0;
        int currentY = listY - scrollOffset;
        ItemStack hoveredStack = ItemStack.EMPTY;

        for (int i = 0; i < conflicts.size(); i++) {
            RecipeConflictInfo info = conflicts.get(i);
            CustomRecipeData rec = info.attemptedRecipe;
            RecipeTypeEnum wsType = info.workstationType;

            int entryStartY = currentY;
            int cardH = (wsType == RecipeTypeEnum.SHAPED_CRAFTING) ? 78 : 50;

            // Card background
            context.fill(listX + 2, entryStartY, listX + listW - 4, entryStartY + cardH - 4, 0x44222222);
            context.drawBorder(listX + 2, entryStartY, listW - 6, cardH - 4, 0x33FFFFFF);

            // Left Side: Grid & Station
            int gridX = listX + 8;
            int gridY = entryStartY + 4;

            Text wsText = wsType.getDisplayName().copy().formatted(Formatting.YELLOW, Formatting.BOLD);
            context.drawTextWithShadow(this.textRenderer, wsText, gridX, gridY, 0xFFFFAA00);

            int slotGridY = gridY + 11;
            int arrowX;
            int resX;

            if (wsType == RecipeTypeEnum.SMELTING || wsType == RecipeTypeEnum.BLASTING ||
                wsType == RecipeTypeEnum.SMOKING || wsType == RecipeTypeEnum.STONECUTTING ||
                wsType == RecipeTypeEnum.CAMPFIRE_COOKING) {
                // 1 Slot
                drawMiniSlot(context, gridX, slotGridY);
                if (rec != null) {
                    Item inItem = rec.getItemAt(0);
                    if (inItem != Items.AIR) {
                        ItemStack st = new ItemStack(inItem);
                        context.drawItem(st, gridX + 1, slotGridY + 1);
                        if (isHovered(mouseX, mouseY, gridX, slotGridY, 18, 18, listY, listH)) {
                            hoveredStack = st;
                        }
                    }
                }
                arrowX = gridX + 22;
                resX = arrowX + 16;
            } else if (wsType == RecipeTypeEnum.SMITHING) {
                // 3 Slots
                for (int s = 0; s < 3; s++) {
                    int slotX = gridX + s * 19;
                    drawMiniSlot(context, slotX, slotGridY);
                    if (rec != null) {
                        Item inItem = rec.getItemAt(s);
                        if (inItem != Items.AIR) {
                            ItemStack st = new ItemStack(inItem);
                            context.drawItem(st, slotX + 1, slotGridY + 1);
                            if (isHovered(mouseX, mouseY, slotX, slotGridY, 18, 18, listY, listH)) {
                                hoveredStack = st;
                            }
                        }
                    }
                }
                arrowX = gridX + 3 * 19 + 4;
                resX = arrowX + 16;
            } else {
                // 3x3 Grid (compact 18px per slot)
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        int slotIdx = r * 3 + c;
                        int slotX = gridX + c * 18;
                        int sY = slotGridY + r * 18;
                        drawMiniSlot(context, slotX, sY);
                        if (rec != null) {
                            Item inItem = rec.getItemAt(slotIdx);
                            if (inItem != Items.AIR) {
                                ItemStack st = new ItemStack(inItem);
                                context.drawItem(st, slotX + 1, sY + 1);
                                if (isHovered(mouseX, mouseY, slotX, sY, 18, 18, listY, listH)) {
                                    hoveredStack = st;
                                }
                            }
                        }
                    }
                }
                arrowX = gridX + 3 * 18 + 4;
                resX = arrowX + 16;
            }

            int arrowY = (wsType == RecipeTypeEnum.SHAPED_CRAFTING) ? (slotGridY + 18) : (slotGridY + 4);
            int resY = (wsType == RecipeTypeEnum.SHAPED_CRAFTING) ? (slotGridY + 17) : slotGridY;

            context.drawTextWithShadow(this.textRenderer, Text.literal("➜").formatted(Formatting.GOLD), arrowX, arrowY, 0xFFFFAA00);
            drawMiniSlot(context, resX, resY);

            if (info.conflictingItem != null && info.conflictingItem != Items.AIR) {
                ItemStack resSt = new ItemStack(info.conflictingItem);
                context.drawItem(resSt, resX + 1, resY + 1);
                context.drawStackOverlay(this.textRenderer, resSt, resX + 1, resY + 1);
                if (isHovered(mouseX, mouseY, resX, resY, 18, 18, listY, listH)) {
                    hoveredStack = resSt;
                }
            }

            // Right Side: Conflict Details with multi-line text wrapping
            int textX = resX + 24;
            int textY = entryStartY + 6;
            int textW = listW - (textX - listX) - 10;

            if (textW > 40) {
                Text usedByHeader = Text.translatable("recipeeditor.gui.conflict_used_by", info.getConflictingItemName().getString()).formatted(Formatting.WHITE, Formatting.BOLD);
                List<OrderedText> wrappedUsedBy = this.textRenderer.wrapLines(usedByHeader, textW);
                for (int l = 0; l < Math.min(2, wrappedUsedBy.size()); l++) {
                    context.drawTextWithShadow(this.textRenderer, wrappedUsedBy.get(l), textX, textY, 0xFFFF5555);
                    textY += 10;
                }

                if (info.conflictingRecipeId != null && !info.conflictingRecipeId.isEmpty()) {
                    Text idText = Text.literal("ID: " + info.conflictingRecipeId).formatted(Formatting.DARK_GRAY);
                    List<OrderedText> wrappedId = this.textRenderer.wrapLines(idText, textW);
                    if (!wrappedId.isEmpty()) {
                        context.drawTextWithShadow(this.textRenderer, wrappedId.get(0), textX, textY, 0xFF888888);
                        textY += 10;
                    }
                }

                Text sourceText = Text.translatable("recipeeditor.gui.conflict_source", info.sourceName).formatted(Formatting.YELLOW);
                List<OrderedText> wrappedSource = this.textRenderer.wrapLines(sourceText, textW);
                for (int l = 0; l < Math.min(2, wrappedSource.size()); l++) {
                    context.drawTextWithShadow(this.textRenderer, wrappedSource.get(l), textX, textY, 0xFFFFAA00);
                    textY += 10;
                }
            }

            currentY += cardH;
            totalContentHeight += cardH;
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

        // Item Tooltips
        if (!hoveredStack.isEmpty()) {
            context.drawItemTooltip(this.textRenderer, hoveredStack, mouseX, mouseY);
        }
    }

    private boolean isHovered(int mouseX, int mouseY, int x, int y, int w, int h, int listY, int listH) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h && mouseY >= listY && mouseY <= listY + listH;
    }

    private void drawMiniSlot(DrawContext context, int x, int y) {
        context.fill(x, y, x + 18, y + 18, 0x99000000);
        context.drawBorder(x, y, 18, 18, 0xFF555555);
    }
}
