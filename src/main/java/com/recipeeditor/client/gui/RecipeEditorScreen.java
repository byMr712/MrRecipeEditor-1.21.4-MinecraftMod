package com.recipeeditor.client.gui;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
import com.recipeeditor.exporter.DatapackExporter;
import com.recipeeditor.inspector.RecipeInspector;
import com.recipeeditor.inspector.RecipeStatus;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.*;

public class RecipeEditorScreen extends Screen {
    private final Screen parent;
    private final RecipeEditorConfig configCopy;

    // Currently edited recipe (starts clean and empty)
    private CustomRecipeData currentRecipe;
    private static final int RESULT_SLOT = 9;
    private int selectedSlot = 0; // 0..8 - crafting grid, 9 - result

    // Filter modes
    public enum CatalogFilter {
        ALL("recipeeditor.gui.filter_all"),
        UNCRAFTABLE("recipeeditor.gui.filter_uncraftable"),
        CUSTOM("recipeeditor.gui.filter_custom"),
        CRAFTABLE("recipeeditor.gui.filter_craftable");

        public final String translationKey;
        CatalogFilter(String translationKey) {
            this.translationKey = translationKey;
        }
    }
    private CatalogFilter currentFilter = CatalogFilter.ALL;

    // Mod Tabs
    public static class ModTab {
        public final String id;
        public final String displayName;

        public ModTab(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
    }

    private final List<ModTab> modTabs = new ArrayList<>();
    private int selectedTabIdx = 0;
    private int tabScrollOffset = 0;
    private int visibleTabs = 4;

    // Dynamic Item picker catalog
    private final List<Item> allItems = new ArrayList<>();
    private final List<Item> filteredItems = new ArrayList<>();
    private int catalogPage = 0;
    private int catalogCols = 10;
    private int catalogRows = 5;
    private int itemsPerPage = 50;

    // Layout coordinates
    private int leftPaneX;
    private int rightPaneX;
    private int contentY;
    private int catalogGridY;
    private int searchWidth;
    private static final int SLOT_SIZE = 24;
    private static final int LEFT_PANE_WIDTH = 200;

    // Widgets
    private TextFieldWidget searchField;
    private ButtonWidget prevPageBtn;
    private ButtonWidget nextPageBtn;
    private ButtonWidget toggleEnabledBtn;
    private ButtonWidget prevTabBtn;
    private ButtonWidget nextTabBtn;
    private final List<ButtonWidget> tabButtons = new ArrayList<>();
    private final List<ButtonWidget> filterButtons = new ArrayList<>();
    private final List<ButtonWidget> typeButtons = new ArrayList<>();
    private ButtonWidget loadVanillaBtn;
    private ButtonWidget deleteRecipeBtn;

    // Drag and drop state
    private Item draggedItem = null;
    private int dragSourceSlot = -1; // -1 for catalog, 0..8 for crafting slots, 9 for result
    private double dragStartX = 0;
    private double dragStartY = 0;

    // Status message notification
    private Text notificationText = null;
    private long notificationTimer = 0;

    public RecipeEditorScreen(Screen parent) {
        super(Text.translatable("recipeeditor.config.title"));
        this.parent = parent;
        this.configCopy = RecipeEditorConfig.getInstance().copy();

        // Always start with a fresh, empty craft canvas
        this.currentRecipe = new CustomRecipeData("", "minecraft:air", 1, RecipeTypeEnum.SHAPED_CRAFTING);

        // Gather all registered items
        Set<String> namespaces = new LinkedHashSet<>();
        for (Item item : Registries.ITEM) {
            if (item != Items.AIR) {
                allItems.add(item);
                Identifier id = Registries.ITEM.getId(item);
                if (id != null) {
                    namespaces.add(id.getNamespace());
                }
            }
        }

        // Build tabs: All -> Minecraft -> Other mods
        modTabs.add(new ModTab("all", "Все / All"));
        if (namespaces.remove("minecraft")) {
            modTabs.add(new ModTab("minecraft", "Minecraft"));
        }
        for (String ns : namespaces) {
            String modName = getFriendlyModName(ns);
            modTabs.add(new ModTab(ns, modName));
        }

        refreshFilteredItems();
    }

    private String getFriendlyModName(String namespace) {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(namespace);
        if (container.isPresent()) {
            return container.get().getMetadata().getName();
        }
        if (!namespace.isEmpty()) {
            return Character.toUpperCase(namespace.charAt(0)) + namespace.substring(1);
        }
        return namespace;
    }

