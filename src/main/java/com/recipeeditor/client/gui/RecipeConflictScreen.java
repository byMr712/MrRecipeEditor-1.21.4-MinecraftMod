package com.recipeeditor.client.gui;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeTypeEnum;
import com.recipeeditor.inspector.RecipeConflictInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class RecipeConflictScreen extends Screen {
    private final Screen parent;
    private final List<RecipeConflictInfo> conflicts;
    private final Runnable onConfirmOverride;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    public RecipeConflictScreen(Screen parent, List<RecipeConflictInfo> conflicts, Runnable onConfirmOverride) {
        super(Component.translatable("recipeeditor.gui.conflict_title"));
        this.parent = parent;
        this.conflicts = conflicts;
        this.onConfirmOverride = onConfirmOverride;
    }

    public RecipeConflictScreen(Screen parent, List<RecipeConflictInfo> conflicts) {
        this(parent, conflicts, null);
    }

    @Override
    protected void init() {
        int btnY = this.height - 32;

        if (onConfirmOverride != null) {
            int btnW = 150;
            int spacing = 10;
            int startX = this.width / 2 - btnW - (spacing / 2);

            this.addRenderableWidget(Button.builder(
                    Component.translatable("recipeeditor.gui.conflict_override").withStyle(ChatFormatting.GOLD),
                    btn -> {
                        this.onClose();
                        if (onConfirmOverride != null) {
                            onConfirmOverride.run();
                        }
                    })
                    .bounds(startX, btnY, btnW, 20)
                    .build());

            this.addRenderableWidget(Button.builder(
                    Component.translatable("recipeeditor.gui.conflict_cancel"),
                    btn -> this.onClose())
                    .bounds(this.width / 2 + (spacing / 2), btnY, btnW, 20)
                    .build());
        } else {
            int btnW = 150;
            this.addRenderableWidget(Button.builder(
                    Component.translatable("recipeeditor.gui.conflict_dismiss"),
                    btn -> this.onClose())
                    .bounds((this.width - btnW) / 2, btnY, btnW, 20)
                    .build());
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        DrawContext context = new DrawContext(graphics);
        renderContent(context, mouseX, mouseY, delta);
    }

    public void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        Component titleText = Component.literal("⚠️ ").append(Component.translatable("recipeeditor.gui.conflict_title")).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        context.drawCenteredTextWithShadow(this.font, titleText, this.width / 2, 14, 0xFFFFAA00);

        Component desc = Component.translatable("recipeeditor.gui.conflict_desc").withStyle(ChatFormatting.GRAY);
        context.drawCenteredTextWithShadow(this.font, desc, this.width / 2, 28, 0xFFAAAAAA);

        int listTop = 44;
        int btnY = this.height - 32;
        int listBottom = btnY - 8;
        int listH = Math.max(20, listBottom - listTop);
        int listW = Math.min(460, this.width - 32);
        int listX = (this.width - listW) / 2;

        context.fill(listX, listTop, listX + listW, listBottom, 0x44000000);
        drawBorder(context, listX, listTop, listW, listH, 0x33FFFFFF);

        context.enableScissor(listX + 1, listTop + 1, listX + listW - 1, listBottom - 1);

        int totalContentHeight = 0;
        int currentY = listTop + 4 - scrollOffset;
        ItemStack hoveredStack = ItemStack.EMPTY;

        for (int i = 0; i < conflicts.size(); i++) {
            RecipeConflictInfo info = conflicts.get(i);
            CustomRecipeData rec = info.attemptedRecipe;
            RecipeTypeEnum wsType = info.workstationType;

            int entryStartY = currentY;
            int cardH = (wsType == RecipeTypeEnum.SHAPED_CRAFTING) ? 78 : 50;

            if (entryStartY + cardH >= listTop && entryStartY <= listBottom) {
                context.fill(listX + 4, entryStartY, listX + listW - 8, entryStartY + cardH - 4, 0x44222222);
                drawBorder(context, listX + 4, entryStartY, listW - 12, cardH - 4, 0x33FFFFFF);

                int gridX = listX + 10;
                int gridY = entryStartY + 4;

                Component wsText = wsType.getDisplayName().copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
                context.drawTextWithShadow(this.font, wsText, gridX, gridY, 0xFFFFAA00);

                int slotGridY = gridY + 11;
                int arrowX;
                int resX;

                if (wsType == RecipeTypeEnum.SMELTING || wsType == RecipeTypeEnum.BLASTING ||
                    wsType == RecipeTypeEnum.SMOKING || wsType == RecipeTypeEnum.STONECUTTING ||
                    wsType == RecipeTypeEnum.CAMPFIRE_COOKING) {
                    drawMiniSlot(context, gridX, slotGridY);
                    if (rec != null) {
                        Item inItem = rec.getItemAt(0);
                        if (inItem != Items.AIR) {
                            ItemStack st = new ItemStack(inItem);
                            context.drawItem(st, gridX + 1, slotGridY + 1);
                            if (isHovered(mouseX, mouseY, gridX, slotGridY, 18, 18, listTop, listH)) {
                                hoveredStack = st;
                            }
                        }
                    }
                    arrowX = gridX + 22;
                    resX = arrowX + 16;
                } else if (wsType == RecipeTypeEnum.SMITHING) {
                    for (int s = 0; s < 3; s++) {
                        int slotX = gridX + s * 19;
                        drawMiniSlot(context, slotX, slotGridY);
                        if (rec != null) {
                            Item inItem = rec.getItemAt(s);
                            if (inItem != Items.AIR) {
                                ItemStack st = new ItemStack(inItem);
                                context.drawItem(st, slotX + 1, slotGridY + 1);
                                if (isHovered(mouseX, mouseY, slotX, slotGridY, 18, 18, listTop, listH)) {
                                    hoveredStack = st;
                                }
                            }
                        }
                    }
                    arrowX = gridX + 3 * 19 + 4;
                    resX = arrowX + 16;
                } else {
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
                                    if (isHovered(mouseX, mouseY, slotX, sY, 18, 18, listTop, listH)) {
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

                context.drawTextWithShadow(this.font, Component.literal("➜").withStyle(ChatFormatting.GOLD), arrowX, arrowY, 0xFFFFAA00);
                drawMiniSlot(context, resX, resY);

                if (info.conflictingItem != null && info.conflictingItem != Items.AIR) {
                    ItemStack resSt = new ItemStack(info.conflictingItem);
                    context.drawItem(resSt, resX + 1, resY + 1);
                    context.drawStackOverlay(this.font, resSt, resX + 1, resY + 1);
                    if (isHovered(mouseX, mouseY, resX, resY, 18, 18, listTop, listH)) {
                        hoveredStack = resSt;
                    }
                }

                int textX = resX + 24;
                int textY = entryStartY + 6;
                int textW = listW - (textX - listX) - 16;

                if (textW > 40) {
                    Component usedByHeader = Component.translatable("recipeeditor.gui.conflict_used_by", info.getConflictingItemName().getString()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
                    List<FormattedCharSequence> wrappedUsedBy = this.font.split(usedByHeader, textW);
                    for (int l = 0; l < Math.min(2, wrappedUsedBy.size()); l++) {
                        context.drawTextWithShadow(this.font, wrappedUsedBy.get(l), textX, textY, 0xFFFF5555);
                        textY += 10;
                    }

                    if (info.conflictingRecipeId != null && !info.conflictingRecipeId.isEmpty()) {
                        Component idText = Component.literal("ID: " + info.conflictingRecipeId).withStyle(ChatFormatting.DARK_GRAY);
                        List<FormattedCharSequence> wrappedId = this.font.split(idText, textW);
                        if (!wrappedId.isEmpty()) {
                            context.drawTextWithShadow(this.font, wrappedId.get(0), textX, textY, 0xFF888888);
                            textY += 10;
                        }
                    }

                    Component sourceText = Component.translatable("recipeeditor.gui.conflict_source", info.sourceName).withStyle(ChatFormatting.YELLOW);
                    List<FormattedCharSequence> wrappedSource = this.font.split(sourceText, textW);
                    for (int l = 0; l < Math.min(2, wrappedSource.size()); l++) {
                        context.drawTextWithShadow(this.font, wrappedSource.get(l), textX, textY, 0xFFFFAA00);
                        textY += 10;
                    }
                }
            }

            currentY += cardH;
            totalContentHeight += cardH;
        }

        context.disableScissor();

        maxScroll = Math.max(0, totalContentHeight - listH + 8);

        if (maxScroll > 0) {
            int scrollbarH = Math.max(16, (int) ((float) listH / (float) totalContentHeight * listH));
            int scrollbarY = listTop + (int) ((float) scrollOffset / (float) maxScroll * (listH - scrollbarH));
            int scrollbarX = listX + listW - 6;
            context.fill(scrollbarX, listTop + 2, scrollbarX + 3, listBottom - 2, 0x44000000);
            context.fill(scrollbarX, scrollbarY, scrollbarX + 3, scrollbarY + scrollbarH, 0xAAFFFFFF);
        }

        if (!hoveredStack.isEmpty()) {
            context.drawItemTooltip(this.font, hoveredStack, mouseX, mouseY);
        }
    }

    private boolean isHovered(int mouseX, int mouseY, int x, int y, int w, int h, int listTop, int listH) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h && mouseY >= listTop && mouseY <= listTop + listH;
    }

    private void drawMiniSlot(DrawContext context, int x, int y) {
        context.fill(x, y, x + 18, y + 18, 0x99000000);
        drawBorder(context, x, y, 18, 18, 0xFF555555);
    }

    private static void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
