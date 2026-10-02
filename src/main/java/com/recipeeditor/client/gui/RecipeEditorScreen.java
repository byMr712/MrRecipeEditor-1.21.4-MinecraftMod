package com.recipeeditor.client.gui;

import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
import com.recipeeditor.inspector.RecipeInspector;
import com.recipeeditor.inspector.RecipeStatus;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
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
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class RecipeEditorScreen extends Screen {
    private final Screen parent;
    private final RecipeEditorConfig configCopy;

    // Target Item & Scoped Variants
    private Item targetItem = null;
    private RecipeTypeEnum selectedType = RecipeTypeEnum.SHAPED_CRAFTING;
    private final List<CustomRecipeData> typeVariants = new ArrayList<>();
    private int currentVariantIndex = 0;
    private CustomRecipeData currentRecipe = null;

    private static final int RESULT_SLOT = 9;
    private int selectedSlot = 0; // 0..8 - crafting grid, 9 - result

    // Filter modes
    public enum CatalogFilter {
        ALL("recipeeditor.gui.filter_all"),
        UNCRAFTABLE("recipeeditor.gui.filter_uncraftable"),
        CRAFTABLE("recipeeditor.gui.filter_craftable"),
        CUSTOM("recipeeditor.gui.filter_custom");

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

    // Workstations Pagination
    private int workstationPage = 0;
    private static final int WORKSTATIONS_PER_PAGE = 10;
    private ButtonWidget prevWorkstationBtn;
    private ButtonWidget nextWorkstationBtn;

    // Layout coordinates
    private int leftPaneX;
    private int rightPaneX;
    private int contentY;
    private int catalogGridY;
    private int searchWidth;
    private static final int SLOT_SIZE = 24;
    private static final int LEFT_PANE_WIDTH = 196;

    // Widgets
    private TextFieldWidget searchField;
    private TextFieldWidget resultCountField;
    private ButtonWidget minusCountBtn;
    private ButtonWidget plusCountBtn;
    private ButtonWidget prevPageBtn;
    private ButtonWidget nextPageBtn;
    private ButtonWidget toggleEnabledBtn;
    private ButtonWidget prevTabBtn;
    private ButtonWidget nextTabBtn;
    private final List<ButtonWidget> tabButtons = new ArrayList<>();
    private final List<ButtonWidget> filterButtons = new ArrayList<>();
    private final List<ButtonWidget> typeButtons = new ArrayList<>();
    private ButtonWidget saveCraftBtn;
    private ButtonWidget clearCraftBtn;
    private ButtonWidget deleteRecipeBtn;

    // Variant Controls
    private ButtonWidget prevVariantBtn;
    private ButtonWidget nextVariantBtn;
    private TextFieldWidget variantField;
    private boolean isUpdatingVariantField = false;

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

        RecipeInspector.invalidateCache();

        // Target item starts null (user must select/RMB an item to edit)
        this.targetItem = null;
        this.currentRecipe = null;
        this.typeVariants.clear();
        this.currentVariantIndex = 0;

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

    private boolean hasAnyIngredients(CustomRecipeData recipe) {
        if (recipe == null || recipe.patternSlots == null) return false;
        for (String slot : recipe.patternSlots) {
            if (slot != null && !slot.isEmpty() && !slot.equals("minecraft:air")) {
                return true;
            }
        }
        return false;
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
        this.targetItem = item;
        var world = this.client != null ? this.client.world : null;

        // Pick best default machine type
        List<CustomRecipeData> customList = configCopy.getRecipesFor(item);
        if (!customList.isEmpty()) {
            selectedType = customList.get(0).type;
        } else {
            List<CustomRecipeData> scanned = RecipeInspector.getAllRecipeVariants(item, world);
            if (!scanned.isEmpty()) {
                selectedType = scanned.get(0).type;
            } else {
                selectedType = RecipeTypeEnum.SHAPED_CRAFTING;
            }
        }

        refreshTypeVariants(selectedType, true);
        setEditorWidgetsVisible(true);
        rebuildTypeButtons();
        updateButtonStates();
    }

    public void loadRecipeForTarget(Item item) {
        selectTargetItem(item);
    }

    private void refreshTypeVariants(RecipeTypeEnum type, boolean resetIndex) {
        this.selectedType = type;
        this.typeVariants.clear();

        if (targetItem == null || targetItem == Items.AIR) {
            this.currentRecipe = null;
            return;
        }

        var world = this.client != null ? this.client.world : null;
        Set<String> seenSignatures = new HashSet<>();

        // 1. Custom recipes for targetItem and this machine type
        List<CustomRecipeData> customList = configCopy.getRecipesFor(targetItem, type);
        for (CustomRecipeData c : customList) {
            String sig = c.getPatternSignature();
            if (seenSignatures.add(sig)) {
                typeVariants.add(c.copy());
            }
        }

        // 2. Scanned / Modded / Vanilla recipes for targetItem and this machine type
        List<CustomRecipeData> scanned = RecipeInspector.getAllRecipeVariants(targetItem, world);
        for (CustomRecipeData s : scanned) {
            if (s.type == type) {
                String sig = s.getPatternSignature();
                if (seenSignatures.add(sig)) {
                    typeVariants.add(s.copy());
                }
            }
        }

        // 3. Fallback: If no recipes exist for this machine type, create a blank empty craft
        if (typeVariants.isEmpty()) {
            Identifier id = Registries.ITEM.getId(targetItem);
            CustomRecipeData blank = new CustomRecipeData(
                    id != null ? id.getPath() : "craft",
                    id != null ? id.toString() : "minecraft:air",
                    1,
                    type
            );
            typeVariants.add(blank);
        }

        if (resetIndex || currentVariantIndex >= typeVariants.size() || currentVariantIndex < 0) {
            currentVariantIndex = 0;
        }

        currentRecipe = typeVariants.get(currentVariantIndex);

        if (resultCountField != null) {
            resultCountField.setText(String.valueOf(currentRecipe.getResultCountForType(selectedType)));
        }
        updateVariantButtons();
        updateToggleBtn(leftPaneX, contentY);
        updateButtonStates();
    }

    private void switchMachineType(RecipeTypeEnum newType) {
        if (targetItem == null) return;
        refreshTypeVariants(newType, true);
        rebuildTypeButtons();
    }

    private void changeVariant(int delta) {
        if (typeVariants.isEmpty()) {
            if (currentRecipe != null) typeVariants.add(currentRecipe);
            else return;
        }

        if (delta < 0) {
            if (currentVariantIndex > 0) {
                currentVariantIndex--;
            }
        } else if (delta > 0) {
            if (currentVariantIndex < typeVariants.size() - 1) {
                currentVariantIndex++;
            } else {
                // Add a new empty variant for the current machine type
                Identifier id = Registries.ITEM.getId(targetItem);
                CustomRecipeData newVar = new CustomRecipeData(
                        id != null ? id.getPath() : "craft",
                        id != null ? id.toString() : "minecraft:air",
                        1,
                        selectedType
                );
                typeVariants.add(newVar);
                currentVariantIndex = typeVariants.size() - 1;
            }
        }

        currentRecipe = typeVariants.get(currentVariantIndex);
        if (resultCountField != null) {
            resultCountField.setText(String.valueOf(currentRecipe.getResultCountForType(selectedType)));
        }
        updateVariantButtons();
        updateToggleBtn(leftPaneX, contentY);
        updateButtonStates();
    }

    private void updateVariantButtons() {
        if (prevVariantBtn != null) {
            prevVariantBtn.active = currentVariantIndex > 0;
        }
        if (nextVariantBtn != null) {
            boolean isLast = currentVariantIndex >= typeVariants.size() - 1;
            nextVariantBtn.setMessage(Text.literal(isLast ? "+" : "▶"));
        }
        if (variantField != null) {
            isUpdatingVariantField = true;
            variantField.setText(String.valueOf(currentVariantIndex + 1));
            isUpdatingVariantField = false;
        }
    }

    private void setEditorWidgetsVisible(boolean visible) {
        for (ButtonWidget btn : typeButtons) {
            btn.visible = visible;
        }
        if (prevWorkstationBtn != null) prevWorkstationBtn.visible = visible && (RecipeTypeEnum.values().length > WORKSTATIONS_PER_PAGE);
        if (nextWorkstationBtn != null) nextWorkstationBtn.visible = visible && (RecipeTypeEnum.values().length > WORKSTATIONS_PER_PAGE);
        if (prevVariantBtn != null) prevVariantBtn.visible = visible;
        if (variantField != null) variantField.setVisible(visible);
        if (nextVariantBtn != null) nextVariantBtn.visible = visible;
        if (resultCountField != null) resultCountField.setVisible(visible);
        if (minusCountBtn != null) minusCountBtn.visible = visible;
        if (plusCountBtn != null) plusCountBtn.visible = visible;
        if (saveCraftBtn != null) saveCraftBtn.visible = visible;
        if (clearCraftBtn != null) clearCraftBtn.visible = visible;
        if (deleteRecipeBtn != null) deleteRecipeBtn.visible = visible;
    }

    @Override
    protected void init() {
        contentY = 36;
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

        // --- LEFT PANE (Controls Aligned horizontally with Right Pane Tabs) ---
        // Top toggle button: [Мои крафты: ВКЛЮЧЕНЫ / ВЫКЛЮЧЕНЫ] - Aligned with filter buttons at filterY
        updateToggleBtn(leftPaneX, filterY);

        // Machine Type Buttons (2 Columns)
        rebuildTypeButtons();

        int typeBtnStartY = filterY + 22;
        int numVisibleTypes = Math.min(WORKSTATIONS_PER_PAGE, RecipeTypeEnum.values().length);
        int numRows = (numVisibleTypes + 1) / 2;
        int typeBtnsHeight = numRows * 18;

        // Variant Bar (Above Crafting Grid)
        int variantBarY = typeBtnStartY + typeBtnsHeight + 4;
        prevVariantBtn = ButtonWidget.builder(Text.literal("◀"), btn -> changeVariant(-1))
                .dimensions(leftPaneX + 4, variantBarY, 18, 16)
                .build();
        this.addDrawableChild(prevVariantBtn);

        variantField = new TextFieldWidget(this.textRenderer, leftPaneX + 24, variantBarY, 26, 16, Text.literal("Variant"));
        variantField.setText(String.valueOf(currentVariantIndex + 1));
        variantField.setMaxLength(3);
        variantField.setChangedListener(text -> {
            if (isUpdatingVariantField) return;
            try {
                if (!text.trim().isEmpty()) {
                    int val = Integer.parseInt(text.trim());
                    if (val >= 1 && val <= typeVariants.size()) {
                        currentVariantIndex = val - 1;
                        currentRecipe = typeVariants.get(currentVariantIndex);
                        if (resultCountField != null) {
                            resultCountField.setText(String.valueOf(currentRecipe.getResultCountForType(selectedType)));
                        }
                        updateVariantButtons();
                        updateButtonStates();
                    }
                }
            } catch (NumberFormatException ignored) {}
        });
        this.addDrawableChild(variantField);

        nextVariantBtn = ButtonWidget.builder(Text.literal(currentVariantIndex >= typeVariants.size() - 1 ? "+" : "▶"), btn -> changeVariant(1))
                .dimensions(leftPaneX + 52, variantBarY, 18, 16)
                .build();
        this.addDrawableChild(nextVariantBtn);

        // Crafting Grid & Result Slot Layout
        int gridStartX = leftPaneX + 4;
        int gridStartY = variantBarY + 20;
        int arrowX = gridStartX + 3 * SLOT_SIZE + 10;
        int resultX = arrowX + 24;
        int resultY = gridStartY + SLOT_SIZE - 3;

        // Result count controls (Centered under result slot)
        int countControlsWidth = 60;
        int countStartX = (resultX + 14) - (countControlsWidth / 2);
        int countY = resultY + 32;

        minusCountBtn = ButtonWidget.builder(Text.literal("-"), btn -> adjustResultCount(-1))
                .dimensions(countStartX, countY, 14, 16)
                .build();
        this.addDrawableChild(minusCountBtn);

        resultCountField = new TextFieldWidget(this.textRenderer, countStartX + 16, countY, 28, 16, Text.literal("Count"));
        resultCountField.setText(String.valueOf(currentRecipe != null ? currentRecipe.getResultCountForType(selectedType) : 1));
        resultCountField.setMaxLength(4);
        resultCountField.setChangedListener(text -> {
            try {
                if (!text.trim().isEmpty()) {
                    int val = Integer.parseInt(text.trim());
                    if (val > 1000) val = 1000;
                    if (val < 1) val = 1;
                    if (currentRecipe != null) {
                        currentRecipe.setResultCountForType(selectedType, val);
                    }
                }
            } catch (NumberFormatException ignored) {}
        });
        this.addDrawableChild(resultCountField);

        plusCountBtn = ButtonWidget.builder(Text.literal("+"), btn -> adjustResultCount(1))
                .dimensions(countStartX + 46, countY, 14, 16)
                .build();
        this.addDrawableChild(plusCountBtn);

        // Action Buttons
        int actionBtnY = gridStartY + 92;

        saveCraftBtn = ButtonWidget.builder(Text.translatable("recipeeditor.gui.save_craft").formatted(Formatting.GREEN, Formatting.BOLD), btn -> saveCurrentCraft())
                .dimensions(leftPaneX, actionBtnY, LEFT_PANE_WIDTH, 18)
                .build();
        this.addDrawableChild(saveCraftBtn);

        clearCraftBtn = ButtonWidget.builder(Text.translatable("recipeeditor.gui.clear_all").formatted(Formatting.RED), btn -> clearAllSlots())
                .dimensions(leftPaneX, actionBtnY + 20, LEFT_PANE_WIDTH, 18)
                .build();
        this.addDrawableChild(clearCraftBtn);

        deleteRecipeBtn = ButtonWidget.builder(Text.translatable("recipeeditor.gui.delete_custom").formatted(Formatting.RED), btn -> deleteCurrentRecipe())
                .dimensions(leftPaneX, actionBtnY + 40, LEFT_PANE_WIDTH, 18)
                .build();
        this.addDrawableChild(deleteRecipeBtn);

        // Set initial visibility based on targetItem selection
        setEditorWidgetsVisible(targetItem != null);
        updateVariantButtons();

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

        // --- BOTTOM PANE (Controls in bottom-right corner) ---
        rebuildBottomButtons(bottomY);

        updateButtonStates();
    }

    private void rebuildBottomButtons(int bottomY) {
        Text resetText = Text.translatable("recipeeditor.gui.reset_defaults");
        Text exitText = Text.translatable("recipeeditor.gui.exit");

        int wExit = Math.max(65, this.textRenderer.getWidth(exitText) + 20);
        int wReset = Math.max(120, this.textRenderer.getWidth(resetText) + 16);

        int rightMargin = this.width - 12;
        int exitX = rightMargin - wExit;
        int resetX = exitX - 6 - wReset;

        this.addDrawableChild(ButtonWidget.builder(resetText, btn -> promptResetDefaults())
                .dimensions(resetX, bottomY, wReset, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(exitText, btn -> this.close())
                .dimensions(exitX, bottomY, wExit, 20)
                .build());
    }

    private void promptResetDefaults() {
        if (this.client == null) return;
        this.client.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        resetDefaults();
                    }
                    this.client.setScreen(this);
                },
                Text.translatable("recipeeditor.gui.reset_confirm_title"),
                Text.translatable("recipeeditor.gui.reset_confirm_msg")
        ));
    }

    private void updateButtonStates() {
        if (deleteRecipeBtn != null) {
            deleteRecipeBtn.active = targetItem != null && configCopy.hasCustomRecipe(targetItem);
        }
        if (saveCraftBtn != null) {
            saveCraftBtn.active = targetItem != null && currentRecipe != null && hasAnyIngredients(currentRecipe);
        }
        if (clearCraftBtn != null) {
            clearCraftBtn.active = currentRecipe != null && hasAnyIngredients(currentRecipe);
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
                selectedTabIdx = 0; // Automatically reset to All mods
                tabScrollOffset = 0;
                currentFilter = f;
                refreshFilteredItems();
                rebuildFilterButtons(startX, startY);
                rebuildTabButtons(rightPaneX + 22, contentY + 18);
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
        if (prevWorkstationBtn != null) {
            this.remove(prevWorkstationBtn);
            prevWorkstationBtn = null;
        }
        if (nextWorkstationBtn != null) {
            this.remove(nextWorkstationBtn);
            nextWorkstationBtn = null;
        }

        RecipeTypeEnum[] allTypes = RecipeTypeEnum.values();
        int totalTypes = allTypes.length;
        int maxPages = Math.max(1, (totalTypes + WORKSTATIONS_PER_PAGE - 1) / WORKSTATIONS_PER_PAGE);
        workstationPage = Math.max(0, Math.min(maxPages - 1, workstationPage));

        int btnW = (LEFT_PANE_WIDTH - 4) / 2;
        int typeBtnStartY = contentY + 22;

        int startIdx = workstationPage * WORKSTATIONS_PER_PAGE;
        int endIdx = Math.min(totalTypes, startIdx + WORKSTATIONS_PER_PAGE);

        for (int i = startIdx; i < endIdx; i++) {
            RecipeTypeEnum t = allTypes[i];
            boolean isSelected = (selectedType == t);
            Text label = t.getDisplayName().copy().formatted(isSelected ? Formatting.YELLOW : Formatting.GRAY);

            int localIdx = i - startIdx;
            int row = localIdx / 2;
            int col = localIdx % 2;
            int x = leftPaneX + col * (btnW + 4);
            int y = typeBtnStartY + row * 18;

            ButtonWidget btn = ButtonWidget.builder(label, b -> switchMachineType(t))
                    .dimensions(x, y, btnW, 16)
                    .tooltip(Tooltip.of(t.getTooltip()))
                    .build();

            btn.visible = (targetItem != null);
            typeButtons.add(btn);
            this.addDrawableChild(btn);
        }

        // Add pager controls if more than 10 workstations exist
        if (totalTypes > WORKSTATIONS_PER_PAGE) {
            int pagerY = typeBtnStartY - 14;
            prevWorkstationBtn = ButtonWidget.builder(Text.literal("◀"), b -> {
                if (workstationPage > 0) {
                    workstationPage--;
                    rebuildTypeButtons();
                }
            }).dimensions(leftPaneX, pagerY, 16, 12).build();
            prevWorkstationBtn.active = workstationPage > 0;
            prevWorkstationBtn.visible = (targetItem != null);
            this.addDrawableChild(prevWorkstationBtn);

            nextWorkstationBtn = ButtonWidget.builder(Text.literal("▶"), b -> {
                if (workstationPage < maxPages - 1) {
                    workstationPage++;
                    rebuildTypeButtons();
                }
            }).dimensions(leftPaneX + LEFT_PANE_WIDTH - 16, pagerY, 16, 12).build();
            nextWorkstationBtn.active = workstationPage < maxPages - 1;
            nextWorkstationBtn.visible = (targetItem != null);
            this.addDrawableChild(nextWorkstationBtn);
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
                currentFilter = CatalogFilter.ALL;
                refreshFilteredItems();
                rebuildFilterButtons(rightPaneX, contentY);
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
        boolean enabled = configCopy.modEnabled;
        Text toggleText = enabled
                ? Text.translatable("recipeeditor.gui.recipe_enabled").formatted(Formatting.GREEN, Formatting.BOLD)
                : Text.translatable("recipeeditor.gui.recipe_disabled").formatted(Formatting.RED, Formatting.BOLD);

        toggleEnabledBtn = ButtonWidget.builder(toggleText, btn -> {
            configCopy.modEnabled = !configCopy.modEnabled;
            RecipeEditorConfig actual = RecipeEditorConfig.getInstance();
            actual.modEnabled = configCopy.modEnabled;
            actual.save();
            updateToggleBtn(x, y);
            refreshFilteredItems();
            updateButtonStates();
        }).dimensions(x, y, LEFT_PANE_WIDTH, 18).build();
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
            int currentCount = currentRecipe.getResultCountForType(selectedType);
            int newCount = Math.max(1, Math.min(1000, currentCount + delta));
            currentRecipe.setResultCountForType(selectedType, newCount);
            if (resultCountField != null) {
                resultCountField.setText(String.valueOf(newCount));
            }
        }
    }

    private void clearSelectedSlot() {
        if (currentRecipe == null) return;
        if (selectedSlot >= 0 && selectedSlot < 9) {
            currentRecipe.setItemAt(selectedSlot, Items.AIR);
        } else if (selectedSlot == RESULT_SLOT) {
            currentRecipe.setResultItem(Items.AIR);
            currentRecipe.resultItemId = "minecraft:air";
            if (resultCountField != null) {
                resultCountField.setText("1");
            }
        }
        updateButtonStates();
    }

    private void clearAllSlots() {
        if (currentRecipe == null) return;
        for (int i = 0; i < 9; i++) {
            currentRecipe.setItemAt(i, Items.AIR);
        }
        updateButtonStates();
    }

    private void saveCurrentCraft() {
        if (targetItem == null || currentRecipe == null || targetItem == Items.AIR) return;

        if (!hasAnyIngredients(currentRecipe)) {
            notificationText = Text.translatable("recipeeditor.gui.craft_empty_warn").formatted(Formatting.RED, Formatting.BOLD);
            notificationTimer = System.currentTimeMillis() + 3000;
            return;
        }

        currentRecipe.setResultItem(targetItem);
        currentRecipe.type = selectedType;
        if (currentRecipe.getResultCountForType(selectedType) <= 0) {
            currentRecipe.setResultCountForType(selectedType, 1);
        }

        // Deduplicate exact pattern variants inside current type list
        String currentSig = currentRecipe.getPatternSignature();
        for (int i = typeVariants.size() - 1; i >= 0; i--) {
            if (i != currentVariantIndex && currentSig.equals(typeVariants.get(i).getPatternSignature())) {
                typeVariants.remove(i);
                if (currentVariantIndex > i) currentVariantIndex--;
            }
        }

        // Save into config
        configCopy.addOrUpdateRecipe(currentRecipe.copy());
        RecipeEditorConfig actual = RecipeEditorConfig.getInstance();
        actual.recipes.clear();
        for (Map.Entry<String, CustomRecipeData> entry : configCopy.recipes.entrySet()) {
            actual.recipes.put(entry.getKey(), entry.getValue().copy());
        }
        actual.save();

        notificationText = Text.translatable("recipeeditor.gui.craft_saved").formatted(Formatting.GREEN, Formatting.BOLD);
        notificationTimer = System.currentTimeMillis() + 3000;
        updateVariantButtons();
        updateButtonStates();
        refreshFilteredItems();
    }

    private void deleteCurrentRecipe() {
        if (targetItem == null || currentRecipe == null) return;

        configCopy.removeRecipe(currentRecipe);
        RecipeEditorConfig actual = RecipeEditorConfig.getInstance();
        actual.removeRecipe(currentRecipe);
        actual.save();

        notificationText = Text.translatable("recipeeditor.gui.craft_deleted").formatted(Formatting.RED, Formatting.BOLD);
        notificationTimer = System.currentTimeMillis() + 3000;

        refreshTypeVariants(selectedType, true);
        updateButtonStates();
        refreshFilteredItems();
    }

    private void resetDefaults() {
        configCopy.initDefaults();
        RecipeEditorConfig actual = RecipeEditorConfig.getInstance();
        actual.initDefaults();
        actual.save();

        this.targetItem = null;
        this.currentRecipe = null;
        this.typeVariants.clear();
        this.currentVariantIndex = 0;

        setEditorWidgetsVisible(false);
        updateButtonStates();
        refreshFilteredItems();
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    private int getCraftingSlotAt(double mouseX, double mouseY) {
        if (targetItem == null) return -1;

        int gridStartX = leftPaneX + 4;
        int typeBtnStartY = contentY + 22;
        int numVisibleTypes = Math.min(WORKSTATIONS_PER_PAGE, RecipeTypeEnum.values().length);
        int numRows = (numVisibleTypes + 1) / 2;
        int typeBtnsHeight = numRows * 18;
        int variantBarY = typeBtnStartY + typeBtnsHeight + 4;
        int gridStartY = variantBarY + 20;

        if (selectedType == RecipeTypeEnum.SMELTING || selectedType == RecipeTypeEnum.BLASTING ||
            selectedType == RecipeTypeEnum.SMOKING || selectedType == RecipeTypeEnum.STONECUTTING ||
            selectedType == RecipeTypeEnum.CAMPFIRE_COOKING) {
            int inputX = gridStartX + SLOT_SIZE;
            int inputY = gridStartY + SLOT_SIZE;
            if (mouseX >= inputX && mouseX <= inputX + 22 && mouseY >= inputY && mouseY <= inputY + 22) {
                return 0;
            }
        } else if (selectedType == RecipeTypeEnum.SMITHING) {
            // Smithing: 3 horizontal slots (0: Template, 1: Base, 2: Addition)
            int slotY = gridStartY + SLOT_SIZE;
            for (int i = 0; i < 3; i++) {
                int slotX = gridStartX + i * SLOT_SIZE;
                if (mouseX >= slotX && mouseX <= slotX + 22 && mouseY >= slotY && mouseY <= slotY + 22) {
                    return i;
                }
            }
        } else {
            // 3x3 Grid
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
        int tabY = contentY + 18;
        int tabHeight = 18;
        int catalogHeight = (catalogRows * SLOT_SIZE) + 10;

        // 1. Mouse wheel over Mod Tabs row: scroll tabs
        if (mouseX >= rightPaneX && mouseX <= rightPaneX + searchWidth &&
                mouseY >= tabY && mouseY <= tabY + tabHeight) {
            if (verticalAmount > 0) {
                scrollTabs(-1);
                return true;
            } else if (verticalAmount < 0) {
                scrollTabs(1);
                return true;
            }
        }

        // 2. Mouse wheel over Catalog Grid: scroll catalog pages
        if (mouseX >= rightPaneX && mouseX <= rightPaneX + searchWidth &&
                mouseY >= catalogGridY && mouseY <= catalogGridY + catalogHeight) {
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
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 4, 0xFFFFFF);

        ItemStack hoveredStack = ItemStack.EMPTY;
        int hoveredCraftingSlot = getCraftingSlotAt(mouseX, mouseY);

        // --- LEFT PANE RENDERING ---
        if (targetItem == null) {
            // Placeholder when no item is selected yet (green, 3 lines, centered)
            int pCenterX = leftPaneX + (LEFT_PANE_WIDTH / 2);
            int pStartY = contentY + 90;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("recipeeditor.gui.select_item_hint_1").formatted(Formatting.GREEN), pCenterX, pStartY, 0xFF55FF55);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("recipeeditor.gui.select_item_hint_2").formatted(Formatting.GREEN), pCenterX, pStartY + 12, 0xFF55FF55);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("recipeeditor.gui.select_item_hint_3").formatted(Formatting.GREEN), pCenterX, pStartY + 24, 0xFF55FF55);
        } else {
            // Draw total variants count beside variant selector buttons
            int variantTotal = Math.max(1, typeVariants.size());
            int typeBtnStartY = contentY + 22;
            int numVisibleTypes = Math.min(WORKSTATIONS_PER_PAGE, RecipeTypeEnum.values().length);
            int numRows = (numVisibleTypes + 1) / 2;
            int typeBtnsHeight = numRows * 18;
            int variantBarY = typeBtnStartY + typeBtnsHeight + 4;
            int gridStartX = leftPaneX + 4;
            int gridStartY = variantBarY + 20;

            context.drawTextWithShadow(this.textRenderer, Text.literal("/ " + variantTotal).formatted(Formatting.GRAY), leftPaneX + 74, variantBarY + 4, 0xFFAAAAAA);

            if (selectedType == RecipeTypeEnum.SMELTING || selectedType == RecipeTypeEnum.BLASTING ||
                selectedType == RecipeTypeEnum.SMOKING || selectedType == RecipeTypeEnum.STONECUTTING ||
                selectedType == RecipeTypeEnum.CAMPFIRE_COOKING) {
                // 1 Single Input Slot
                int inputX = gridStartX + SLOT_SIZE;
                int inputY = gridStartY + SLOT_SIZE;
                boolean isSelected = (selectedSlot == 0);
                boolean isDropTarget = (draggedItem != null && hoveredCraftingSlot == 0);
                drawSlotBox(context, inputX, inputY, 22, 22, isSelected, isDropTarget);

                if (currentRecipe != null) {
                    Item item = currentRecipe.getItemAt(0);
                    if (item != Items.AIR) {
                        ItemStack stack = new ItemStack(item);
                        context.drawItem(stack, inputX + 3, inputY + 3);
                        context.drawStackOverlay(this.textRenderer, stack, inputX + 3, inputY + 3);
                    }
                    if (mouseX >= inputX && mouseX <= inputX + 22 && mouseY >= inputY && mouseY <= inputY + 22 && item != Items.AIR) {
                        hoveredStack = new ItemStack(item);
                    }
                }
            } else if (selectedType == RecipeTypeEnum.SMITHING) {
                // Smithing Table: 3 Horizontal Slots (Template, Base, Addition)
                int slotY = gridStartY + SLOT_SIZE;
                for (int i = 0; i < 3; i++) {
                    int slotX = gridStartX + i * SLOT_SIZE;
                    boolean isSelected = (selectedSlot == i);
                    boolean isDropTarget = (draggedItem != null && hoveredCraftingSlot == i);
                    drawSlotBox(context, slotX, slotY, 22, 22, isSelected, isDropTarget);

                    if (currentRecipe != null) {
                        Item item = currentRecipe.getItemAt(i);
                        if (item != Items.AIR) {
                            ItemStack stack = new ItemStack(item);
                            context.drawItem(stack, slotX + 3, slotY + 3);
                            context.drawStackOverlay(this.textRenderer, stack, slotX + 3, slotY + 3);
                        }
                        if (mouseX >= slotX && mouseX <= slotX + 22 && mouseY >= slotY && mouseY <= slotY + 22 && item != Items.AIR) {
                            hoveredStack = new ItemStack(item);
                        }
                    }
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

            if (targetItem != null && targetItem != Items.AIR) {
                int count = currentRecipe != null ? currentRecipe.getResultCountForType(selectedType) : 1;
                ItemStack resStack = new ItemStack(targetItem, count);
                context.drawItem(resStack, resultX + 6, resultY + 6);
                context.drawStackOverlay(this.textRenderer, resStack, resultX + 6, resultY + 6);
                if (mouseX >= resultX && mouseX <= resultX + 28 && mouseY >= resultY && mouseY <= resultY + 28) {
                    hoveredStack = resStack;
                }
            }

            // Active variant description text below crafting grid
            Text variantText = Text.translatable("recipeeditor.gui.craft_variant", currentVariantIndex + 1, variantTotal).formatted(Formatting.GREEN);
            context.drawTextWithShadow(this.textRenderer, variantText, leftPaneX, gridStartY + 76, 0xFF55FF55);
        }

        // --- DRAW HINTS IN A CLEAN VERTICAL COLUMN UNDER LEFT MENU BUTTONS ---
        int actionBtnY = (targetItem != null)
                ? (contentY + 22 + ((Math.min(WORKSTATIONS_PER_PAGE, RecipeTypeEnum.values().length) + 1) / 2 * 18) + 4 + 20 + 92)
                : (contentY + 140);
        int hintStartY = actionBtnY + 62;

        context.drawTextWithShadow(this.textRenderer, Text.translatable("recipeeditor.gui.hint_1").formatted(Formatting.GREEN), leftPaneX, hintStartY, 0xFF55FF55);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("recipeeditor.gui.hint_1_sub").formatted(Formatting.GREEN), leftPaneX, hintStartY + 10, 0xFF55FF55);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("recipeeditor.gui.hint_2").formatted(Formatting.GREEN), leftPaneX, hintStartY + 21, 0xFF55FF55);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("recipeeditor.gui.hint_3").formatted(Formatting.GREEN), leftPaneX, hintStartY + 32, 0xFF55FF55);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("recipeeditor.gui.hint_4").formatted(Formatting.GREEN), leftPaneX, hintStartY + 43, 0xFF55FF55);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("recipeeditor.gui.hint_5").formatted(Formatting.GREEN), leftPaneX, hintStartY + 54, 0xFF55FF55);

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

        // Notification overlay message (drawn to the left of Reset Defaults button)
        int bottomY = this.height - 24;
        if (notificationText != null && System.currentTimeMillis() < notificationTimer) {
            Text resetText = Text.translatable("recipeeditor.gui.reset_defaults");
            Text exitText = Text.translatable("recipeeditor.gui.exit");

            int wExit = Math.max(65, this.textRenderer.getWidth(exitText) + 20);
            int wReset = Math.max(120, this.textRenderer.getWidth(resetText) + 16);
            int resetX = (this.width - 12) - wExit - 6 - wReset;

            int msgW = this.textRenderer.getWidth(notificationText);
            int msgX = resetX - msgW - 10;
            context.drawTextWithShadow(this.textRenderer, notificationText, Math.max(8, msgX), bottomY + 6, 0xFF55FF55);
        }

        // Render dragged item or hover tooltip safely
        if (draggedItem != null && draggedItem != Items.AIR) {
            ItemStack dragStack = (dragSourceSlot == RESULT_SLOT && currentRecipe != null)
                    ? new ItemStack(draggedItem, currentRecipe.getResultCountForType(selectedType))
                    : new ItemStack(draggedItem);
            context.drawItem(dragStack, mouseX - 8, mouseY - 8);
            context.drawStackOverlay(this.textRenderer, dragStack, mouseX - 8, mouseY - 8);
        } else if (!hoveredStack.isEmpty()) {
            try {
                context.drawItemTooltip(this.textRenderer, hoveredStack, mouseX, mouseY);
            } catch (Throwable t) {
                try {
                    context.drawTooltip(this.textRenderer, hoveredStack.getName(), mouseX, mouseY);
                } catch (Throwable ignored) {}
            }
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
        } else if (button == 1) { // Right click: clear slot or LOAD RECIPE for catalog item
            int craftingSlot = getCraftingSlotAt(mouseX, mouseY);
            if (craftingSlot >= 0 && craftingSlot < 9) {
                if (currentRecipe != null) {
                    currentRecipe.setItemAt(craftingSlot, Items.AIR);
                    updateButtonStates();
                }
                return true;
            } else if (craftingSlot == RESULT_SLOT) {
                if (currentRecipe != null) {
                    currentRecipe.setResultItem(Items.AIR);
                    currentRecipe.resultItemId = "minecraft:air";
                    if (resultCountField != null) {
                        resultCountField.setText("1");
                    }
                    updateButtonStates();
                }
                return true;
            }

            Item catItem = getCatalogItemAt(mouseX, mouseY);
            if (catItem != null && catItem != Items.AIR) {
                loadRecipeForTarget(catItem);
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((searchField != null && searchField.isFocused()) || (resultCountField != null && resultCountField.isFocused()) || (variantField != null && variantField.isFocused())) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            clearSelectedSlot();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