    private void refreshFilteredItems() {
        filteredItems.clear();
        String currentTabId = modTabs.get(selectedTabIdx).id;
        String query = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        var world = this.client != null ? this.client.world : null;

        for (Item item : allItems) {
            Identifier id = Registries.ITEM.getId(item);
            if (id == null) continue;

            // Namespace filter
            if (!currentTabId.equals("all")) {
                if (!id.getNamespace().equalsIgnoreCase(currentTabId)) {
                    continue;
                }
            }

            // Smart Status Filter ("Мои крафты" / "Без крафта" / "С крафтом")
            if (currentFilter == CatalogFilter.UNCRAFTABLE) {
                if (RecipeInspector.getStatus(item, world, configCopy) != RecipeStatus.UNCRAFTABLE) {
                    continue;
                }
            } else if (currentFilter == CatalogFilter.CUSTOM) {
                if (!configCopy.hasCustomRecipe(item)) {
                    continue;
                }
            } else if (currentFilter == CatalogFilter.CRAFTABLE) {
                if (!RecipeInspector.hasExistingRecipe(item, world)) {
                    continue;
                }
            }

            // Search query filter (matches item name or raw id)
            if (!query.isEmpty()) {
                String idStr = id.toString().toLowerCase(Locale.ROOT);
                String nameStr = item.getName().getString().toLowerCase(Locale.ROOT);
                if (!idStr.contains(query) && !nameStr.contains(query)) {
                    continue;
                }
            }

            filteredItems.add(item);
        }

        catalogPage = 0;
        updatePaginationButtons();
    }

