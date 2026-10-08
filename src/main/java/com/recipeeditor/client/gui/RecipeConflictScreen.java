package com.recipeeditor.client.gui;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeTypeEnum;
import com.recipeeditor.inspector.RecipeConflictInfo;
import net.minecraft.client.gui.Click;
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

    // Responsive Studio scaling matching RecipeEditorScreen
    private float uiScale = 1.0f;
    private static final int BASE_WIDTH = 460;
    private static final int BASE_HEIGHT = 320;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean isDraggingScroll = false;
    private double dragScrollStartY = 0;
    private int dragScrollStartOffset = 0;

    public RecipeConflictScreen(Screen parent, List<RecipeConflictInfo> conflicts, Runnable onConfirmOverride) {
        super(Text.translatable("recipeeditor.gui.conflict_title"));
        this.parent = parent;
        this.conflicts = conflicts != null ? conflicts : List.of();
        this.onConfirmOverride = onConfirmOverride;
    }

    public RecipeConflictScreen(Screen parent, List<RecipeConflictInfo> conflicts) {
        this(parent, conflicts, null);
    }

    private void updateUiScale() {
        float scaleX = (float) this.width / (float) BASE_WIDTH;
        float scaleY = (float) this.height / (float) BASE_HEIGHT;
        this.uiScale = Math.min(1.0f, Math.min(scaleX, scaleY));
        if (this.uiScale <= 0.05f) {
            this.uiScale = 1.0f;
        }
    }

    public int getVirtualWidth() {
        return (int) Math.ceil(this.width / uiScale);
    }

    public int getVirtualHeight() {
        return (int) Math.ceil(this.height / uiScale);
    }

    @Override
    protected void init() {
        updateUiScale();
        int vWidth = getVirtualWidth();
        int vHeight = getVirtualHeight();

        int btnY = vHeight - 28;
        int btnW = 140;
        int spacing = 12;

        if (onConfirmOverride != null) {
            int startX = (vWidth - (btnW * 2 + spacing)) / 2;

            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("recipeeditor.gui.conflict_override").formatted(Formatting.GOLD),
                    btn -> {
                        this.close();
                        if (onConfirmOverride != null) {
                            onConfirmOverride.run();
                        }
                    })
                    .dimensions(startX, btnY, btnW, 20)
                    .build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("recipeeditor.gui.conflict_cancel"),
                    btn -> this.close())
                    .dimensions(startX + btnW + spacing, btnY, btnW, 20)
                    .build());
        } else {
            int startX = (vWidth - btnW) / 2;
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("recipeeditor.gui.conflict_dismiss"),
                    btn -> this.close())
                    .dimensions(startX, btnY, btnW, 20)
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
        double sX = mouseX / uiScale;
        double sY = mouseY / uiScale;

        if (maxScroll > 0) {
            if (verticalAmount > 0) {
                scrollOffset = Math.max(0, scrollOffset - 24);
                return true;
            } else if (verticalAmount < 0) {
                scrollOffset = Math.min(maxScroll, scrollOffset + 24);
                return true;
            }
        }
        return super.mouseScrolled(sX, sY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        double sX = click.x() / uiScale;
        double sY = click.y() / uiScale;
        int button = click.button();
        Click scaledClick = new Click(sX, sY, click.buttonInfo());

        if (button == 0 && maxScroll > 0) {
            int vWidth = getVirtualWidth();
            int vHeight = getVirtualHeight();
            int listTop = 40;
            int btnY = vHeight - 28;
            int listBottom = btnY - 6;
            int listW = Math.min(440, vWidth - 24);
            int listX = (vWidth - listW) / 2;
            int scrollbarX = listX + listW - 8;

            if (sX >= scrollbarX - 4 && sX <= scrollbarX + 10 && sY >= listTop && sY <= listBottom) {
                isDraggingScroll = true;
                dragScrollStartY = sY;
                dragScrollStartOffset = scrollOffset;
                return true;
            }
        }
        return super.mouseClicked(scaledClick, bl);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double sX = click.x() / uiScale;
        double sY = click.y() / uiScale;
        Click scaledClick = new Click(sX, sY, click.buttonInfo());
        isDraggingScroll = false;
        return super.mouseReleased(scaledClick);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double sX = click.x() / uiScale;
        double sY = click.y() / uiScale;
        int button = click.button();
        Click scaledClick = new Click(sX, sY, click.buttonInfo());

        if (isDraggingScroll && button == 0 && maxScroll > 0) {
            int vHeight = getVirtualHeight();
            int listTop = 40;
            int btnY = vHeight - 28;
            int listBottom = btnY - 6;
            int listH = Math.max(20, listBottom - listTop);
            int totalContentHeight = maxScroll + listH - 8;
            int scrollbarH = Math.max(16, (int) ((float) listH / (float) totalContentHeight * listH));
            int trackH = listH - scrollbarH;
            if (trackH > 0) {
                double delta = sY - dragScrollStartY;
                scrollOffset = Math.max(0, Math.min(maxScroll, dragScrollStartOffset + (int) (delta / trackH * maxScroll)));
            }
            return true;
        }
        return super.mouseDragged(scaledClick, deltaX / uiScale, deltaY / uiScale);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateUiScale();
        int vWidth = getVirtualWidth();
        int vHeight = getVirtualHeight();

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(uiScale, uiScale);

        int scaledMouseX = (int) (mouseX / uiScale);
        int scaledMouseY = (int) (mouseY / uiScale);

        // Dark dim background
        context.fill(0, 0, vWidth, vHeight, 0xCC101010);

        // Header
        Text titleText = Text.literal("\u26A0 " + Text.translatable("recipeeditor.gui.conflict_title").getString() + " \u26A0").formatted(Formatting.RED, Formatting.BOLD);
        context.drawCenteredTextWithShadow(this.textRenderer, titleText, vWidth / 2, 12, 0xFFFF5555);

        Text desc = Text.translatable("recipeeditor.gui.conflict_desc").formatted(Formatting.GRAY);
        context.drawCenteredTextWithShadow(this.textRenderer, desc, vWidth / 2, 25, 0xFFAAAAAA);

        int listTop = 40;
        int btnY = vHeight - 28;
        int listBottom = btnY - 6;
        int listH = Math.max(20, listBottom - listTop);
        int listW = Math.min(440, vWidth - 24);
        int listX = (vWidth - listW) / 2;

        // Conflict container background panel
        context.fill(listX, listTop, listX + listW, listBottom, 0x44000000);
        drawBorder(context, listX, listTop, listW, listH, 0x33FFFFFF);

        renderConflictList(context, scaledMouseX, scaledMouseY, listX, listTop, listW, listH, listBottom);

        super.render(context, scaledMouseX, scaledMouseY, delta);

        context.getMatrices().popMatrix();
    }

    private void renderConflictList(DrawContext context, int mouseX, int mouseY, int listX, int listTop, int listW, int listH, int listBottom) {
        if (listW <= 2 || listH <= 2) return;

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
                // Card background
                context.fill(listX + 4, entryStartY, listX + listW - 8, entryStartY + cardH - 4, 0x44222222);
                drawBorder(context, listX + 4, entryStartY, listW - 12, cardH - 4, 0x33FFFFFF);

                // Left Side: Grid & Station
                int gridX = listX + 10;
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
                            if (isHovered(mouseX, mouseY, gridX, slotGridY, 18, 18, listTop, listH)) {
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
                                if (isHovered(mouseX, mouseY, slotX, slotGridY, 18, 18, listTop, listH)) {
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

                context.drawTextWithShadow(this.textRenderer, Text.literal("➜").formatted(Formatting.GOLD), arrowX, arrowY, 0xFFFFAA00);
                drawMiniSlot(context, resX, resY);

                if (info.conflictingItem != null && info.conflictingItem != Items.AIR) {
                    ItemStack resSt = new ItemStack(info.conflictingItem);
                    context.drawItem(resSt, resX + 1, resY + 1);
                    context.drawStackOverlay(this.textRenderer, resSt, resX + 1, resY + 1);
                    if (isHovered(mouseX, mouseY, resX, resY, 18, 18, listTop, listH)) {
                        hoveredStack = resSt;
                    }
                }

                // Right Side: Conflict Details with multi-line text wrapping
                int textX = resX + 24;
                int textY = entryStartY + 6;
                int textW = listW - (textX - listX) - 16;

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
            }

            currentY += cardH;
            totalContentHeight += cardH;
        }

        context.disableScissor();

        // Calculate max scroll
        maxScroll = Math.max(0, totalContentHeight - listH + 8);

        // Scrollbar
        if (maxScroll > 0) {
            int scrollbarH = Math.max(16, (int) ((float) listH / (float) totalContentHeight * listH));
            int scrollbarY = listTop + (int) ((float) scrollOffset / (float) maxScroll * (listH - scrollbarH));
            int scrollbarX = listX + listW - 6;
            context.fill(scrollbarX, listTop + 2, scrollbarX + 3, listBottom - 2, 0x44000000);
            context.fill(scrollbarX, scrollbarY, scrollbarX + 3, scrollbarY + scrollbarH, 0xAAFFFFFF);
        }

        // Item Tooltips
        if (!hoveredStack.isEmpty()) {
            context.drawItemTooltip(this.textRenderer, hoveredStack, mouseX, mouseY);
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
