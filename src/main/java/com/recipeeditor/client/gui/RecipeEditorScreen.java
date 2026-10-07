package com.recipeeditor.client.gui;

import com.recipeeditor.RecipeEditorMod;
import com.recipeeditor.config.CustomRecipeData;
import com.recipeeditor.config.RecipeEditorConfig;
import com.recipeeditor.config.RecipeTypeEnum;
import com.recipeeditor.inspector.RecipeInspector;
import com.recipeeditor.inspector.RecipeStatus;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    private int workstationsPerPage = 10;
    private Button prevWorkstationBtn;
    private Button nextWorkstationBtn;

    // Responsive scaling
    private float uiScale = 1.0f;
    private static final int BASE_WIDTH = 460;
    private static final int BASE_HEIGHT = 380;

    // Layout coordinates
    private int leftPaneX;
    private int rightPaneX;
    private int contentY;
    private int catalogGridY;
    private int searchWidth;
    private static final int SLOT_SIZE = 24;
    private int leftPaneWidth = 196;
    private int typeBtnStartY;
    private int typeBtnsHeight;
    private int variantBarY;
    private int gridStartY;
    private int actionBtnY;

    // Widgets
    private SelectableTextFieldWidget searchField;
    private Button clearSearchBtn;
    private SelectableTextFieldWidget resultCountField;
    private SelectableTextFieldWidget cookingTimeField;
    private SelectableTextFieldWidget experienceField;
    private Button minusCountBtn;
    private Button plusCountBtn;
    private Button prevPageBtn;
    private Button nextPageBtn;
    private Button toggleEnabledBtn;
    private Button prevTabBtn;
    private Button nextTabBtn;
    private final List<Button> tabButtons = new ArrayList<>();
    private final List<Button> filterButtons = new ArrayList<>();
    private final List<Button> typeButtons = new ArrayList<>();
    private final List<RecipeTypeEnum> typeButtonEnums = new ArrayList<>();
    private Button saveCraftBtn;
    private Button clearCraftBtn;
    private Button deleteVariantBtn;
    private Button deleteRecipeBtn;
    private Button shapelessToggleBtn;
    private boolean showCustomDeleteButtons = false;
    private final Map<RecipeTypeEnum, List<CustomRecipeData>> sessionVariantsByType = new HashMap<>();
    private final Map<RecipeTypeEnum, Integer> sessionVariantIndexByType = new HashMap<>();
    private int filterY;
    private Button createRecipeBtn;
    private final Set<RecipeTypeEnum> activeCreatedTypes = new HashSet<>();
    private Button toggleHintsBtn;
    private Button resetDefaultsBtn;
    private Button exitBtn;

    // Variant Controls
    private Button prevVariantBtn;
    private Button nextVariantBtn;
    private SelectableTextFieldWidget variantField;
    private boolean isUpdatingVariantField = false;

    // Drag and drop state
    private Item draggedItem = null;
    private int dragSourceSlot = -1; // -1 for catalog, 0..8 for crafting slots, 9 for result
    private double dragStartX = 0;
    private double dragStartY = 0;

    // Status message notification
    private Component notificationText = null;
    private long notificationTimer = 0;

    // Toggleable hints (hidden by default on every screen open)
    private boolean showHints = false;

    public RecipeEditorScreen(Screen parent) {
        super(Component.translatable("recipeeditor.config.title"));
        this.parent = parent;
        this.configCopy = RecipeEditorConfig.getInstance().copy();

        RecipeInspector.invalidateWorldCache();
        RecipeEditorMod.ensureItemComponentsBound();

        // Target item starts null (user must select/RMB an item to edit)
        this.targetItem = null;
        this.currentRecipe = null;
        this.typeVariants.clear();
        this.currentVariantIndex = 0;

        // Gather all registered items
        Set<String> namespaces = new LinkedHashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                try {
                    if (!item.builtInRegistryHolder().areComponentsBound()) {
                        continue;
                    }
                    new ItemStack(item);
                } catch (Throwable ignored) {
                    continue;
                }
                allItems.add(item);
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
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
        // Trigger async indexing of all lang files via ResourceManager (catches vanilla ru_ru etc.)
        RecipeInspector.startLangIndexViaRMAsync();
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
        String flippedQuery = !query.isEmpty() ? RecipeInspector.flipKeyboardLayout(query).toLowerCase(Locale.ROOT) : "";
        boolean hasFlipped = !flippedQuery.isEmpty() && !flippedQuery.equals(query);
        var world = this.minecraft != null ? this.minecraft.level : null;

        for (Item item : allItems) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
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

            // Search query filter (matches item name, raw id, or any indexed multilingual name)
            if (!query.isEmpty()) {
                String idStr = RecipeInspector.getItemIdLower(item);
                String nameStr = RecipeInspector.getItemNameLower(item);
                boolean matches = idStr.contains(query) || nameStr.contains(query)
                        || (hasFlipped && (idStr.contains(flippedQuery) || nameStr.contains(flippedQuery)))
                        || RecipeInspector.matchesMultilingual(item, query, flippedQuery, hasFlipped);
                if (!matches) {
                    continue;
                }
            }

            filteredItems.add(item);
        }

        int maxPage = Math.max(0, (filteredItems.size() - 1) / Math.max(1, itemsPerPage));
        if (catalogPage > maxPage) {
            catalogPage = maxPage;
        }
        updatePaginationButtons();
    }

    private void refreshFilteredItemsResetPage() {
        catalogPage = 0;
        refreshFilteredItems();
    }

    public void selectTargetItem(Item item) {
        if (!isLocalWorldCreator()) return;
        if (item == null || item == Items.AIR) return;
        this.sessionVariantsByType.clear();
        this.sessionVariantIndexByType.clear();
        this.showCustomDeleteButtons = (currentFilter == CatalogFilter.CUSTOM);
        this.targetItem = item;
        this.activeCreatedTypes.clear();
        this.currentRecipe = null;
        this.currentVariantIndex = 0;
        var world = this.minecraft != null ? this.minecraft.level : null;

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
        rebuildTypeButtons();
        updateButtonStates();
    }

    private void startCreatingRecipeForTarget() {
        if (!isLocalWorldCreator()) return;
        if (targetItem == null || targetItem == Items.AIR) return;
        this.showCustomDeleteButtons = false;
        activeCreatedTypes.add(selectedType);
        refreshTypeVariants(selectedType, true);
        rebuildTypeButtons();
        updateButtonStates();
    }

    public void loadRecipeForTarget(Item item) {
        if (!isLocalWorldCreator()) return;
        selectTargetItem(item);
        if (currentFilter == CatalogFilter.CUSTOM) {
            this.showCustomDeleteButtons = true;
            updateEditorWidgetsVisibility();
            updateButtonStates();
        }
    }

    private void refreshTypeVariants(RecipeTypeEnum type, boolean resetIndex) {
        this.selectedType = type;
        this.typeVariants.clear();

        if (targetItem == null || targetItem == Items.AIR) {
            this.currentRecipe = null;
            updateEditorWidgetsVisibility();
            return;
        }

        if (sessionVariantsByType.containsKey(type)) {
            List<CustomRecipeData> saved = sessionVariantsByType.get(type);
            if (saved != null && !saved.isEmpty()) {
                for (CustomRecipeData s : saved) {
                    typeVariants.add(s.copy());
                }
                currentVariantIndex = Math.min(typeVariants.size() - 1, Math.max(0, sessionVariantIndexByType.getOrDefault(type, 0)));
                currentRecipe = typeVariants.get(currentVariantIndex);
                updateEditorWidgetsVisibility();
                updateVariantButtons();
                updateButtonStates();
                return;
            }
        }

        var world = this.minecraft != null ? this.minecraft.level : null;
        Set<String> seenSignatures = new HashSet<>();

        // 1. Collect overridden keys and IDs from active custom recipes for this targetItem and type
        List<CustomRecipeData> customList = configCopy.getRecipesFor(targetItem, type);
        Set<String> overriddenKeys = new HashSet<>();
        Set<String> overriddenIds = new HashSet<>();
        for (CustomRecipeData c : customList) {
            if (c.overrideExisting) {
                if (c.overriddenKey != null) overriddenKeys.add(c.overriddenKey);
                if (c.overriddenId != null) overriddenIds.add(c.overriddenId);
            }
        }

        // 2. Scanned / Modded / Vanilla recipes for targetItem and this machine type (only in All/Craftable filters)
        if (currentFilter != CatalogFilter.CUSTOM) {
            List<CustomRecipeData> scanned = RecipeInspector.getAllRecipeVariants(targetItem, world);
            for (CustomRecipeData s : scanned) {
                if (s.type == type) {
                    boolean isOverridden = overriddenKeys.contains(s.getKey())
                            || (s.overriddenKey != null && overriddenKeys.contains(s.overriddenKey))
                            || (s.overriddenId != null && overriddenIds.contains(s.overriddenId))
                            || (s.id != null && overriddenIds.contains(s.id));
                    if (isOverridden) {
                        continue;
                    }
                    String sig = s.getPatternSignature();
                    if (seenSignatures.add(sig)) {
                        CustomRecipeData copy = s.copy();
                        copy.overriddenId = s.overriddenId != null ? s.overriddenId : s.id;
                        copy.overriddenKey = s.overriddenKey != null ? s.overriddenKey : s.getKey();
                        copy.originalKey = s.getKey();
                        typeVariants.add(copy);
                    }
                }
            }
        }

        // 3. Custom recipes for targetItem and this machine type (always appended at the END of variants)
        for (CustomRecipeData c : customList) {
            String sig = c.getPatternSignature();
            if (seenSignatures.add(sig)) {
                CustomRecipeData copy = c.copy();
                copy.originalKey = c.getKey();
                typeVariants.add(copy);
            }
        }

        // 3. Fallback: If no recipes exist for this machine type, check if user created this type
        if (typeVariants.isEmpty()) {
            if (activeCreatedTypes.contains(type)) {
                Identifier id = BuiltInRegistries.ITEM.getKey(targetItem);
                CustomRecipeData blank = new CustomRecipeData(
                        id != null ? id.getPath() : "craft",
                        id != null ? id.toString() : "minecraft:air",
                        1,
                        type
                );
                typeVariants.add(blank);
                currentVariantIndex = 0;
                currentRecipe = blank;
            } else {
                currentVariantIndex = 0;
                currentRecipe = null;
            }
        } else {
            if (resetIndex || currentVariantIndex >= typeVariants.size() || currentVariantIndex < 0) {
                currentVariantIndex = 0;
            }
            currentRecipe = typeVariants.get(currentVariantIndex);
        }

        if (currentRecipe != null) {
            if (resultCountField != null) {
                resultCountField.setText(String.valueOf(currentRecipe.getResultCountForType(selectedType)));
            }
            if (cookingTimeField != null) {
                cookingTimeField.setText(String.valueOf(currentRecipe.cookingTime));
            }
            if (experienceField != null) {
                experienceField.setText(String.format(Locale.ROOT, "%.1f", currentRecipe.experience));
            }
        }
        updateEditorWidgetsVisibility();
        updateVariantButtons();
        updateToggleBtn(leftPaneX, contentY);
        updateButtonStates();
    }

    private void switchMachineType(RecipeTypeEnum newType) {
        if (targetItem == null) return;
        this.selectedSlot = 0;
        if (selectedType != newType && !typeVariants.isEmpty()) {
            List<CustomRecipeData> copyList = new ArrayList<>();
            for (CustomRecipeData d : typeVariants) {
                copyList.add(d.copy());
            }
            sessionVariantsByType.put(selectedType, copyList);
            sessionVariantIndexByType.put(selectedType, currentVariantIndex);
        }
        refreshTypeVariants(newType, false);
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
                Identifier id = BuiltInRegistries.ITEM.getKey(targetItem);
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
        if (cookingTimeField != null) {
            cookingTimeField.setText(String.valueOf(currentRecipe.cookingTime));
        }
        if (experienceField != null) {
            experienceField.setText(String.format(Locale.ROOT, "%.1f", currentRecipe.experience));
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
            nextVariantBtn.setMessage(Component.literal(isLast ? "+" : "▶"));
        }
        if (variantField != null) {
            isUpdatingVariantField = true;
            variantField.setText(String.valueOf(currentVariantIndex + 1));
            isUpdatingVariantField = false;
        }
    }

    private void updateEditorWidgetsVisibility() {
        boolean isCreator = isLocalWorldCreator();
        boolean enabled = configCopy.modEnabled && isCreator;
        boolean hasItem = enabled && (targetItem != null && targetItem != Items.AIR);
        boolean hasActiveCraft = hasItem && (currentRecipe != null);
        boolean isFurnaceMachine = hasActiveCraft && (selectedType == RecipeTypeEnum.SMELTING || selectedType == RecipeTypeEnum.BLASTING ||
                selectedType == RecipeTypeEnum.SMOKING || selectedType == RecipeTypeEnum.CAMPFIRE_COOKING);

        for (Button btn : typeButtons) {
            btn.visible = hasItem;
        }
        if (prevWorkstationBtn != null) prevWorkstationBtn.visible = hasItem && (RecipeTypeEnum.values().length > workstationsPerPage);
        if (nextWorkstationBtn != null) nextWorkstationBtn.visible = hasItem && (RecipeTypeEnum.values().length > workstationsPerPage);
        if (prevVariantBtn != null) prevVariantBtn.visible = hasActiveCraft;
        if (variantField != null) variantField.setVisible(hasActiveCraft);
        if (nextVariantBtn != null) nextVariantBtn.visible = hasActiveCraft;
        if (resultCountField != null) resultCountField.setVisible(hasActiveCraft);
        if (minusCountBtn != null) minusCountBtn.visible = hasActiveCraft;
        if (plusCountBtn != null) plusCountBtn.visible = hasActiveCraft;
        if (cookingTimeField != null) cookingTimeField.setVisible(isFurnaceMachine);
        if (experienceField != null) experienceField.setVisible(isFurnaceMachine);
        if (saveCraftBtn != null) saveCraftBtn.visible = hasActiveCraft;
        if (clearCraftBtn != null) clearCraftBtn.visible = hasActiveCraft;
        if (shapelessToggleBtn != null) {
            boolean isShapedTable = hasActiveCraft && (selectedType == RecipeTypeEnum.SHAPED_CRAFTING);
            shapelessToggleBtn.visible = isShapedTable;
            shapelessToggleBtn.active = enabled && hasEditPermission();
            shapelessToggleBtn.setMessage(getShapelessBtnText());
        }
        boolean isCustomCraft = targetItem != null && configCopy.hasCustomRecipe(targetItem);
        boolean showDelete = (currentFilter == CatalogFilter.CUSTOM || showCustomDeleteButtons) && (hasActiveCraft) && isCustomCraft;
        if (deleteVariantBtn != null) deleteVariantBtn.visible = showDelete;
        if (deleteRecipeBtn != null) deleteRecipeBtn.visible = showDelete;
        if (createRecipeBtn != null) createRecipeBtn.visible = (hasItem && !hasActiveCraft);
    }

    private Component getShapelessBtnText() {
        boolean shapeless = currentRecipe != null && currentRecipe.isShapeless;
        return shapeless
                ? Component.translatable("recipeeditor.gui.shapeless_btn").withStyle(ChatFormatting.AQUA)
                : Component.translatable("recipeeditor.gui.shaped_btn").withStyle(ChatFormatting.GRAY);
    }

    private void updateUiScale() {
        float scaleX = (float) this.width / (float) BASE_WIDTH;
        float scaleY = (float) this.height / (float) BASE_HEIGHT;
        this.uiScale = Math.min(1.0f, Math.min(scaleX, scaleY));
        if (this.uiScale <= 0.05f) this.uiScale = 1.0f;
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

        contentY = 24;
        int bottomY = vHeight - 22;

        // Calculate dynamic dimensions inside virtual canvas
        leftPaneWidth = 196;
        int availableTotalWidth = Math.max(300, vWidth - 20);
        int availableRightWidth = Math.max(120, availableTotalWidth - leftPaneWidth - 14);
        catalogCols = Math.max(4, availableRightWidth / SLOT_SIZE);

        int actualCatalogWidth = catalogCols * SLOT_SIZE;
        searchWidth = actualCatalogWidth - 2;

        int totalContentWidth = leftPaneWidth + 14 + actualCatalogWidth;
        leftPaneX = Math.max(8, (vWidth - totalContentWidth) / 2);
        rightPaneX = leftPaneX + leftPaneWidth + 14;

        workstationsPerPage = 8;
        typeBtnStartY = contentY + 22;
        typeBtnsHeight = 4 * 17;

        // Dynamic Rows Calculation for Right Pane
        this.filterY = contentY;
        int tabY = filterY + 17;
        int searchY = tabY + 18;
        catalogGridY = searchY + 21;
        int availableCatalogHeight = Math.max(SLOT_SIZE * 3, (bottomY - 24) - catalogGridY);
        catalogRows = Math.max(3, availableCatalogHeight / SLOT_SIZE);
        itemsPerPage = catalogCols * catalogRows;

        visibleTabs = Math.max(2, (searchWidth - 40) / 54);

        // --- LEFT PANE (Controls Aligned horizontally with Right Pane Tabs) ---
        updateToggleBtn(leftPaneX, filterY);

        // Machine Type Buttons (2 Columns)
        rebuildTypeButtons();

        // Variant Bar (Above Crafting Grid)
        variantBarY = typeBtnStartY + typeBtnsHeight + 3;
        prevVariantBtn = Button.builder(Component.literal("◀"), btn -> changeVariant(-1))
                .bounds(leftPaneX + 4, variantBarY, 18, 16)
                .build();
        this.addRenderableWidget(prevVariantBtn);

        variantField = new SelectableTextFieldWidget(this.font, leftPaneX + 24, variantBarY, 26, 16, Component.literal("Variant"));
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
                        if (cookingTimeField != null) {
                            cookingTimeField.setText(String.valueOf(currentRecipe.cookingTime));
                        }
                        if (experienceField != null) {
                            experienceField.setText(String.format(Locale.ROOT, "%.1f", currentRecipe.experience));
                        }
                        updateVariantButtons();
                        updateButtonStates();
                    }
                }
            } catch (NumberFormatException ignored) {}
        });
        this.addRenderableWidget(variantField);

        nextVariantBtn = Button.builder(Component.literal(currentVariantIndex >= typeVariants.size() - 1 ? "+" : "▶"), btn -> changeVariant(1))
                .bounds(leftPaneX + 52, variantBarY, 18, 16)
                .build();
        this.addRenderableWidget(nextVariantBtn);

        // Crafting Grid & Result Slot Layout
        int gridStartX = leftPaneX + 4;
        gridStartY = variantBarY + 30;
        int arrowX = gridStartX + 3 * SLOT_SIZE + 10;
        int resultX = arrowX + 24;
        int resultY = gridStartY + SLOT_SIZE - 3;

        // Result count controls (Centered under result slot)
        int countControlsWidth = 60;
        int countStartX = (resultX + 14) - (countControlsWidth / 2);
        int countY = resultY + 31;

        minusCountBtn = Button.builder(Component.literal("-"), btn -> adjustResultCount(-1))
                .bounds(countStartX, countY, 14, 16)
                .build();
        this.addRenderableWidget(minusCountBtn);

        resultCountField = new SelectableTextFieldWidget(this.font, countStartX + 16, countY, 28, 16, Component.literal("Count"));
        resultCountField.setText(String.valueOf(currentRecipe != null ? currentRecipe.getResultCountForType(selectedType) : 1));
        resultCountField.setMaxLength(4);
        resultCountField.setChangedListener(text -> {
            try {
                if (!text.trim().isEmpty()) {
                    int max = targetItem != null && targetItem != Items.AIR ? targetItem.getDefaultMaxStackSize() : 64;
                    int val = Integer.parseInt(text.trim());
                    if (val > max) {
                        val = max;
                        resultCountField.setText(String.valueOf(val));
                    }
                    if (val < 1) {
                        val = 1;
                        resultCountField.setText(String.valueOf(val));
                    }
                    if (currentRecipe != null) {
                        currentRecipe.setResultCountForType(selectedType, val);
                        updateButtonStates();
                    }
                }
            } catch (NumberFormatException ignored) {}
        });
        this.addRenderableWidget(resultCountField);

        plusCountBtn = Button.builder(Component.literal("+"), btn -> adjustResultCount(1))
                .bounds(countStartX + 46, countY, 14, 16)
                .build();
        this.addRenderableWidget(plusCountBtn);

        // Shapeless toggle button (Centered directly above the result slot)
        int shapelessBtnWidth = 64;
        int shapelessBtnX = (resultX + 14) - (shapelessBtnWidth / 2);
        int shapelessBtnY = resultY - 18;
        shapelessToggleBtn = Button.builder(
                getShapelessBtnText(),
                btn -> {
                    if (currentRecipe != null) {
                        currentRecipe.isShapeless = !currentRecipe.isShapeless;
                        btn.setMessage(getShapelessBtnText());
                        updateButtonStates();
                    }
                })
                .bounds(shapelessBtnX, shapelessBtnY, shapelessBtnWidth, 16)
                .build();
        this.addRenderableWidget(shapelessToggleBtn);

        // Furnace Cooking Time & Experience controls (under input slot: gridStartX + 4)
        int furnaceControlsY = countY;
        cookingTimeField = new SelectableTextFieldWidget(this.font, gridStartX + 2, furnaceControlsY, 34, 16, Component.translatable("recipeeditor.gui.label_time"));
        cookingTimeField.setText(String.valueOf(currentRecipe != null ? currentRecipe.cookingTime : 200));
        cookingTimeField.setMaxLength(5);
        cookingTimeField.setChangedListener(text -> {
            try {
                if (!text.trim().isEmpty()) {
                    int val = Integer.parseInt(text.trim());
                    if (val < 1) val = 1;
                    if (val > 72000) val = 72000;
                    if (currentRecipe != null) {
                        currentRecipe.cookingTime = val;
                        updateButtonStates();
                    }
                }
            } catch (NumberFormatException ignored) {}
        });
        this.addRenderableWidget(cookingTimeField);

        experienceField = new SelectableTextFieldWidget(this.font, gridStartX + 39, furnaceControlsY, 33, 16, Component.translatable("recipeeditor.gui.label_exp"));
        experienceField.setText(String.format(Locale.ROOT, "%.1f", currentRecipe != null ? currentRecipe.experience : 0.1f));
        experienceField.setMaxLength(5);
        experienceField.setChangedListener(text -> {
            try {
                if (!text.trim().isEmpty()) {
                    float val = Float.parseFloat(text.trim().replace(',', '.'));
                    if (val < 0.0f) val = 0.0f;
                    if (val > 100.0f) val = 100.0f;
                    if (currentRecipe != null) {
                        currentRecipe.experience = val;
                        updateButtonStates();
                    }
                }
            } catch (NumberFormatException ignored) {}
        });
        this.addRenderableWidget(experienceField);

        // Action Buttons (Full width stacked vertically)
        actionBtnY = gridStartY + 76;

        saveCraftBtn = Button.builder(Component.translatable("recipeeditor.gui.save_craft").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), btn -> saveCurrentCraft())
                .bounds(leftPaneX, actionBtnY, leftPaneWidth, 18)
                .build();
        this.addRenderableWidget(saveCraftBtn);

        clearCraftBtn = Button.builder(Component.translatable("recipeeditor.gui.clear_all").withStyle(ChatFormatting.RED), btn -> promptClearAllSlots())
                .bounds(leftPaneX, actionBtnY + 20, leftPaneWidth, 18)
                .build();
        this.addRenderableWidget(clearCraftBtn);

        deleteVariantBtn = Button.builder(Component.translatable("recipeeditor.gui.delete_variant").withStyle(ChatFormatting.RED), btn -> promptDeleteCurrentVariant())
                .bounds(leftPaneX, actionBtnY + 40, leftPaneWidth, 18)
                .build();
        this.addRenderableWidget(deleteVariantBtn);

        deleteRecipeBtn = Button.builder(Component.translatable("recipeeditor.gui.delete_custom").withStyle(ChatFormatting.RED), btn -> promptDeleteEntireCustomCraft())
                .bounds(leftPaneX, actionBtnY + 60, leftPaneWidth, 18)
                .build();
        this.addRenderableWidget(deleteRecipeBtn);

        // Create Recipe Button for uncraftable items / machines without recipes
        int createBtnY = gridStartY + 38;
        createRecipeBtn = Button.builder(Component.translatable("recipeeditor.gui.create_recipe_btn").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), btn -> startCreatingRecipeForTarget())
                .bounds(leftPaneX + 10, createBtnY, leftPaneWidth - 20, 20)
                .build();
        this.addRenderableWidget(createRecipeBtn);

        // Set initial visibility based on targetItem selection
        updateEditorWidgetsVisibility();
        updateVariantButtons();

        // --- RIGHT PANE (Filters, Tabs, Search, Dynamic Catalog) ---
        rebuildFilterButtons();

        prevTabBtn = Button.builder(Component.literal("◀"), btn -> scrollTabs(-1))
                .bounds(rightPaneX, tabY, 18, 16)
                .build();
        nextTabBtn = Button.builder(Component.literal("▶"), btn -> scrollTabs(1))
                .bounds(rightPaneX + searchWidth - 18, tabY, 18, 16)
                .build();
        this.addRenderableWidget(prevTabBtn);
        this.addRenderableWidget(nextTabBtn);
        rebuildTabButtons(rightPaneX + 22, tabY);

        // Search Field & Clear Button
        String previousSearch = searchField != null ? searchField.getText() : "";
        searchField = new SelectableTextFieldWidget(this.font, rightPaneX, searchY, searchWidth, 18, Component.literal("Search"));
        searchField.setText(previousSearch);
        searchField.setPlaceholder(Component.translatable("recipeeditor.gui.search_placeholder").withStyle(ChatFormatting.GRAY));
        searchField.setChangedListener(query -> {
            updateSearchClearButtonState();
            refreshFilteredItemsResetPage();
        });
        this.addRenderableWidget(searchField);

        int clearBtnSize = 18;
        int clearBtnX = rightPaneX + searchWidth - clearBtnSize;
        int clearBtnY = searchY;
        clearSearchBtn = Button.builder(Component.literal("✕").withStyle(ChatFormatting.RED), btn -> {
            if (searchField != null) {
                searchField.setText("");
                searchField.setFocused(true);
                updateSearchClearButtonState();
                refreshFilteredItemsResetPage();
            }
        }).bounds(clearBtnX, clearBtnY, clearBtnSize, clearBtnSize).build();
        this.addRenderableWidget(clearSearchBtn);
        updateSearchClearButtonState();

        // Catalog Pagination Buttons
        int pageBtnY = catalogGridY + (catalogRows * SLOT_SIZE) + 4;
        prevPageBtn = Button.builder(Component.literal("◀"), btn -> changePage(-1))
                .bounds(rightPaneX, pageBtnY, 24, 16)
                .build();
        nextPageBtn = Button.builder(Component.literal("▶"), btn -> changePage(1))
                .bounds(rightPaneX + searchWidth - 24, pageBtnY, 24, 16)
                .build();
        this.addRenderableWidget(prevPageBtn);
        this.addRenderableWidget(nextPageBtn);
        updatePaginationButtons();

        // --- BOTTOM PANE (Controls in bottom-right corner) ---
        rebuildBottomButtons(bottomY);

        updateButtonStates();
    }

    private void toggleHintsVisibility() {
        this.showHints = !this.showHints;
        updateToggleHintsBtnText();
    }

    private void updateToggleHintsBtnText() {
        if (toggleHintsBtn != null) {
            Component msg = showHints
                    ? Component.translatable("recipeeditor.gui.hide_hints")
                    : Component.translatable("recipeeditor.gui.show_hints");
            toggleHintsBtn.setMessage(msg);
        }
    }

    private void rebuildBottomButtons(int bottomY) {
        Component resetText = Component.translatable("recipeeditor.gui.reset_defaults");
        Component exitText = Component.translatable("recipeeditor.gui.exit");
        Component showHintsText = Component.translatable("recipeeditor.gui.show_hints");
        Component hideHintsText = Component.translatable("recipeeditor.gui.hide_hints");

        int wExit = Math.max(55, this.font.width(exitText) + 16);
        int wReset = Math.max(100, this.font.width(resetText) + 16);
        int wHints = Math.max(100, Math.max(this.font.width(showHintsText), this.font.width(hideHintsText)) + 16);

        int rightMargin = getVirtualWidth() - 8;
        int exitX = rightMargin - wExit;
        int resetX = exitX - 4 - wReset;
        int hintsX = resetX - 4 - wHints;

        Component initialMsg = showHints ? hideHintsText : showHintsText;
        toggleHintsBtn = Button.builder(initialMsg, btn -> toggleHintsVisibility())
                .bounds(hintsX, bottomY, wHints, 18)
                .build();
        this.addRenderableWidget(toggleHintsBtn);

        resetDefaultsBtn = Button.builder(resetText, btn -> promptResetDefaults())
                .bounds(resetX, bottomY, wReset, 18)
                .build();
        this.addRenderableWidget(resetDefaultsBtn);

        exitBtn = Button.builder(exitText, btn -> this.onClose())
                .bounds(exitX, bottomY, wExit, 18)
                .build();
        this.addRenderableWidget(exitBtn);
    }

    private void promptResetDefaults() {
        if (this.minecraft == null) return;
        this.minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        resetDefaults();
                    }
                    this.minecraft.setScreen(this);
                },
                Component.translatable("recipeeditor.gui.reset_confirm_title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.translatable("recipeeditor.gui.reset_confirm_msg")
        ));
    }

    private void promptDeleteCurrentVariant() {
        if (this.minecraft == null || targetItem == null || currentRecipe == null) return;
        this.minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        deleteCurrentVariant();
                    }
                    this.minecraft.setScreen(this);
                },
                Component.translatable("recipeeditor.gui.delete_variant_confirm_title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.translatable("recipeeditor.gui.delete_variant_confirm_msg")
        ));
    }

    private void promptDeleteEntireCustomCraft() {
        if (this.minecraft == null || targetItem == null) return;
        this.minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        deleteEntireCustomCraft();
                    }
                    this.minecraft.setScreen(this);
                },
                Component.translatable("recipeeditor.gui.delete_confirm_title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.translatable("recipeeditor.gui.delete_confirm_msg")
        ));
    }

    private boolean isExactRecipeMatch(CustomRecipeData a, CustomRecipeData b, RecipeTypeEnum type) {
        if (a == null || b == null) return false;
        if (a.type != type || b.type != type) return false;
        if (a.getResultCountForType(type) != b.getResultCountForType(type)) return false;
        if (a.isShapeless != b.isShapeless) return false;

        if (type == RecipeTypeEnum.SMELTING || type == RecipeTypeEnum.BLASTING ||
            type == RecipeTypeEnum.SMOKING || type == RecipeTypeEnum.CAMPFIRE_COOKING) {
            return a.getItemAt(0) == b.getItemAt(0) &&
                   a.cookingTime == b.cookingTime &&
                   Math.abs(a.experience - b.experience) < 0.001f;
        }

        if (type == RecipeTypeEnum.STONECUTTING) {
            return a.getItemAt(0) == b.getItemAt(0);
        }

        if (type == RecipeTypeEnum.SMITHING) {
            for (int i = 0; i < 3; i++) {
                if (a.getItemAt(i) != b.getItemAt(i)) return false;
            }
            return true;
        }

        // Shaped Crafting 3x3: direct and horizontal mirror match
        boolean directMatch = true;
        for (int i = 0; i < 9; i++) {
            if (a.getItemAt(i) != b.getItemAt(i)) {
                directMatch = false;
                break;
            }
        }
        if (directMatch) return true;

        boolean mirrorMatch = true;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (a.getItemAt(r * 3 + c) != b.getItemAt(r * 3 + (2 - c))) {
                    mirrorMatch = false;
                    break;
                }
            }
            if (!mirrorMatch) break;
        }
        return mirrorMatch;
    }

    private boolean isRecipeSavable(CustomRecipeData recipe, Item targetItem, RecipeTypeEnum type) {
        if (recipe == null || targetItem == null || targetItem == Items.AIR) return false;
        if (!hasAnyIngredients(recipe)) return false;

        var world = this.minecraft != null ? this.minecraft.level : null;

        // 1. If it's already saved in config with exact same pattern and count, no unsaved changes
        List<CustomRecipeData> savedCustom = configCopy.getRecipesFor(targetItem, type);
        for (CustomRecipeData saved : savedCustom) {
            if (isExactRecipeMatch(saved, recipe, type)) {
                return false;
            }
        }

        // 2. If it exactly matches an existing vanilla / modded recipe for targetItem, it is unmodified
        List<CustomRecipeData> scanned = RecipeInspector.getAllRecipeVariants(targetItem, world);
        for (CustomRecipeData v : scanned) {
            if (v.type == type && isExactRecipeMatch(v, recipe, type)) {
                return false; // Unmodified vanilla / modded recipe!
            }
        }

        return true;
    }

    private boolean isAnyVariantSavable() {
        if (targetItem == null || targetItem == Items.AIR) return false;
        for (CustomRecipeData v : typeVariants) {
            if (isRecipeSavable(v, targetItem, selectedType)) {
                return true;
            }
        }
        return false;
    }

    public boolean isLocalWorldCreator() {
        if (this.minecraft == null) return false;
        // In the Main Menu (no active world/player), editing local client configuration is always permitted
        if (this.minecraft.level == null || this.minecraft.player == null) {
            return true;
        }
        // In a loaded world: allow only if in singleplayer or hosting an integrated server (LAN host)
        if (this.minecraft.getSingleplayerServer() != null && this.minecraft.isLocalServer()) {
            return this.minecraft.isSingleplayer() || this.minecraft.getSingleplayerServer().isSingleplayerOwner(new net.minecraft.server.players.NameAndId(this.minecraft.player.getGameProfile()));
        }
        return false;
    }

    public boolean hasEditPermission() {
        return isLocalWorldCreator();
    }

    private void updateButtonStates() {
        boolean isCreator = isLocalWorldCreator();
        boolean enabled = configCopy.modEnabled && isCreator;
        boolean canEdit = enabled;

        if (toggleEnabledBtn != null) {
            toggleEnabledBtn.active = isCreator;
        }

        if (deleteVariantBtn != null) {
            deleteVariantBtn.active = canEdit && targetItem != null && currentRecipe != null && configCopy.hasCustomRecipe(targetItem);
        }
        if (deleteRecipeBtn != null) {
            deleteRecipeBtn.active = canEdit && targetItem != null && currentRecipe != null && configCopy.hasCustomRecipe(targetItem);
        }
        if (saveCraftBtn != null) {
            saveCraftBtn.active = canEdit && currentRecipe != null && isAnyVariantSavable();
        }
        if (clearCraftBtn != null) {
            clearCraftBtn.active = canEdit && currentRecipe != null && hasAnyIngredients(currentRecipe);
        }
        if (createRecipeBtn != null) {
            createRecipeBtn.active = canEdit;
        }
        if (resetDefaultsBtn != null) {
            resetDefaultsBtn.active = canEdit;
        }
        for (Button btn : typeButtons) {
            btn.active = enabled;
        }
        RecipeTypeEnum[] allTypes = RecipeTypeEnum.values();
        int maxPages = Math.max(1, (allTypes.length + workstationsPerPage - 1) / workstationsPerPage);
        if (prevWorkstationBtn != null) {
            prevWorkstationBtn.active = enabled && workstationPage > 0;
        }
        if (nextWorkstationBtn != null) {
            nextWorkstationBtn.active = enabled && workstationPage < maxPages - 1;
        }
        if (prevVariantBtn != null) {
            prevVariantBtn.active = enabled && currentVariantIndex > 0;
        }
        if (nextVariantBtn != null) {
            nextVariantBtn.active = enabled;
        }
        if (minusCountBtn != null) {
            minusCountBtn.active = enabled;
        }
        if (plusCountBtn != null) {
            plusCountBtn.active = enabled;
        }
        if (variantField != null) {
            variantField.setEditable(enabled);
        }
        if (resultCountField != null) {
            resultCountField.setEditable(enabled);
        }
        if (cookingTimeField != null) {
            cookingTimeField.setEditable(enabled);
        }
        if (experienceField != null) {
            experienceField.setEditable(enabled);
        }
        for (Button btn : filterButtons) {
            btn.active = enabled;
        }
        for (Button btn : tabButtons) {
            btn.active = enabled;
        }
        if (prevTabBtn != null) {
            prevTabBtn.active = enabled && tabScrollOffset > 0;
        }
        if (nextTabBtn != null) {
            int maxOffset = Math.max(0, modTabs.size() - visibleTabs);
            nextTabBtn.active = enabled && tabScrollOffset < maxOffset;
        }
        if (searchField != null) {
            searchField.setEditable(enabled);
        }
        updateSearchClearButtonState();
        if (prevPageBtn != null) {
            prevPageBtn.active = enabled && catalogPage > 0;
        }
        if (nextPageBtn != null) {
            int maxPage = Math.max(0, (filteredItems.size() - 1) / Math.max(1, itemsPerPage));
            nextPageBtn.active = enabled && catalogPage < maxPage;
        }
        if (resetDefaultsBtn != null) {
            resetDefaultsBtn.active = enabled;
        }
        if (toggleHintsBtn != null) {
            toggleHintsBtn.active = enabled;
        }
    }

    private void updateSearchClearButtonState() {
        if (clearSearchBtn == null || searchField == null) return;
        boolean hasText = !searchField.getText().isEmpty();
        boolean enabled = configCopy.modEnabled && isLocalWorldCreator();
        clearSearchBtn.visible = hasText;
        clearSearchBtn.active = hasText && enabled;
        if (hasText) {
            searchField.setWidth(searchWidth - 20);
        } else {
            searchField.setWidth(searchWidth);
        }
    }

    private void rebuildFilterButtons() {
        rebuildFilterButtons(rightPaneX, filterY);
    }

    private void rebuildFilterButtons(int startX, int startY) {
        for (Button btn : filterButtons) {
            this.removeWidget(btn);
        }
        filterButtons.clear();

        CatalogFilter[] filters = CatalogFilter.values();
        int btnWidth = (searchWidth - (filters.length - 1) * 2) / filters.length;

        for (int i = 0; i < filters.length; i++) {
            CatalogFilter f = filters[i];
            boolean isSelected = (f == currentFilter);
            ChatFormatting color = isSelected ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
            MutableComponent label = Component.translatable(f.translationKey).withStyle(color);
            if (f == CatalogFilter.CUSTOM && configCopy != null && configCopy.hasAnyCustomRecipes()) {
                label = label.withStyle(ChatFormatting.UNDERLINE);
            }

            Button btn = Button.builder(label, b -> {
                if (currentFilter != f) {
                    showCustomDeleteButtons = (f == CatalogFilter.CUSTOM);
                }
                currentFilter = f;
                refreshFilteredItemsResetPage();
                if (f == CatalogFilter.CUSTOM) {
                    if (targetItem == null || !configCopy.hasCustomRecipe(targetItem)) {
                        if (!filteredItems.isEmpty()) {
                            loadRecipeForTarget(filteredItems.get(0));
                        }
                    } else {
                        loadRecipeForTarget(targetItem);
                    }
                }
                updateEditorWidgetsVisibility();
                updateButtonStates();
                rebuildFilterButtons(startX, startY);
            }).bounds(startX + i * (btnWidth + 2), startY, btnWidth, 15).build();

            filterButtons.add(btn);
            this.addRenderableWidget(btn);
        }
    }

    private void rebuildTypeButtons() {
        for (Button btn : typeButtons) {
            this.removeWidget(btn);
        }
        typeButtons.clear();
        typeButtonEnums.clear();
        if (prevWorkstationBtn != null) {
            this.removeWidget(prevWorkstationBtn);
            prevWorkstationBtn = null;
        }
        if (nextWorkstationBtn != null) {
            this.removeWidget(nextWorkstationBtn);
            nextWorkstationBtn = null;
        }

        RecipeTypeEnum[] allTypes = RecipeTypeEnum.values();
        int totalTypes = allTypes.length;
        int maxPages = Math.max(1, (totalTypes + workstationsPerPage - 1) / workstationsPerPage);
        workstationPage = Math.max(0, Math.min(maxPages - 1, workstationPage));

        int btnW = (leftPaneWidth - 4) / 2;
        int btnH = 15;
        int btnSpacing = 17;

        int startIdx = workstationPage * workstationsPerPage;
        int endIdx = Math.min(totalTypes, startIdx + workstationsPerPage);

        for (int i = startIdx; i < endIdx; i++) {
            RecipeTypeEnum t = allTypes[i];
            boolean isSelected = (selectedType == t);
            Component label = t.getDisplayName().copy().withStyle(isSelected ? ChatFormatting.YELLOW : ChatFormatting.GRAY);

            int localIdx = i - startIdx;
            int row = localIdx / 2;
            int col = localIdx % 2;
            int x = leftPaneX + col * (btnW + 4);
            int y = typeBtnStartY + row * btnSpacing;

            Button btn = Button.builder(label, b -> switchMachineType(t))
                    .bounds(x, y, btnW, btnH)
                    .build();

            btn.visible = (targetItem != null);
            typeButtons.add(btn);
            typeButtonEnums.add(t);
            this.addRenderableWidget(btn);
        }

        // Add pager controls if more workstations than workstationsPerPage exist
        if (totalTypes > workstationsPerPage) {
            int pagerY = typeBtnStartY - 13;
            prevWorkstationBtn = Button.builder(Component.literal("◀"), b -> {
                if (workstationPage > 0) {
                    workstationPage--;
                    rebuildTypeButtons();
                }
            }).bounds(leftPaneX, pagerY, 16, 11).build();
            prevWorkstationBtn.active = workstationPage > 0;
            prevWorkstationBtn.visible = (targetItem != null);
            this.addRenderableWidget(prevWorkstationBtn);

            nextWorkstationBtn = Button.builder(Component.literal("▶"), b -> {
                if (workstationPage < maxPages - 1) {
                    workstationPage++;
                    rebuildTypeButtons();
                }
            }).bounds(leftPaneX + leftPaneWidth - 16, pagerY, 16, 11).build();
            nextWorkstationBtn.active = workstationPage < maxPages - 1;
            nextWorkstationBtn.visible = (targetItem != null);
            this.addRenderableWidget(nextWorkstationBtn);
        }
    }

    private void scrollTabs(int delta) {
        int maxOffset = Math.max(0, modTabs.size() - visibleTabs);
        tabScrollOffset = Math.max(0, Math.min(maxOffset, tabScrollOffset + delta));
        rebuildTabButtons(rightPaneX + 22, contentY + 17);
    }

    private void rebuildTabButtons(int startX, int startY) {
        for (Button btn : tabButtons) {
            this.removeWidget(btn);
        }
        tabButtons.clear();

        int maxOffset = Math.max(0, modTabs.size() - visibleTabs);
        if (prevTabBtn != null) prevTabBtn.active = tabScrollOffset > 0;
        if (nextTabBtn != null) nextTabBtn.active = tabScrollOffset < maxOffset;

        int availableTabsWidth = searchWidth - 40;
        int tabWidth = Math.max(34, (availableTabsWidth - (visibleTabs - 1) * 2) / Math.max(1, visibleTabs));

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
            Component tabText = isSelected
                    ? Component.literal(label).withStyle(ChatFormatting.YELLOW)
                    : Component.literal(label).withStyle(ChatFormatting.GRAY);

            final int chosenIdx = tabIndex;
            Button btn = Button.builder(tabText, b -> {
                selectedTabIdx = chosenIdx;
                showCustomDeleteButtons = (currentFilter == CatalogFilter.CUSTOM);
                updateEditorWidgetsVisibility();
                updateButtonStates();
                refreshFilteredItemsResetPage();
                rebuildTabButtons(startX, startY);
            }).bounds(startX + (i * (tabWidth + 2)), startY, tabWidth, 16).build();

            tabButtons.add(btn);
            this.addRenderableWidget(btn);
        }
    }

    private void updateToggleBtn(int x, int y) {
        if (toggleEnabledBtn != null) {
            this.removeWidget(toggleEnabledBtn);
        }
        boolean isCreator = isLocalWorldCreator();
        boolean enabled = configCopy.modEnabled;
        Component toggleText = enabled
                ? Component.translatable("recipeeditor.gui.recipe_enabled").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                : Component.translatable("recipeeditor.gui.recipe_disabled").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        toggleEnabledBtn = Button.builder(toggleText, btn -> {
            if (!isLocalWorldCreator()) return;
            configCopy.modEnabled = !configCopy.modEnabled;
            RecipeEditorConfig actual = RecipeEditorConfig.getInstance();
            actual.modEnabled = configCopy.modEnabled;
            actual.save();
            actual.invalidateAllRecipeCaches();
            if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
                com.recipeeditor.recipe.CustomRecipeDispatcher.syncRecipeBookToPlayers(this.minecraft.getSingleplayerServer().getPlayerList().getPlayers());
                syncStonecutterRecipes();
            }
            updateToggleBtn(x, y);
            refreshFilteredItems();
            updateButtonStates();
            updateEditorWidgetsVisibility();
        }).bounds(x, y, leftPaneWidth, 18).build();
        toggleEnabledBtn.active = isCreator;
        this.addRenderableWidget(toggleEnabledBtn);
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
            int max = targetItem != null && targetItem != Items.AIR ? targetItem.getDefaultMaxStackSize() : 64;
            int currentCount = currentRecipe.getResultCountForType(selectedType);
            int newCount = Math.max(1, Math.min(max, currentCount + delta));
            currentRecipe.setResultCountForType(selectedType, newCount);
            if (resultCountField != null) {
                resultCountField.setText(String.valueOf(newCount));
            }
            updateButtonStates();
        }
    }

    private void clearSelectedSlot() {
        if (selectedSlot == RESULT_SLOT) {
            return;
        }
        if (currentRecipe == null) return;
        if (selectedSlot >= 0 && selectedSlot < 9) {
            currentRecipe.setItemAt(selectedSlot, Items.AIR);
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

    private void syncStonecutterRecipes() {
        if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
            try {
                var recipeManager = this.minecraft.getSingleplayerServer().getRecipeManager();
                var stonecutterPacket = new ClientboundUpdateRecipesPacket(
                        recipeManager.getSynchronizedItemProperties(),
                        recipeManager.getSynchronizedStonecutterRecipes());
                for (ServerPlayer p : this.minecraft.getSingleplayerServer().getPlayerList().getPlayers()) {
                    p.connection.send(stonecutterPacket);
                }
            } catch (Throwable ignored) {}
        }
    }

    private void promptClearAllSlots() {
        if (!hasEditPermission() || currentRecipe == null) return;
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ConfirmScreen(
                    confirmed -> {
                        if (confirmed) {
                            clearAllSlots();
                        }
                        this.minecraft.setScreen(this);
                    },
                    Component.translatable("recipeeditor.gui.clear_confirm_title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    Component.translatable("recipeeditor.gui.clear_confirm_msg")
            ));
        }
    }

    private void saveCurrentCraft() {
        if (!hasEditPermission() || targetItem == null || targetItem == Items.AIR) return;

        List<CustomRecipeData> toSave = new ArrayList<>();
        for (CustomRecipeData v : typeVariants) {
            if (isRecipeSavable(v, targetItem, selectedType)) {
                toSave.add(v);
            }
        }

        if (toSave.isEmpty()) return;

        // Check for conflicts with existing vanilla, mod, or custom recipes
        List<com.recipeeditor.inspector.RecipeConflictInfo> allConflicts = new ArrayList<>();
        for (CustomRecipeData recipe : toSave) {
            recipe.setResultItem(targetItem);
            recipe.type = selectedType;
            if (recipe.getResultCountForType(selectedType) <= 0) {
                recipe.setResultCountForType(selectedType, 1);
            }

            List<com.recipeeditor.inspector.RecipeConflictInfo> conflicts = RecipeInspector.findConflicts(
                    recipe,
                    targetItem,
                    this.minecraft != null ? this.minecraft.level : null,
                    configCopy
            );
            if (!conflicts.isEmpty()) {
                allConflicts.addAll(conflicts);
            }
        }

        if (!allConflicts.isEmpty()) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new RecipeConflictScreen(this, allConflicts, () -> {
                    RecipeEditorConfig actual = RecipeEditorConfig.getInstance();
                    // Point 4: Delete conflicting custom recipe if it was overridden by this new recipe
                    for (com.recipeeditor.inspector.RecipeConflictInfo info : allConflicts) {
                        if (info.conflictingRecipeKey != null && !info.conflictingRecipeKey.isEmpty()) {
                            configCopy.removeRecipeByKey(info.conflictingRecipeKey);
                            actual.removeRecipeByKey(info.conflictingRecipeKey);
                        }
                    }

                    // Point 3: Only apply conflict overrides to the recipe that actually generated the conflict
                    for (CustomRecipeData recipe : toSave) {
                        for (com.recipeeditor.inspector.RecipeConflictInfo info : allConflicts) {
                            if (info.attemptedRecipe == recipe) {
                                recipe.overrideExisting = true;
                                if (info.conflictingRecipeId != null && !info.conflictingRecipeId.isEmpty()) {
                                    recipe.overriddenId = info.conflictingRecipeId;
                                }
                                if (info.conflictingRecipeKey != null && !info.conflictingRecipeKey.isEmpty()) {
                                    recipe.overriddenKey = info.conflictingRecipeKey;
                                }
                            }
                        }
                    }
                    executeSaveCraft(toSave);
                }));
            }
            return;
        }

        executeSaveCraft(toSave);
    }

    private void executeSaveCraft(List<CustomRecipeData> toSave) {
        RecipeEditorConfig actual = RecipeEditorConfig.getInstance();
        String savedSig = currentRecipe != null ? currentRecipe.getPatternSignature() : null;

        boolean stonecutterChanged = false;
        for (CustomRecipeData recipe : toSave) {
            if (recipe.overriddenKey != null || recipe.overriddenId != null) {
                recipe.overrideExisting = true;
            }
            if (recipe.originalKey != null && !recipe.originalKey.equals(recipe.getKey())) {
                CustomRecipeData old = configCopy.recipes.get(recipe.originalKey);
                if (old != null && old.type == RecipeTypeEnum.STONECUTTING) {
                    stonecutterChanged = true;
                }
                configCopy.removeRecipeByKey(recipe.originalKey);
                actual.removeRecipeByKey(recipe.originalKey);
            }
            if (recipe.type == RecipeTypeEnum.STONECUTTING) {
                stonecutterChanged = true;
            }
            recipe.originalKey = recipe.getKey();

            configCopy.addOrUpdateRecipe(recipe.copy());
            actual.addOrUpdateRecipe(recipe.copy());
            com.recipeeditor.integration.RecipeViewerIntegration.updateRecipeInViewers(recipe, recipe.originalKey);
        }
        actual.save();
        actual.invalidateAllRecipeCaches();
        if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
            com.recipeeditor.recipe.CustomRecipeDispatcher.syncRecipeBookToPlayers(this.minecraft.getSingleplayerServer().getPlayerList().getPlayers());
            if (stonecutterChanged) {
                syncStonecutterRecipes();
            }
        }

        sessionVariantsByType.clear();
        sessionVariantIndexByType.clear();

        notificationText = Component.translatable("recipeeditor.gui.craft_saved").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        notificationTimer = System.currentTimeMillis() + 3000;

        refreshTypeVariants(selectedType, false);

        if (savedSig != null) {
            for (int i = 0; i < typeVariants.size(); i++) {
                if (savedSig.equals(typeVariants.get(i).getPatternSignature())) {
                    currentVariantIndex = i;
                    currentRecipe = typeVariants.get(i);
                    break;
                }
            }
        }

        updateVariantButtons();
        updateButtonStates();
        refreshFilteredItems();
        rebuildFilterButtons();
    }

    private void deleteCurrentVariant() {
        if (!hasEditPermission() || targetItem == null || currentRecipe == null) return;
        sessionVariantsByType.clear();
        sessionVariantIndexByType.clear();

        boolean deleted = false;
        boolean stonecutterChanged = false;
        if (currentRecipe.originalKey != null && !currentRecipe.originalKey.isEmpty()) {
            CustomRecipeData old = configCopy.recipes.get(currentRecipe.originalKey);
            if (old != null && old.type == RecipeTypeEnum.STONECUTTING) {
                stonecutterChanged = true;
            }
            configCopy.removeRecipeByKey(currentRecipe.originalKey);
            RecipeEditorConfig.getInstance().removeRecipeByKey(currentRecipe.originalKey);
            deleted = true;
        }

        String currentKey = currentRecipe.getKey();
        if (!currentKey.equals(currentRecipe.originalKey) && (configCopy.recipes.containsKey(currentKey) || configCopy.hasCustomRecipe(targetItem))) {
            if (currentRecipe.type == RecipeTypeEnum.STONECUTTING) {
                stonecutterChanged = true;
            }
            configCopy.removeRecipe(currentRecipe);
            RecipeEditorConfig.getInstance().removeRecipe(currentRecipe);
            deleted = true;
        }

        if (deleted) {
            RecipeEditorConfig.getInstance().save();
            RecipeEditorConfig.getInstance().invalidateAllRecipeCaches();
            if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
                com.recipeeditor.recipe.CustomRecipeDispatcher.syncRecipeBookToPlayers(this.minecraft.getSingleplayerServer().getPlayerList().getPlayers());
                if (stonecutterChanged) {
                    syncStonecutterRecipes();
                }
            }
            if (currentRecipe != null) {
                com.recipeeditor.integration.RecipeViewerIntegration.removeRecipeFromViewers(currentRecipe.getKey());
            }
        }

        notificationText = Component.translatable("recipeeditor.gui.variant_deleted").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        notificationTimer = System.currentTimeMillis() + 3000;

        refreshTypeVariants(selectedType, false);

        if (typeVariants.isEmpty()) {
            this.targetItem = null;
            this.currentRecipe = null;
            this.typeVariants.clear();
            this.activeCreatedTypes.clear();
            this.currentVariantIndex = 0;
            this.showCustomDeleteButtons = false;
        } else {
            if (currentVariantIndex >= typeVariants.size()) {
                currentVariantIndex = typeVariants.size() - 1;
            }
            currentRecipe = typeVariants.get(currentVariantIndex);
            if (resultCountField != null) {
                resultCountField.setText(String.valueOf(currentRecipe.getResultCountForType(selectedType)));
            }
        }
        updateVariantButtons();
        updateButtonStates();
        updateEditorWidgetsVisibility();

        refreshFilteredItems();
        rebuildFilterButtons();
    }

    private void deleteEntireCustomCraft() {
        if (!hasEditPermission() || targetItem == null) return;
        sessionVariantsByType.clear();
        sessionVariantIndexByType.clear();

        boolean stonecutterChanged = false;
        List<CustomRecipeData> toDelete = new ArrayList<>(configCopy.getRecipesFor(targetItem));
        for (CustomRecipeData r : toDelete) {
            if (r.type == RecipeTypeEnum.STONECUTTING) {
                stonecutterChanged = true;
            }
            configCopy.removeRecipe(r);
            RecipeEditorConfig actual = RecipeEditorConfig.getInstance();
            actual.removeRecipe(r);
            com.recipeeditor.integration.RecipeViewerIntegration.removeRecipeFromViewers(r.getKey());
        }

        RecipeEditorConfig.getInstance().save();
        RecipeEditorConfig.getInstance().invalidateAllRecipeCaches();
        if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
            com.recipeeditor.recipe.CustomRecipeDispatcher.syncRecipeBookToPlayers(this.minecraft.getSingleplayerServer().getPlayerList().getPlayers());
            if (stonecutterChanged) {
                syncStonecutterRecipes();
            }
        }

        notificationText = Component.translatable("recipeeditor.gui.craft_deleted").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        notificationTimer = System.currentTimeMillis() + 3000;

        if (currentFilter == CatalogFilter.CUSTOM) {
            this.targetItem = null;
            this.currentRecipe = null;
            this.typeVariants.clear();
            this.activeCreatedTypes.clear();
            this.currentVariantIndex = 0;
            this.showCustomDeleteButtons = false;
        } else {
            refreshTypeVariants(selectedType, true);
            if (typeVariants.isEmpty()) {
                this.targetItem = null;
                this.currentRecipe = null;
                this.activeCreatedTypes.clear();
                this.currentVariantIndex = 0;
                this.showCustomDeleteButtons = false;
            } else {
                currentRecipe = typeVariants.get(0);
                if (resultCountField != null) {
                    resultCountField.setText(String.valueOf(currentRecipe.getResultCountForType(selectedType)));
                }
            }
        }

        updateEditorWidgetsVisibility();
        updateButtonStates();
        refreshFilteredItems();
        rebuildFilterButtons();
    }

    private void resetDefaults() {
        if (!hasEditPermission()) return;
        sessionVariantsByType.clear();
        sessionVariantIndexByType.clear();

        configCopy.initDefaults();
        RecipeEditorConfig actual = RecipeEditorConfig.getInstance();
        actual.initDefaults();
        actual.save();
        actual.invalidateAllRecipeCaches();
        if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
            com.recipeeditor.recipe.CustomRecipeDispatcher.syncRecipeBookToPlayers(this.minecraft.getSingleplayerServer().getPlayerList().getPlayers());
            syncStonecutterRecipes();
        }

        this.targetItem = null;
        this.currentRecipe = null;
        this.typeVariants.clear();
        this.activeCreatedTypes.clear();
        this.currentVariantIndex = 0;
        this.showCustomDeleteButtons = false;

        updateEditorWidgetsVisibility();
        updateButtonStates();
        refreshFilteredItems();
        rebuildFilterButtons();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private int getCraftingSlotAt(double mouseX, double mouseY) {
        if (targetItem == null) return -1;

        int gridStartX = leftPaneX + 4;

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
        if (!configCopy.modEnabled || !isLocalWorldCreator()) {
            return false;
        }
        double sX = mouseX / uiScale;
        double sY = mouseY / uiScale;
        int tabY = contentY + 17;
        int tabHeight = 18;
        int catalogHeight = (catalogRows * SLOT_SIZE) + 10;

        // 1. Mouse wheel over Mod Tabs row: scroll tabs
        if (sX >= rightPaneX && sX <= rightPaneX + searchWidth &&
                sY >= tabY && sY <= tabY + tabHeight) {
            if (verticalAmount > 0) {
                scrollTabs(-1);
                return true;
            } else if (verticalAmount < 0) {
                scrollTabs(1);
                return true;
            }
        }

        // 2. Mouse wheel over Catalog Grid: scroll catalog pages
        if (sX >= rightPaneX && sX <= rightPaneX + searchWidth &&
                sY >= catalogGridY && sY <= catalogGridY + catalogHeight) {
            if (verticalAmount > 0) {
                changePage(-1);
                return true;
            } else if (verticalAmount < 0) {
                changePage(1);
                return true;
            }
        }

        // 3. Mouse wheel over Crafting Grid / Left Pane: scroll through craft variants (like catalog pages)
        int craftAreaStartY = variantBarY;
        int craftAreaEndY = (actionBtnY > 0) ? (actionBtnY + 40) : (gridStartY + 3 * SLOT_SIZE + 20);
        if (sX >= leftPaneX && sX <= leftPaneX + leftPaneWidth &&
                sY >= craftAreaStartY && sY <= craftAreaEndY && targetItem != null && !typeVariants.isEmpty()) {
            if (verticalAmount > 0) {
                if (currentVariantIndex > 0) {
                    changeVariant(-1);
                    return true;
                }
            } else if (verticalAmount < 0) {
                if (currentVariantIndex < typeVariants.size() - 1) {
                    changeVariant(1);
                    return true;
                }
            }
        }

        return super.mouseScrolled(sX, sY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        DrawContext context = new DrawContext(graphics);

        // Mod's semi-transparent dark overlay across the entire unscaled screen
        context.fill(0, 0, this.width, this.height, 0x90000000);

        int scaledMouseX = (int) (mouseX / uiScale);
        int scaledMouseY = (int) (mouseY / uiScale);

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(uiScale, uiScale);

        super.extractRenderState(graphics, scaledMouseX, scaledMouseY, delta);

        int centerX = getVirtualWidth() / 2;

        // Title
        context.drawCenteredTextWithShadow(this.font, this.title, centerX, 4, 0xFFFFFF);

        ItemStack hoveredStack = ItemStack.EMPTY;
        int hoveredCraftingSlot = getCraftingSlotAt(scaledMouseX, scaledMouseY);

        // --- LEFT PANE RENDERING ---
        if (!isLocalWorldCreator()) {
            // Main prompt replaced with red text on non-local / non-creator servers
            int pCenterX = leftPaneX + (leftPaneWidth / 2);
            int pStartY = contentY + 36;
            context.drawCenteredTextWithShadow(this.font, Component.translatable("recipeeditor.gui.remote_server_hint_1").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), pCenterX, pStartY, 0xFFFF5555);
            context.drawCenteredTextWithShadow(this.font, Component.translatable("recipeeditor.gui.remote_server_hint_2").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), pCenterX, pStartY + 12, 0xFFFF5555);
            context.drawCenteredTextWithShadow(this.font, Component.translatable("recipeeditor.gui.remote_server_hint_3").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), pCenterX, pStartY + 24, 0xFFFF5555);
        } else if (!configCopy.modEnabled) {
            // Message when mod is disabled (red title + gray/white prompt)
            int pCenterX = leftPaneX + (leftPaneWidth / 2);
            int pStartY = contentY + 36;
            context.drawCenteredTextWithShadow(this.font, Component.translatable("recipeeditor.gui.mod_disabled_hint_1").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), pCenterX, pStartY, 0xFFFF5555);
            context.drawCenteredTextWithShadow(this.font, Component.translatable("recipeeditor.gui.mod_disabled_hint_2").withStyle(ChatFormatting.GRAY), pCenterX, pStartY + 14, 0xFFAAAAAA);
            context.drawCenteredTextWithShadow(this.font, Component.translatable("recipeeditor.gui.mod_disabled_hint_3").withStyle(ChatFormatting.GRAY), pCenterX, pStartY + 26, 0xFFAAAAAA);
        } else if (targetItem == null) {
            // Placeholder when no item is selected yet (green, 3 lines, centered)
            int pCenterX = leftPaneX + (leftPaneWidth / 2);
            int pStartY = contentY + 36;
            context.drawCenteredTextWithShadow(this.font, Component.translatable("recipeeditor.gui.select_item_hint_1").withStyle(ChatFormatting.GREEN), pCenterX, pStartY, 0xFF55FF55);
            context.drawCenteredTextWithShadow(this.font, Component.translatable("recipeeditor.gui.select_item_hint_2").withStyle(ChatFormatting.GREEN), pCenterX, pStartY + 12, 0xFF55FF55);
            context.drawCenteredTextWithShadow(this.font, Component.translatable("recipeeditor.gui.select_item_hint_3").withStyle(ChatFormatting.GREEN), pCenterX, pStartY + 24, 0xFF55FF55);
        } else if (currentRecipe == null) {
            // Screen prompt for machine without recipes
            int pCenterX = leftPaneX + (leftPaneWidth / 2);
            int promptY = gridStartY + 6;
            String machineName = selectedType.getDisplayName().getString();
            Component line1 = (currentFilter == CatalogFilter.CUSTOM)
                    ? Component.translatable("recipeeditor.gui.uncraftable_machine_custom_1").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("recipeeditor.gui.uncraftable_machine_1").withStyle(ChatFormatting.GRAY);
            Component line2 = Component.translatable("recipeeditor.gui.uncraftable_machine_2", machineName).withStyle(ChatFormatting.WHITE);
            context.drawCenteredTextWithShadow(this.font, line1, pCenterX, promptY, 0xFFAAAAAA);
            context.drawCenteredTextWithShadow(this.font, line2, pCenterX, promptY + 14, 0xFFFFFFFF);
        } else {
            // Draw total variants count beside variant selector buttons and description directly below
            int variantTotal = Math.max(1, typeVariants.size());
            int gridStartX = leftPaneX + 4;

            context.drawTextWithShadow(this.font, Component.literal("/ " + variantTotal).withStyle(ChatFormatting.GRAY), leftPaneX + 74, variantBarY + 4, 0xFFAAAAAA);
            Component variantText = Component.translatable("recipeeditor.gui.craft_variant", currentVariantIndex + 1, variantTotal).withStyle(ChatFormatting.GREEN);
            context.drawTextWithShadow(this.font, variantText, leftPaneX + 4, variantBarY + 18, 0xFF55FF55);

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
                        ItemStack stack = safeItemStack(item);
                        drawItemInScreen(context, stack, inputX + 3, inputY + 3);
                        if (configCopy.modEnabled) {
                            context.drawStackOverlay(this.font, stack, inputX + 3, inputY + 3);
                        }
                    }
                    if (scaledMouseX >= inputX && scaledMouseX <= inputX + 22 && scaledMouseY >= inputY && scaledMouseY <= inputY + 22 && item != Items.AIR) {
                        hoveredStack = safeItemStack(item);
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
                            ItemStack stack = safeItemStack(item);
                            drawItemInScreen(context, stack, slotX + 3, slotY + 3);
                            if (configCopy.modEnabled) {
                                context.drawStackOverlay(this.font, stack, slotX + 3, slotY + 3);
                            }
                        }
                        if (scaledMouseX >= slotX && scaledMouseX <= slotX + 22 && scaledMouseY >= slotY && scaledMouseY <= slotY + 22 && item != Items.AIR) {
                            hoveredStack = safeItemStack(item);
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
                                ItemStack stack = safeItemStack(item);
                                drawItemInScreen(context, stack, x + 3, y + 3);
                                if (configCopy.modEnabled) {
                                    context.drawStackOverlay(this.font, stack, x + 3, y + 3);
                                }
                            }
                            if (scaledMouseX >= x && scaledMouseX <= x + 22 && scaledMouseY >= y && scaledMouseY <= y + 22 && item != Items.AIR) {
                                hoveredStack = safeItemStack(item);
                            }
                        }
                    }
                }
            }

            // Draw Arrow
            int arrowX = gridStartX + 3 * SLOT_SIZE + 10;
            int arrowY = gridStartY + SLOT_SIZE + 2;
            context.drawTextWithShadow(this.font, Component.literal("➡").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), arrowX, arrowY, 0xFFFFAA00);

            // Draw Result Slot
            int resultX = arrowX + 24;
            int resultY = gridStartY + SLOT_SIZE - 3;
            boolean isResultSelected = (selectedSlot == RESULT_SLOT);
            boolean isResultDropTarget = (draggedItem != null && hoveredCraftingSlot == RESULT_SLOT);
            drawSlotBox(context, resultX, resultY, 28, 28, isResultSelected, isResultDropTarget);

            if (targetItem != null && targetItem != Items.AIR) {
                int count = currentRecipe != null ? currentRecipe.getResultCountForType(selectedType) : 1;
                ItemStack resStack = safeItemStack(targetItem, count);
                drawItemInScreen(context, resStack, resultX + 6, resultY + 6);
                if (configCopy.modEnabled) {
                    context.drawStackOverlay(this.font, resStack, resultX + 6, resultY + 6);
                }
                if (scaledMouseX >= resultX && scaledMouseX <= resultX + 28 && scaledMouseY >= resultY && scaledMouseY <= resultY + 28) {
                    hoveredStack = resStack;
                }
            }

            // Draw labels for furnace fields if applicable
            if (selectedType == RecipeTypeEnum.SMELTING || selectedType == RecipeTypeEnum.BLASTING ||
                selectedType == RecipeTypeEnum.SMOKING || selectedType == RecipeTypeEnum.CAMPFIRE_COOKING) {
                if (cookingTimeField != null && cookingTimeField.isVisible()) {
                    Component timeLabel = Component.translatable("recipeeditor.gui.label_time");
                    context.drawTextWithShadow(this.font, timeLabel.copy().withStyle(ChatFormatting.GRAY), cookingTimeField.getX(), cookingTimeField.getY() - 10, 0xFFAAAAAA);
                }
                if (experienceField != null && experienceField.isVisible()) {
                    Component expLabel = Component.translatable("recipeeditor.gui.label_exp");
                    context.drawTextWithShadow(this.font, expLabel.copy().withStyle(ChatFormatting.YELLOW), experienceField.getX(), experienceField.getY() - 10, 0xFFFFAA00);
                }
            }
        }

        // --- DRAW HINTS IN A CLEAN ADAPTIVE VERTICAL COLUMN UNDER LEFT MENU BUTTONS (IF ENABLED) ---
        if (showHints && configCopy.modEnabled) {
            int hintStartY;
            if (targetItem == null) {
                hintStartY = contentY + 92;
            } else if (currentRecipe == null) {
                hintStartY = gridStartY + 68;
            } else {
                boolean deleteBtnsVisible = (deleteRecipeBtn != null && deleteRecipeBtn.visible);
                hintStartY = deleteBtnsVisible ? (actionBtnY + 82) : (actionBtnY + 42);
            }
            int vBottomY = getVirtualHeight() - 22;
            int availableHeight = vBottomY - hintStartY;

            if (availableHeight >= 10) {
                List<Component> hintLines = List.of(
                        Component.translatable("recipeeditor.gui.hint_1").withStyle(ChatFormatting.GREEN),
                        Component.translatable("recipeeditor.gui.hint_1_sub").withStyle(ChatFormatting.GREEN),
                        Component.translatable("recipeeditor.gui.hint_2").withStyle(ChatFormatting.GREEN),
                        Component.translatable("recipeeditor.gui.hint_2_sub").withStyle(ChatFormatting.GREEN),
                        Component.translatable("recipeeditor.gui.hint_3").withStyle(ChatFormatting.GREEN),
                        Component.translatable("recipeeditor.gui.hint_4").withStyle(ChatFormatting.GREEN),
                        Component.translatable("recipeeditor.gui.hint_5").withStyle(ChatFormatting.GREEN)
                );

                int maxTextWidth = 10;
                for (Component line : hintLines) {
                    maxTextWidth = Math.max(maxTextWidth, this.font.width(line));
                }

                float baseHeight = hintLines.size() * 10.0f;
                float scaleH = (float) availableHeight / baseHeight;
                float scaleW = (float) leftPaneWidth / (float) maxTextWidth;
                float textScale = Math.min(1.0f, Math.min(scaleH, scaleW));

                context.getMatrices().pushMatrix();
                context.getMatrices().translate((float) leftPaneX, (float) hintStartY);
                context.getMatrices().scale(textScale, textScale);

                int lineSpacing = 10;
                for (int i = 0; i < hintLines.size(); i++) {
                    context.drawTextWithShadow(this.font, hintLines.get(i), 0, i * lineSpacing, 0xFF55FF55);
                }

                context.getMatrices().popMatrix();
            }
        }

        // --- DRAW DYNAMIC CATALOG GRID ---
        int startIndex = catalogPage * itemsPerPage;
        int endIndex = Math.min(filteredItems.size(), startIndex + itemsPerPage);

        for (int i = startIndex; i < endIndex; i++) {
            int localIdx = i - startIndex;
            int cRow = localIdx / catalogCols;
            int cCol = localIdx % catalogCols;

            int slotX = rightPaneX + cCol * SLOT_SIZE;
            int slotY = catalogGridY + cRow * SLOT_SIZE;

            boolean isHovered = scaledMouseX >= slotX && scaledMouseX <= slotX + 22 && scaledMouseY >= slotY && scaledMouseY <= slotY + 22;
            drawSlotBox(context, slotX, slotY, 22, 22, isHovered, false);

            Item catItem = filteredItems.get(i);
            ItemStack catStack = safeItemStack(catItem);
            drawItemInScreen(context, catStack, slotX + 3, slotY + 3);

            if (isHovered) {
                hoveredStack = catStack;
            }
        }

        // Catalog Page Info
        int maxPage = Math.max(1, (filteredItems.size() + itemsPerPage - 1) / Math.max(1, itemsPerPage));
        MutableComponent pageInfo = Component.translatable("recipeeditor.gui.items_total", catalogPage + 1, maxPage, filteredItems.size());
        context.drawCenteredTextWithShadow(this.font, pageInfo.withStyle(ChatFormatting.GRAY), rightPaneX + (searchWidth / 2), catalogGridY + (catalogRows * SLOT_SIZE) + 8, 0xFFAAAAAA);

        if (RecipeInspector.isScanningJars()) {
            Component scanIndicator = Component.translatable("recipeeditor.gui.scanning_recipes").withStyle(ChatFormatting.YELLOW);
            context.drawCenteredTextWithShadow(this.font, scanIndicator, rightPaneX + (searchWidth / 2), catalogGridY + (catalogRows * SLOT_SIZE) + 20, 0xFFFFAA00);
        }

        // Notification overlay message (drawn to the left of bottom buttons or at top)
        int bottomY = getVirtualHeight() - 22;
        if (notificationText != null && System.currentTimeMillis() < notificationTimer) {
            int msgW = this.font.width(notificationText);
            Component showHintsText = Component.translatable("recipeeditor.gui.show_hints");
            Component hideHintsText = Component.translatable("recipeeditor.gui.hide_hints");
            int wExit = Math.max(55, this.font.width(Component.translatable("recipeeditor.gui.exit")) + 16);
            int wReset = Math.max(100, this.font.width(Component.translatable("recipeeditor.gui.reset_defaults")) + 16);
            int wHints = Math.max(100, Math.max(this.font.width(showHintsText), this.font.width(hideHintsText)) + 16);
            int hintsX = (getVirtualWidth() - 8) - wExit - 4 - wReset - 4 - wHints;

            int msgX = hintsX - msgW - 8;
            if (msgX >= 8) {
                context.drawTextWithShadow(this.font, notificationText, msgX, bottomY + 5, 0xFF55FF55);
            } else {
                context.drawCenteredTextWithShadow(this.font, notificationText, getVirtualWidth() / 2, 16, 0xFF55FF55);
            }
        }

        // Render dragged item
        if (draggedItem != null && draggedItem != Items.AIR) {
            ItemStack dragStack = (dragSourceSlot == RESULT_SLOT && currentRecipe != null)
                    ? safeItemStack(draggedItem, currentRecipe.getResultCountForType(selectedType))
                    : safeItemStack(draggedItem);
            drawItemInScreen(context, dragStack, scaledMouseX - 8, scaledMouseY - 8);
            if (configCopy.modEnabled) {
                context.drawStackOverlay(this.font, dragStack, scaledMouseX - 8, scaledMouseY - 8);
            }
        }

        if (!configCopy.modEnabled) {
            // Semi-transparent dark-grey veil over everything
            context.fill(0, 0, getVirtualWidth(), getVirtualHeight(), 0x99181818);

            // Re-render toggle button and exit button so they stay sharp and bright on top of the veil
            if (toggleEnabledBtn != null && toggleEnabledBtn.visible) {
                toggleEnabledBtn.extractRenderState(graphics, scaledMouseX, scaledMouseY, delta);
            }
            if (exitBtn != null && exitBtn.visible) {
                exitBtn.extractRenderState(graphics, scaledMouseX, scaledMouseY, delta);
            }

            // Render message on top of veil
            int pCenterX = leftPaneX + (leftPaneWidth / 2);
            int pStartY = contentY + 36;
            context.drawCenteredTextWithShadow(this.font, Component.translatable("recipeeditor.gui.mod_disabled_hint_1").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), pCenterX, pStartY, 0xFFFF5555);
            context.drawCenteredTextWithShadow(this.font, Component.translatable("recipeeditor.gui.mod_disabled_hint_2").withStyle(ChatFormatting.GRAY), pCenterX, pStartY + 14, 0xFFAAAAAA);
            context.drawCenteredTextWithShadow(this.font, Component.translatable("recipeeditor.gui.mod_disabled_hint_3").withStyle(ChatFormatting.GRAY), pCenterX, pStartY + 26, 0xFFAAAAAA);
        }

        context.getMatrices().popMatrix();

        // Render Hover Tooltip (outside matrices, at native screen resolution)
        if (!configCopy.modEnabled) {
            if (toggleEnabledBtn != null && toggleEnabledBtn.visible && toggleEnabledBtn.isHovered()) {
                try {
                    context.drawTooltip(this.font, Component.translatable("recipeeditor.tooltip.toggle_mod"), mouseX, mouseY);
                } catch (Throwable ignored) {}
            } else if (exitBtn != null && exitBtn.visible && exitBtn.isHovered()) {
                try {
                    context.drawTooltip(this.font, Component.translatable("recipeeditor.tooltip.exit"), mouseX, mouseY);
                } catch (Throwable ignored) {}
            }
            return;
        }

        if (draggedItem == null) {
            if (!hoveredStack.isEmpty()) {
                try {
                    context.drawItemTooltip(this.font, hoveredStack, mouseX, mouseY);
                } catch (Throwable t) {
                    try {
                        context.drawTooltip(this.font, hoveredStack.getHoverName(), mouseX, mouseY);
                    } catch (Throwable ignored) {}
                }
            } else if (shapelessToggleBtn != null && shapelessToggleBtn.visible && shapelessToggleBtn.isHovered()) {
                boolean shapeless = currentRecipe != null && currentRecipe.isShapeless;
                List<Component> lines = shapeless
                        ? List.of(
                                Component.translatable("recipeeditor.tooltip.shapeless_line1"),
                                Component.translatable("recipeeditor.tooltip.shapeless_line2"),
                                Component.translatable("recipeeditor.tooltip.shapeless_line3")
                        )
                        : List.of(
                                Component.translatable("recipeeditor.tooltip.shaped_line1"),
                                Component.translatable("recipeeditor.tooltip.shaped_line2"),
                                Component.translatable("recipeeditor.tooltip.shaped_line3")
                        );
                try {
                    context.drawTooltip(this.font, lines, mouseX, mouseY);
                } catch (Throwable ignored) {}
            } else {
                Component buttonTooltip = getHoveredButtonTooltip(scaledMouseX, scaledMouseY);
                if (buttonTooltip != null) {
                    try {
                        context.drawTooltip(this.font, buttonTooltip, mouseX, mouseY);
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    private Component getHoveredButtonTooltip(int scaledMouseX, int scaledMouseY) {
        if (toggleEnabledBtn != null && toggleEnabledBtn.visible && toggleEnabledBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.toggle_mod");
        }
        for (int i = 0; i < typeButtons.size(); i++) {
            Button btn = typeButtons.get(i);
            if (btn.visible && btn.isHovered()) {
                if (i < typeButtonEnums.size()) {
                    return typeButtonEnums.get(i).getTooltip();
                }
            }
        }
        if (prevWorkstationBtn != null && prevWorkstationBtn.visible && prevWorkstationBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.prev_workstation");
        }
        if (nextWorkstationBtn != null && nextWorkstationBtn.visible && nextWorkstationBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.next_workstation");
        }
        if (prevVariantBtn != null && prevVariantBtn.visible && prevVariantBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.prev_variant");
        }
        if (variantField != null && variantField.isVisible() && variantField.isMouseOver(scaledMouseX, scaledMouseY)) {
            return Component.translatable("recipeeditor.tooltip.variant_field");
        }
        if (nextVariantBtn != null && nextVariantBtn.visible && nextVariantBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.next_variant");
        }
        if (minusCountBtn != null && minusCountBtn.visible && minusCountBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.minus_count");
        }
        if (resultCountField != null && resultCountField.isVisible() && resultCountField.isMouseOver(scaledMouseX, scaledMouseY)) {
            return Component.translatable("recipeeditor.tooltip.count_field");
        }
        if (plusCountBtn != null && plusCountBtn.visible && plusCountBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.plus_count");
        }
        if (cookingTimeField != null && cookingTimeField.isVisible()) {
            if (cookingTimeField.isMouseOver(scaledMouseX, scaledMouseY) ||
                    (scaledMouseX >= cookingTimeField.getX() && scaledMouseX <= cookingTimeField.getX() + cookingTimeField.getWidth() &&
                     scaledMouseY >= cookingTimeField.getY() - 12 && scaledMouseY < cookingTimeField.getY())) {
                int ticks = 200;
                try {
                    String text = cookingTimeField.getText().trim();
                    if (!text.isEmpty()) {
                        ticks = Integer.parseInt(text);
                    } else if (currentRecipe != null) {
                        ticks = currentRecipe.cookingTime;
                    }
                } catch (NumberFormatException ignored) {
                    if (currentRecipe != null) {
                        ticks = currentRecipe.cookingTime;
                    }
                }
                float seconds = ticks / 20.0f;
                String secStr = (seconds == (long) seconds) ? String.format(Locale.ROOT, "%d", (long) seconds) : String.format(Locale.ROOT, "%.1f", seconds);
                return Component.translatable("recipeeditor.tooltip.cooking_time_field", ticks, secStr);
            }
        }
        if (experienceField != null && experienceField.isVisible()) {
            if (experienceField.isMouseOver(scaledMouseX, scaledMouseY) ||
                    (scaledMouseX >= experienceField.getX() && scaledMouseX <= experienceField.getX() + experienceField.getWidth() &&
                     scaledMouseY >= experienceField.getY() - 12 && scaledMouseY < experienceField.getY())) {
                float xp = currentRecipe != null ? currentRecipe.experience : 0.1f;
                return Component.translatable("recipeeditor.tooltip.experience_field", String.format(Locale.ROOT, "%.1f", xp));
            }
        }
        if (saveCraftBtn != null && saveCraftBtn.visible && saveCraftBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.save_craft");
        }
        if (clearCraftBtn != null && clearCraftBtn.visible && clearCraftBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.clear_all");
        }
        if (deleteVariantBtn != null && deleteVariantBtn.visible && deleteVariantBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.delete_variant");
        }
        if (deleteRecipeBtn != null && deleteRecipeBtn.visible && deleteRecipeBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.delete_recipe");
        }
        if (createRecipeBtn != null && createRecipeBtn.visible && createRecipeBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.create_recipe");
        }
        for (int i = 0; i < filterButtons.size(); i++) {
            Button btn = filterButtons.get(i);
            if (btn.visible && btn.isHovered()) {
                CatalogFilter[] filters = CatalogFilter.values();
                if (i < filters.length) {
                    CatalogFilter f = filters[i];
                    return Component.translatable("recipeeditor.tooltip.filter_" + f.name().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (prevTabBtn != null && prevTabBtn.visible && prevTabBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.prev_tab");
        }
        if (nextTabBtn != null && nextTabBtn.visible && nextTabBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.next_tab");
        }
        for (int i = 0; i < tabButtons.size(); i++) {
            Button btn = tabButtons.get(i);
            if (btn.visible && btn.isHovered()) {
                int tabIdx = tabScrollOffset + i;
                if (tabIdx >= 0 && tabIdx < modTabs.size()) {
                    return Component.translatable("recipeeditor.tooltip.tab_mod", modTabs.get(tabIdx).displayName);
                }
            }
        }
        if (clearSearchBtn != null && clearSearchBtn.visible && clearSearchBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.clear_search");
        }
        if (searchField != null && searchField.isVisible() && searchField.isMouseOver(scaledMouseX, scaledMouseY)) {
            return Component.translatable("recipeeditor.tooltip.search");
        }
        if (prevPageBtn != null && prevPageBtn.visible && prevPageBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.prev_page");
        }
        if (nextPageBtn != null && nextPageBtn.visible && nextPageBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.next_page");
        }
        if (toggleHintsBtn != null && toggleHintsBtn.visible && toggleHintsBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.toggle_hints");
        }
        if (resetDefaultsBtn != null && resetDefaultsBtn.visible && resetDefaultsBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.reset_defaults");
        }
        if (exitBtn != null && exitBtn.visible && exitBtn.isHovered()) {
            return Component.translatable("recipeeditor.tooltip.exit");
        }
        return null;
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
        drawBorder(context, x, y, w, h, borderColor);
    }

    private static void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private boolean isShiftDown() {
        if (this.minecraft == null || this.minecraft.getWindow() == null) return false;
        long handle = this.minecraft.getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private static boolean isLeftClick(int button) {
        return button == InputConstants.MOUSE_BUTTON_LEFT || button == 0;
    }

    private static boolean isRightClick(int button) {
        return button == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    private ItemStack safeItemStack(Item item) {
        return safeItemStack(item, 1);
    }

    private ItemStack safeItemStack(Item item, int count) {
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        try {
            if (item.builtInRegistryHolder().areComponentsBound()) {
                return new ItemStack(item, count);
            }
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    private void drawItemInScreen(DrawContext context, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        context.drawItem(stack, x, y);
        if (!configCopy.modEnabled || !isLocalWorldCreator()) {
            context.fill(x, y, x + 16, y + 16, 0x883a3a3a);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double sX = event.x() / uiScale;
        double sY = event.y() / uiScale;
        int button = event.button();
        MouseButtonEvent scaledEvent = new MouseButtonEvent(sX, sY, event.buttonInfo());

        if (!isLocalWorldCreator()) {
            if (exitBtn != null && exitBtn.visible && exitBtn.isMouseOver(sX, sY)) {
                return exitBtn.mouseClicked(scaledEvent, doubleClick);
            }
            return false;
        }

        if (!configCopy.modEnabled) {
            if (toggleEnabledBtn != null && toggleEnabledBtn.visible && toggleEnabledBtn.isMouseOver(sX, sY)) {
                return toggleEnabledBtn.mouseClicked(scaledEvent, doubleClick);
            }
            if (exitBtn != null && exitBtn.visible && exitBtn.isMouseOver(sX, sY)) {
                return exitBtn.mouseClicked(scaledEvent, doubleClick);
            }
            return false;
        }

        if (clearSearchBtn != null && clearSearchBtn.visible && clearSearchBtn.isMouseOver(sX, sY)) {
            return clearSearchBtn.mouseClicked(scaledEvent, doubleClick);
        }

        if (super.mouseClicked(scaledEvent, doubleClick)) {
            return true;
        }

        if (isLeftClick(button)) { // Left click: select slot or start drag
            int craftingSlot = getCraftingSlotAt(sX, sY);
            if (craftingSlot != -1) {
                selectedSlot = craftingSlot;
                Item itemInSlot = getItemInSlot(craftingSlot);
                if (itemInSlot != null && itemInSlot != Items.AIR) {
                    draggedItem = itemInSlot;
                    dragSourceSlot = craftingSlot;
                    dragStartX = sX;
                    dragStartY = sY;
                }
                return true;
            }

            Item catItem = getCatalogItemAt(sX, sY);
            if (catItem != null && catItem != Items.AIR) {
                if (event.hasShiftDown() || isShiftDown()) {
                    selectTargetItem(catItem);
                } else {
                    draggedItem = catItem;
                    dragSourceSlot = -1;
                    dragStartX = sX;
                    dragStartY = sY;
                }
                return true;
            }
        } else if (isRightClick(button)) { // Right click: clear slot or LOAD RECIPE for catalog item
            int craftingSlot = getCraftingSlotAt(sX, sY);
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

            Item catItem = getCatalogItemAt(sX, sY);
            if (catItem != null && catItem != Items.AIR) {
                loadRecipeForTarget(catItem);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!configCopy.modEnabled || !isLocalWorldCreator()) {
            return false;
        }
        double sX = event.x() / uiScale;
        double sY = event.y() / uiScale;
        int button = event.button();
        MouseButtonEvent scaledEvent = new MouseButtonEvent(sX, sY, event.buttonInfo());

        GuiEventListener focused = this.getFocused();
        if (focused instanceof SelectableTextFieldWidget textField && isLeftClick(button)) {
            textField.handleMouseReleased(sX, sY, button);
        }

        if (isLeftClick(button) && draggedItem != null) {
            int targetSlot = getCraftingSlotAt(sX, sY);
            if (targetItem == null) {
                selectTargetItem(draggedItem);
                if (targetSlot >= 0 && targetSlot < 9 && currentRecipe != null) {
                    currentRecipe.setItemAt(targetSlot, draggedItem);
                    selectedSlot = targetSlot;
                }
            } else if (targetSlot != -1) {
                if (currentRecipe != null) {
                    if (targetSlot >= 0 && targetSlot < 9) {
                        currentRecipe.setItemAt(targetSlot, draggedItem);
                        selectedSlot = targetSlot;
                    } else if (targetSlot == RESULT_SLOT) {
                        if (currentRecipe != null) {
                            String[] snapSlots = currentRecipe.patternSlots != null ? Arrays.copyOf(currentRecipe.patternSlots, 9) : new String[9];
                            float snapExp = currentRecipe.experience;
                            int snapTime = currentRecipe.cookingTime;
                            boolean snapShapeless = currentRecipe.isShapeless;
                            int snapCount = currentRecipe.getResultCountForType(selectedType);

                            this.targetItem = draggedItem;
                            this.activeCreatedTypes.add(selectedType);
                            sessionVariantsByType.clear();
                            sessionVariantIndexByType.clear();
                            refreshTypeVariants(selectedType, true);

                            int matchIdx = -1;
                            for (int i = 0; i < typeVariants.size(); i++) {
                                CustomRecipeData v = typeVariants.get(i);
                                if (Arrays.equals(v.patternSlots, snapSlots)) {
                                    matchIdx = i;
                                    break;
                                }
                            }

                            if (matchIdx >= 0) {
                                currentVariantIndex = matchIdx;
                                currentRecipe = typeVariants.get(matchIdx);
                            } else {
                                Identifier id = BuiltInRegistries.ITEM.getKey(draggedItem);
                                int countToUse = Math.min(snapCount > 0 ? snapCount : 1, draggedItem.getDefaultMaxStackSize());
                                CustomRecipeData newVariant = new CustomRecipeData(
                                        id != null ? id.getPath() : "craft",
                                        id != null ? id.toString() : "minecraft:air",
                                        countToUse,
                                        selectedType
                                );
                                newVariant.setResultCountForType(selectedType, countToUse);
                                newVariant.patternSlots = snapSlots;
                                newVariant.experience = snapExp;
                                newVariant.cookingTime = snapTime;
                                newVariant.isShapeless = snapShapeless;
                                newVariant.invalidateCache();
                                typeVariants.add(newVariant);
                                currentVariantIndex = typeVariants.size() - 1;
                                currentRecipe = newVariant;
                            }

                            if (currentRecipe != null) {
                                int max = draggedItem.getDefaultMaxStackSize();
                                if (currentRecipe.getResultCountForType(selectedType) > max) {
                                    currentRecipe.setResultCountForType(selectedType, max);
                                }
                                if (resultCountField != null) {
                                    resultCountField.setText(String.valueOf(currentRecipe.getResultCountForType(selectedType)));
                                }
                                if (cookingTimeField != null) {
                                    cookingTimeField.setText(String.valueOf(currentRecipe.cookingTime));
                                }
                                if (experienceField != null) {
                                    experienceField.setText(String.format(Locale.ROOT, "%.1f", currentRecipe.experience));
                                }
                            }
                            selectedSlot = RESULT_SLOT;
                            updateEditorWidgetsVisibility();
                            updateVariantButtons();
                            rebuildTypeButtons();
                            updateButtonStates();
                        } else {
                            selectTargetItem(draggedItem);
                            selectedSlot = RESULT_SLOT;
                        }
                    }
                }
            } else {
                double distSq = (sX - dragStartX) * (sX - dragStartX) + (sY - dragStartY) * (sY - dragStartY);
                if (dragSourceSlot == -1 && distSq < 36) {
                    if (currentFilter == CatalogFilter.CUSTOM && (currentRecipe == null || selectedSlot == RESULT_SLOT || getCatalogItemAt(sX, sY) != null)) {
                        loadRecipeForTarget(draggedItem);
                    } else if (currentRecipe != null && selectedSlot >= 0 && selectedSlot < 9) {
                        currentRecipe.setItemAt(selectedSlot, draggedItem);
                    }
                }
            }
            draggedItem = null;
            dragSourceSlot = -1;
            updateButtonStates();
            return true;
        }
        return super.mouseReleased(scaledEvent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!configCopy.modEnabled || !isLocalWorldCreator()) {
            return false;
        }
        if (draggedItem != null) {
            return true;
        }
        double sX = event.x() / uiScale;
        double sY = event.y() / uiScale;
        int button = event.button();
        MouseButtonEvent scaledEvent = new MouseButtonEvent(sX, sY, event.buttonInfo());

        GuiEventListener focused = this.getFocused();
        if (focused instanceof SelectableTextFieldWidget textField && isLeftClick(button)) {
            if (textField.handleMouseDragged(sX, sY, button)) {
                return true;
            }
        }

        return super.mouseDragged(scaledEvent, deltaX / uiScale, deltaY / uiScale);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean textFieldFocused = (searchField != null && searchField.isFocused()) ||
                (resultCountField != null && resultCountField.isFocused()) ||
                (variantField != null && variantField.isFocused()) ||
                (cookingTimeField != null && cookingTimeField.isFocused()) ||
                (experienceField != null && experienceField.isFocused());

        if (textFieldFocused) {
            return super.keyPressed(event);
        }

        if (!configCopy.modEnabled || !isLocalWorldCreator()) {
            return super.keyPressed(event);
        }

        int keyCode = event.key();

        if (keyCode == GLFW.GLFW_KEY_I) {
            toggleHintsVisibility();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            clearSelectedSlot();
            return true;
        }

        return super.keyPressed(event);
    }
}