    public void selectTargetItem(Item item) {
        if (item == null || item == Items.AIR) return;
        Identifier id = Registries.ITEM.getId(item);
        if (id == null) return;

        if (configCopy.hasCustomRecipe(item)) {
            currentRecipe = configCopy.getRecipeFor(item);
        } else {
            currentRecipe = new CustomRecipeData(id.getPath(), id.toString(), 1, RecipeTypeEnum.SHAPED_CRAFTING);
        }
        rebuildTypeButtons();
        updateButtonStates();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int topY = 20;

        contentY = topY + 28;
        int bottomY = this.height - 24;

        // Calculate dynamic dimensions
        int availableTotalWidth = Math.max(320, this.width - 24);
        int availableRightWidth = Math.max(120, availableTotalWidth - LEFT_PANE_WIDTH - 16);
        catalogCols = Math.max(4, availableRightWidth / SLOT_SIZE);

        int actualCatalogWidth = catalogCols * SLOT_SIZE;
        searchWidth = actualCatalogWidth - 2;

        int totalContentWidth = LEFT_PANE_WIDTH + 16 + actualCatalogWidth;
        leftPaneX = Math.max(8, (this.width - totalContentWidth) / 2);
        rightPaneX = leftPaneX + LEFT_PANE_WIDTH + 16;

        // Dynamic Rows Calculation
        int filterY = contentY;
        int tabY = filterY + 18;
        int searchY = tabY + 18;
        catalogGridY = searchY + 22;
        int availableCatalogHeight = Math.max(SLOT_SIZE * 3, (bottomY - 26) - catalogGridY);
        catalogRows = Math.max(3, availableCatalogHeight / SLOT_SIZE);
        itemsPerPage = catalogCols * catalogRows;

        visibleTabs = Math.max(2, (searchWidth - 44) / 58);

        // Top toggle button: Recipe Enabled / Disabled
        updateToggleBtn(leftPaneX, topY);

        // --- LEFT PANE (Recipe Type Selector & Actions) ---
        rebuildTypeButtons();

        int actionBtnY = contentY + 116;

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("recipeeditor.gui.clear_slot"), btn -> clearSelectedSlot())
                .dimensions(leftPaneX, actionBtnY, 96, 18)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("recipeeditor.gui.fill_outer"), btn -> fillOuterWithSelected())
                .dimensions(leftPaneX + 100, actionBtnY, 96, 18)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("recipeeditor.gui.clear_all"), btn -> clearAllSlots())
                .dimensions(leftPaneX, actionBtnY + 22, 196, 18)
                .build());

        loadVanillaBtn = ButtonWidget.builder(Text.translatable("recipeeditor.gui.load_vanilla"), btn -> loadVanillaForCurrent())
                .dimensions(leftPaneX, actionBtnY + 44, 196, 18)
                .build();
        this.addDrawableChild(loadVanillaBtn);

        deleteRecipeBtn = ButtonWidget.builder(Text.translatable("recipeeditor.gui.delete_custom").formatted(Formatting.RED), btn -> deleteCurrentRecipe())
                .dimensions(leftPaneX, actionBtnY + 66, 196, 18)
                .build();
        this.addDrawableChild(deleteRecipeBtn);

        // Result count buttons
        int resultX = leftPaneX + 144;
        int resultY = contentY + 54;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("-"), btn -> adjustResultCount(-1))
                .dimensions(resultX - 3, resultY + 34, 18, 18)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), btn -> adjustResultCount(1))
                .dimensions(resultX + 19, resultY + 34, 18, 18)
                .build());

        // --- RIGHT PANE (Filters, Tabs, Search, Dynamic Catalog) ---
        rebuildFilterButtons(rightPaneX, filterY);

        prevTabBtn = ButtonWidget.builder(Text.literal("◀"), btn -> scrollTabs(-1))
                .dimensions(rightPaneX, tabY, 18, 16)
                .build();
        nextTabBtn = ButtonWidget.builder(Text.literal("▶"), btn -> scrollTabs(1))
                .dimensions(rightPaneX + searchWidth - 18, tabY, 18, 16)
                .build();
        this.addDrawableChild(prevTabBtn);
        this.addDrawableChild(nextTabBtn);
        rebuildTabButtons(rightPaneX + 22, tabY);

        // Search Field
        String previousSearch = searchField != null ? searchField.getText() : "";
        searchField = new TextFieldWidget(this.textRenderer, rightPaneX, searchY, searchWidth, 18, Text.literal("Search"));
        searchField.setText(previousSearch);
        searchField.setPlaceholder(Text.translatable("recipeeditor.gui.search_placeholder").formatted(Formatting.GRAY));
        searchField.setChangedListener(query -> refreshFilteredItems());
        this.addDrawableChild(searchField);

        // Catalog Pagination Buttons
        int pageBtnY = catalogGridY + (catalogRows * SLOT_SIZE) + 6;
        prevPageBtn = ButtonWidget.builder(Text.literal("◀"), btn -> changePage(-1))
                .dimensions(rightPaneX, pageBtnY, 24, 18)
                .build();
        nextPageBtn = ButtonWidget.builder(Text.literal("▶"), btn -> changePage(1))
                .dimensions(rightPaneX + searchWidth - 24, pageBtnY, 24, 18)
                .build();
        this.addDrawableChild(prevPageBtn);
        this.addDrawableChild(nextPageBtn);
        updatePaginationButtons();

        // --- BOTTOM PANE (Controls) ---
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("recipeeditor.gui.export_datapack"), btn -> exportDatapack())
                .dimensions(centerX - 210, bottomY, 110, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("recipeeditor.gui.reset_defaults"), btn -> resetDefaults())
                .dimensions(centerX - 95, bottomY, 90, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("recipeeditor.gui.save"), btn -> saveAndClose())
                .dimensions(centerX + 2, bottomY, 100, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("recipeeditor.gui.cancel"), btn -> this.close())
                .dimensions(centerX + 107, bottomY, 95, 20)
                .build());

        updateButtonStates();
    }

    private void updateButtonStates() {
        var world = this.client != null ? this.client.world : null;
        Item item = currentRecipe != null ? currentRecipe.getResultItem() : Items.AIR;
        if (loadVanillaBtn != null) {
            loadVanillaBtn.active = RecipeInspector.hasExistingRecipe(item, world);
        }
        if (deleteRecipeBtn != null) {
            deleteRecipeBtn.active = configCopy.hasCustomRecipe(item);
        }
    }

    private void rebuildFilterButtons(int startX, int startY) {
        for (ButtonWidget btn : filterButtons) {
            this.remove(btn);
        }
        filterButtons.clear();

        CatalogFilter[] filters = CatalogFilter.values();
        int btnWidth = (searchWidth - (filters.length - 1) * 2) / filters.length;

        for (int i = 0; i < filters.length; i++) {
            CatalogFilter f = filters[i];
            boolean isSelected = (f == currentFilter);
            Formatting color = isSelected ? Formatting.YELLOW : Formatting.GRAY;
            Text label = Text.translatable(f.translationKey).formatted(color);

            ButtonWidget btn = ButtonWidget.builder(label, b -> {
                currentFilter = f;
                refreshFilteredItems();
                rebuildFilterButtons(startX, startY);
            }).dimensions(startX + i * (btnWidth + 2), startY, btnWidth, 15).build();

            filterButtons.add(btn);
            this.addDrawableChild(btn);
        }
    }

    private void rebuildTypeButtons() {
        for (ButtonWidget btn : typeButtons) {
            this.remove(btn);
        }
        typeButtons.clear();

        RecipeTypeEnum[] types = RecipeTypeEnum.values();
        int typeY = contentY;
        int btnW = (LEFT_PANE_WIDTH - (types.length - 1) * 2) / types.length;

        for (int i = 0; i < types.length; i++) {
            RecipeTypeEnum t = types[i];
            boolean isSelected = currentRecipe != null && currentRecipe.type == t;
            Text label = t.getDisplayName().copy().formatted(isSelected ? Formatting.YELLOW : Formatting.GRAY);

            ButtonWidget btn = ButtonWidget.builder(label, b -> {
                if (currentRecipe != null) {
                    currentRecipe.type = t;
                    currentRecipe.invalidateCache();
                    rebuildTypeButtons();
                }
            }).dimensions(leftPaneX + i * (btnW + 2), typeY, btnW, 16).build();

            typeButtons.add(btn);
            this.addDrawableChild(btn);
        }
    }

    private void scrollTabs(int delta) {
        int maxOffset = Math.max(0, modTabs.size() - visibleTabs);
        tabScrollOffset = Math.max(0, Math.min(maxOffset, tabScrollOffset + delta));
        rebuildTabButtons(rightPaneX + 22, contentY + 18);
    }

    private void rebuildTabButtons(int startX, int startY) {
        for (ButtonWidget btn : tabButtons) {
            this.remove(btn);
        }
        tabButtons.clear();

        int maxOffset = Math.max(0, modTabs.size() - visibleTabs);
        if (prevTabBtn != null) prevTabBtn.active = tabScrollOffset > 0;
        if (nextTabBtn != null) nextTabBtn.active = tabScrollOffset < maxOffset;

        int availableTabsWidth = searchWidth - 44;
        int tabWidth = Math.max(38, (availableTabsWidth - (visibleTabs - 1) * 2) / Math.max(1, visibleTabs));

        for (int i = 0; i < visibleTabs; i++) {
            int tabIndex = tabScrollOffset + i;
            if (tabIndex >= modTabs.size()) break;

            ModTab tab = modTabs.get(tabIndex);
            boolean isSelected = (tabIndex == selectedTabIdx);

            String label = tab.displayName;
            int maxChars = Math.max(4, tabWidth / 6);
            if (label.length() > maxChars) {
                label = label.substring(0, maxChars - 1) + "…";
            }
            Text tabText = isSelected
                    ? Text.literal(label).formatted(Formatting.YELLOW, Formatting.BOLD)
                    : Text.literal(label).formatted(Formatting.GRAY);

            final int chosenIdx = tabIndex;
            ButtonWidget btn = ButtonWidget.builder(tabText, b -> {
                selectedTabIdx = chosenIdx;
                refreshFilteredItems();
                rebuildTabButtons(startX, startY);
            }).dimensions(startX + (i * (tabWidth + 2)), startY, tabWidth, 16).build();

            tabButtons.add(btn);
            this.addDrawableChild(btn);
        }
    }

    private void updateToggleBtn(int x, int y) {
        if (toggleEnabledBtn != null) {
            this.remove(toggleEnabledBtn);
        }
        boolean enabled = currentRecipe != null && currentRecipe.enabled;
        Text toggleText = enabled
                ? Text.translatable("recipeeditor.gui.recipe_enabled").formatted(Formatting.GREEN, Formatting.BOLD)
                : Text.translatable("recipeeditor.gui.recipe_disabled").formatted(Formatting.RED, Formatting.BOLD);

        toggleEnabledBtn = ButtonWidget.builder(toggleText, btn -> {
            if (currentRecipe != null) {
                currentRecipe.enabled = !currentRecipe.enabled;
                updateToggleBtn(x, y);
            }
        }).dimensions(x, y, 140, 18).build();
        this.addDrawableChild(toggleEnabledBtn);
    }

    private void changePage(int delta) {
        int maxPage = Math.max(0, (filteredItems.size() - 1) / Math.max(1, itemsPerPage));
        catalogPage = Math.max(0, Math.min(maxPage, catalogPage + delta));
        updatePaginationButtons();
    }

    private void updatePaginationButtons() {
        int maxPage = Math.max(0, (filteredItems.size() - 1) / Math.max(1, itemsPerPage));
        if (prevPageBtn != null) prevPageBtn.active = catalogPage > 0;
        if (nextPageBtn != null) nextPageBtn.active = catalogPage < maxPage;
    }

    private void adjustResultCount(int delta) {
        if (currentRecipe != null) {
            currentRecipe.resultCount = Math.max(1, Math.min(64, currentRecipe.resultCount + delta));
        }
    }

    private void clearSelectedSlot() {
        if (currentRecipe == null) return;
        if (selectedSlot >= 0 && selectedSlot < 9) {
            currentRecipe.setItemAt(selectedSlot, Items.AIR);
        } else if (selectedSlot == RESULT_SLOT) {
            currentRecipe.setResultItem(Items.AIR);
            currentRecipe.resultItemId = "minecraft:air";
        }
        updateButtonStates();
    }

    private void fillOuterWithSelected() {
        if (currentRecipe == null) return;
        Item itemToUse;
        if (selectedSlot >= 0 && selectedSlot < 9) {
            itemToUse = currentRecipe.getItemAt(selectedSlot);
        } else {
            itemToUse = currentRecipe.getResultItem();
        }
        if (itemToUse == null || itemToUse == Items.AIR) {
            itemToUse = Items.GOLDEN_APPLE;
        }
        int[] outerIndices = {0, 1, 2, 3, 5, 6, 7, 8};
        for (int idx : outerIndices) {
            currentRecipe.setItemAt(idx, itemToUse);
        }
    }

    private void clearAllSlots() {
        if (currentRecipe == null) return;
        for (int i = 0; i < 9; i++) {
            currentRecipe.setItemAt(i, Items.AIR);
        }
    }

    private void loadVanillaForCurrent() {
        if (currentRecipe == null) return;
        Item item = currentRecipe.getResultItem();
        if (item == Items.AIR) return;

        var world = this.client != null ? this.client.world : null;
        CustomRecipeData decompiled = RecipeInspector.decompileRecipe(item, world);
        if (decompiled != null) {
            currentRecipe.patternSlots = decompiled.patternSlots;
            currentRecipe.type = decompiled.type;
            currentRecipe.experience = decompiled.experience;
            currentRecipe.cookingTime = decompiled.cookingTime;
            currentRecipe.invalidateCache();
            rebuildTypeButtons();
            updateButtonStates();
        }
    }

    private void deleteCurrentRecipe() {
        if (currentRecipe == null) return;
        configCopy.removeRecipe(currentRecipe.resultItemId);
        currentRecipe = new CustomRecipeData("", "minecraft:air", 1, RecipeTypeEnum.SHAPED_CRAFTING);
        rebuildTypeButtons();
        updateButtonStates();
        refreshFilteredItems();
    }

    private void exportDatapack() {
        // Automatically save current recipe if valid
        if (currentRecipe != null && currentRecipe.getResultItem() != Items.AIR) {
            configCopy.addOrUpdateRecipe(currentRecipe.copy());
        }
        boolean success = DatapackExporter.exportToZip();
        if (success) {
            notificationText = Text.translatable("recipeeditor.gui.datapack_exported").formatted(Formatting.GREEN, Formatting.BOLD);
        } else {
            notificationText = Text.translatable("recipeeditor.gui.datapack_export_fail").formatted(Formatting.RED, Formatting.BOLD);
        }
        notificationTimer = System.currentTimeMillis() + 4000;
        updateButtonStates();
    }

    private void resetDefaults() {
        configCopy.initDefaults();
        currentRecipe = new CustomRecipeData("", "minecraft:air", 1, RecipeTypeEnum.SHAPED_CRAFTING);
        rebuildTypeButtons();
        updateButtonStates();
        refreshFilteredItems();
    }

    private void saveAndClose() {
        // If current recipe is valid, save it into configCopy
        if (currentRecipe != null && currentRecipe.getResultItem() != Items.AIR) {
            configCopy.addOrUpdateRecipe(currentRecipe.copy());
        }

        RecipeEditorConfig actual = RecipeEditorConfig.getInstance();
        actual.recipes.clear();
        for (Map.Entry<String, CustomRecipeData> entry : configCopy.recipes.entrySet()) {
            actual.recipes.put(entry.getKey(), entry.getValue().copy());
        }
        actual.save();
        RecipeInspector.invalidateCache();
        this.close();
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    private int getCraftingSlotAt(double mouseX, double mouseY) {
        int gridStartX = leftPaneX + 4;
        int gridStartY = contentY + 24;

        if (currentRecipe != null && (currentRecipe.type == RecipeTypeEnum.SMELTING || currentRecipe.type == RecipeTypeEnum.STONECUTTING)) {
            int inputX = gridStartX + SLOT_SIZE;
            int inputY = gridStartY + SLOT_SIZE;
            if (mouseX >= inputX && mouseX <= inputX + 22 && mouseY >= inputY && mouseY <= inputY + 22) {
                return 0;
            }
        } else {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int slotIndex = row * 3 + col;
                    int x = gridStartX + col * SLOT_SIZE;
                    int y = gridStartY + row * SLOT_SIZE;
                    if (mouseX >= x && mouseX <= x + 22 && mouseY >= y && mouseY <= y + 22) {
                        return slotIndex;
                    }
                }
            }
        }

        int arrowX = gridStartX + 3 * SLOT_SIZE + 10;
        int resultX = arrowX + 24;
        int resultY = gridStartY + SLOT_SIZE - 3;
        if (mouseX >= resultX && mouseX <= resultX + 28 && mouseY >= resultY && mouseY <= resultY + 28) {
            return RESULT_SLOT;
        }

        return -1;
    }

    private Item getCatalogItemAt(double mouseX, double mouseY) {
        int startIndex = catalogPage * itemsPerPage;
        int endIndex = Math.min(filteredItems.size(), startIndex + itemsPerPage);

        for (int i = startIndex; i < endIndex; i++) {
            int localIdx = i - startIndex;
            int cRow = localIdx / catalogCols;
            int cCol = localIdx % catalogCols;

            int slotX = rightPaneX + cCol * SLOT_SIZE;
            int slotY = catalogGridY + cRow * SLOT_SIZE;

            if (mouseX >= slotX && mouseX <= slotX + 22 && mouseY >= slotY && mouseY <= slotY + 22) {
                return filteredItems.get(i);
            }
        }
        return null;
    }

    private Item getItemInSlot(int slotIndex) {
        if (currentRecipe == null) return Items.AIR;
        if (slotIndex >= 0 && slotIndex < 9) {
            return currentRecipe.getItemAt(slotIndex);
        } else if (slotIndex == RESULT_SLOT) {
            return currentRecipe.getResultItem();
        }
        return Items.AIR;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int catalogWidth = catalogCols * SLOT_SIZE + 10;
        int catalogHeight = (catalogRows * SLOT_SIZE) + 40;

        if (mouseX >= rightPaneX && mouseX <= rightPaneX + catalogWidth &&
                mouseY >= contentY && mouseY <= catalogGridY + catalogHeight) {
            if (verticalAmount > 0) {
                changePage(-1);
                return true;
            } else if (verticalAmount < 0) {
                changePage(1);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 6, 0xFFFFFF);

        // Status Badge for current recipe item
        if (currentRecipe != null) {
            Item currentItem = currentRecipe.getResultItem();
            if (currentItem != Items.AIR) {
                var world = this.client != null ? this.client.world : null;
                RecipeStatus status = RecipeInspector.getStatus(currentItem, world, configCopy);
                Text statusBadge = Text.literal("Status: ").formatted(Formatting.GRAY).append(status.getDisplayText());
                context.drawTextWithShadow(this.textRenderer, statusBadge, leftPaneX + 150, 24, 0xFFFFFFFF);
            } else {
                Text statusBadge = Text.literal("Status: ").formatted(Formatting.GRAY).append(Text.translatable("recipeeditor.gui.status_none").formatted(Formatting.DARK_GRAY));
                context.drawTextWithShadow(this.textRenderer, statusBadge, leftPaneX + 150, 24, 0xFFFFFFFF);
            }
        }

        ItemStack hoveredStack = ItemStack.EMPTY;
        int hoveredCraftingSlot = getCraftingSlotAt(mouseX, mouseY);

        // --- DRAW CRAFTING GRID ---
        int gridStartX = leftPaneX + 4;
        int gridStartY = contentY + 24;

        if (currentRecipe != null && (currentRecipe.type == RecipeTypeEnum.SMELTING || currentRecipe.type == RecipeTypeEnum.STONECUTTING)) {
            int inputX = gridStartX + SLOT_SIZE;
            int inputY = gridStartY + SLOT_SIZE;
            boolean isSelected = (selectedSlot == 0);
            boolean isDropTarget = (draggedItem != null && hoveredCraftingSlot == 0);
            drawSlotBox(context, inputX, inputY, 22, 22, isSelected, isDropTarget);

            Item item = currentRecipe.getItemAt(0);
            if (item != Items.AIR) {
                ItemStack stack = new ItemStack(item);
                context.drawItem(stack, inputX + 3, inputY + 3);
                context.drawStackOverlay(this.textRenderer, stack, inputX + 3, inputY + 3);
            }
            if (mouseX >= inputX && mouseX <= inputX + 22 && mouseY >= inputY && mouseY <= inputY + 22 && item != Items.AIR) {
                hoveredStack = new ItemStack(item);
            }
        } else {
            // 3x3 Grid
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int slotIndex = row * 3 + col;
                    int x = gridStartX + col * SLOT_SIZE;
                    int y = gridStartY + row * SLOT_SIZE;

                    boolean isSelected = (selectedSlot == slotIndex);
                    boolean isDropTarget = (draggedItem != null && hoveredCraftingSlot == slotIndex);
                    drawSlotBox(context, x, y, 22, 22, isSelected, isDropTarget);

                    if (currentRecipe != null) {
                        Item item = currentRecipe.getItemAt(slotIndex);
                        if (item != Items.AIR) {
                            ItemStack stack = new ItemStack(item);
                            context.drawItem(stack, x + 3, y + 3);
                            context.drawStackOverlay(this.textRenderer, stack, x + 3, y + 3);
                        }
                        if (mouseX >= x && mouseX <= x + 22 && mouseY >= y && mouseY <= y + 22 && item != Items.AIR) {
                            hoveredStack = new ItemStack(item);
                        }
                    }
                }
            }
        }

        // Draw Arrow
        int arrowX = gridStartX + 3 * SLOT_SIZE + 10;
        int arrowY = gridStartY + SLOT_SIZE + 2;
        context.drawTextWithShadow(this.textRenderer, Text.literal("➡").formatted(Formatting.GOLD, Formatting.BOLD), arrowX, arrowY, 0xFFFFAA00);

        // Draw Result Slot
        int resultX = arrowX + 24;
        int resultY = gridStartY + SLOT_SIZE - 3;
        boolean isResultSelected = (selectedSlot == RESULT_SLOT);
        boolean isResultDropTarget = (draggedItem != null && hoveredCraftingSlot == RESULT_SLOT);
        drawSlotBox(context, resultX, resultY, 28, 28, isResultSelected, isResultDropTarget);

        if (currentRecipe != null) {
            Item resItem = currentRecipe.getResultItem();
            if (resItem != Items.AIR) {
                ItemStack resStack = new ItemStack(resItem, currentRecipe.resultCount);
                context.drawItem(resStack, resultX + 6, resultY + 6);
                context.drawStackOverlay(this.textRenderer, resStack, resultX + 6, resultY + 6);
                if (mouseX >= resultX && mouseX <= resultX + 28 && mouseY >= resultY && mouseY <= resultY + 28) {
                    hoveredStack = resStack;
                }
            }

            // Result count label
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("x" + currentRecipe.resultCount).formatted(Formatting.WHITE, Formatting.BOLD), resultX + 14, resultY + 38, 0xFFFFFFFF);
        }

        // Active slot description text
        Text slotName = selectedSlot == RESULT_SLOT
                ? Text.translatable("recipeeditor.gui.selected_result")
                : Text.translatable("recipeeditor.gui.selected_slot", selectedSlot + 1);
        context.drawTextWithShadow(this.textRenderer, slotName.copy().formatted(Formatting.GREEN), leftPaneX, contentY + 102, 0xFF55FF55);

        // --- DRAW DYNAMIC CATALOG GRID ---
        int startIndex = catalogPage * itemsPerPage;
        int endIndex = Math.min(filteredItems.size(), startIndex + itemsPerPage);

        for (int i = startIndex; i < endIndex; i++) {
            int localIdx = i - startIndex;
            int cRow = localIdx / catalogCols;
            int cCol = localIdx % catalogCols;

            int slotX = rightPaneX + cCol * SLOT_SIZE;
            int slotY = catalogGridY + cRow * SLOT_SIZE;

            boolean isHovered = mouseX >= slotX && mouseX <= slotX + 22 && mouseY >= slotY && mouseY <= slotY + 22;
            drawSlotBox(context, slotX, slotY, 22, 22, isHovered, false);

            Item catItem = filteredItems.get(i);
            ItemStack catStack = new ItemStack(catItem);
            context.drawItem(catStack, slotX + 3, slotY + 3);

            if (isHovered) {
                hoveredStack = catStack;
            }
        }

        // Catalog Page Info
        int maxPage = Math.max(1, (filteredItems.size() + itemsPerPage - 1) / Math.max(1, itemsPerPage));
        MutableText pageInfo = Text.translatable("recipeeditor.gui.items_total", catalogPage + 1, maxPage, filteredItems.size());
        context.drawCenteredTextWithShadow(this.textRenderer, pageInfo.formatted(Formatting.GRAY), rightPaneX + (searchWidth / 2), catalogGridY + (catalogRows * SLOT_SIZE) + 10, 0xFFAAAAAA);

        // Notification overlay message
        if (notificationText != null && System.currentTimeMillis() < notificationTimer) {
            context.drawCenteredTextWithShadow(this.textRenderer, notificationText, centerX, this.height - 40, 0xFF55FF55);
        }

        // Render dragged item or hover tooltip
        if (draggedItem != null && draggedItem != Items.AIR) {
            ItemStack dragStack = (dragSourceSlot == RESULT_SLOT && currentRecipe != null)
                    ? new ItemStack(draggedItem, currentRecipe.resultCount)
                    : new ItemStack(draggedItem);
            context.drawItem(dragStack, mouseX - 8, mouseY - 8);
            context.drawStackOverlay(this.textRenderer, dragStack, mouseX - 8, mouseY - 8);
        } else if (!hoveredStack.isEmpty()) {
            context.drawItemTooltip(this.textRenderer, hoveredStack, mouseX, mouseY);
        }
    }

    private void drawSlotBox(DrawContext context, int x, int y, int w, int h, boolean highlight, boolean isDropTarget) {
        int borderColor;
        int innerBg;
        if (isDropTarget) {
            borderColor = 0xFF55FF55; // Bright green drop target
            innerBg = 0x6655FF55;
        } else if (highlight) {
            borderColor = 0xFFFFD700; // Gold selection
            innerBg = 0x66FFAA00;
        } else {
            borderColor = 0xFF373737; // Transparent dark border
            innerBg = 0x88000000;     // Semi-transparent background
        }

        context.fill(x, y, x + w, y + h, innerBg);
        context.drawBorder(x, y, w, h, borderColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0) { // Left click: select slot or start drag
            int craftingSlot = getCraftingSlotAt(mouseX, mouseY);
            if (craftingSlot != -1) {
                selectedSlot = craftingSlot;
                Item itemInSlot = getItemInSlot(craftingSlot);
                if (itemInSlot != null && itemInSlot != Items.AIR) {
                    draggedItem = itemInSlot;
                    dragSourceSlot = craftingSlot;
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                }
                return true;
            }

            Item catItem = getCatalogItemAt(mouseX, mouseY);
            if (catItem != null && catItem != Items.AIR) {
                if (Screen.hasShiftDown()) {
                    selectTargetItem(catItem);
                } else {
                    draggedItem = catItem;
                    dragSourceSlot = -1;
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                }
                return true;
            }
        } else if (button == 1) { // Right click: clear slot or set target item
            int craftingSlot = getCraftingSlotAt(mouseX, mouseY);
            if (craftingSlot >= 0 && craftingSlot < 9) {
                if (currentRecipe != null) {
                    currentRecipe.setItemAt(craftingSlot, Items.AIR);
                }
                return true;
            } else if (craftingSlot == RESULT_SLOT) {
                if (currentRecipe != null) {
                    currentRecipe.setResultItem(Items.AIR);
                    currentRecipe.resultItemId = "minecraft:air";
                    updateButtonStates();
                }
                return true;
            }

            Item catItem = getCatalogItemAt(mouseX, mouseY);
            if (catItem != null) {
                selectTargetItem(catItem);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggedItem != null) {
            int targetSlot = getCraftingSlotAt(mouseX, mouseY);
            if (targetSlot != -1) {
                if (currentRecipe != null) {
                    if (targetSlot >= 0 && targetSlot < 9) {
                        currentRecipe.setItemAt(targetSlot, draggedItem);
                        selectedSlot = targetSlot;
                    } else if (targetSlot == RESULT_SLOT) {
                        selectTargetItem(draggedItem);
                        selectedSlot = RESULT_SLOT;
                    }
                }
            } else {
                double distSq = (mouseX - dragStartX) * (mouseX - dragStartX) + (mouseY - dragStartY) * (mouseY - dragStartY);
                if (dragSourceSlot == -1 && distSq < 36 && currentRecipe != null) {
                    if (selectedSlot >= 0 && selectedSlot < 9) {
                        currentRecipe.setItemAt(selectedSlot, draggedItem);
                    } else if (selectedSlot == RESULT_SLOT) {
                        selectTargetItem(draggedItem);
                    }
                }
            }
            draggedItem = null;
            dragSourceSlot = -1;
            updateButtonStates();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggedItem != null) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
}
