package com.uncraftbar.easyautocycler.gui;

import com.uncraftbar.easyautocycler.AutomationManager;
import com.uncraftbar.easyautocycler.EasyAutoCyclerMod;
import com.uncraftbar.easyautocycler.filter.FilterEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigScreen extends Screen {

    @Nullable
    private final Screen previousScreen;
    private final boolean librarianMode;
    private final Identifier professionId;
    private final boolean enchantedItemTargetsSupported;

    private FilterListWidget filterListWidget;
    private List<String> enchantmentSuggestions = List.of();
    private EnchantmentNameResolver enchantmentNameResolver;
    private ItemNameResolver itemNameResolver;
    private List<String> itemSuggestions = List.of();

    private List<FilterEntry> filters = new ArrayList<>();
    private final List<FilterEntry> originalFilters = new ArrayList<>();
    private boolean originalMatchAny;
    private CycleButton<Boolean> matchModeCycleButton;
    private Button bulkModeButton;
    private FilterEntry.Mode nextBulkMode = FilterEntry.Mode.ON;
    private boolean matchAny = true;

    private static final int PADDING = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int FILTER_ROW_HEIGHT = 38;

    public static Component titleFor(Screen merchantScreen) {
        if (AutomationManager.INSTANCE.isLibrarianTradeScreen(merchantScreen)) {
            return Component.translatable("gui.easyautocycler.config.title");
        }
        return Component.translatable("gui.easyautocycler.config.title.profession",
                merchantScreen.getTitle());
    }

    public ConfigScreen(@Nullable Screen previousScreen, Component title) {
        super(title);
        this.previousScreen = previousScreen;
        this.librarianMode = AutomationManager.INSTANCE.isLibrarianTradeScreen(previousScreen);
        Identifier detectedProfession = AutomationManager.INSTANCE.getVillagerProfessionId(previousScreen);
        this.professionId = detectedProfession == null
                ? Identifier.withDefaultNamespace("librarian") : detectedProfession;
        this.enchantedItemTargetsSupported = professionId != null && Set.of(
                "armorer", "weaponsmith", "toolsmith", "fisherman", "fletcher")
                .contains(professionId.getPath());

        this.filters.addAll(AutomationManager.INSTANCE.getFilterEntries(professionId));
        this.originalFilters.clear();
        this.originalFilters.addAll(this.filters.stream().map(FilterEntry::new).collect(Collectors.toList()));
        this.originalMatchAny = AutomationManager.INSTANCE.isMatchAny(professionId);
        this.matchAny = AutomationManager.INSTANCE.isMatchAny(professionId);
    }

    List<String> enchantmentSuggestions() {
        return enchantmentSuggestions;
    }

    List<String> itemSuggestions() {
        return itemSuggestions;
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) {
            this.onClose();
            return;
        }

        try {
            if (librarianMode || enchantedItemTargetsSupported) {
                this.enchantmentNameResolver = new EnchantmentNameResolver(
                        this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT),
                        this.minecraft.getResourceManager());
                this.enchantmentSuggestions = this.enchantmentNameResolver.suggestions();
            }
            if (!librarianMode && professionId != null) {
                this.itemNameResolver = new ItemNameResolver(this.minecraft.level.registryAccess(),
                        professionId, this.minecraft.getResourceManager());
                this.itemSuggestions = this.itemNameResolver.suggestions();
            }
        } catch (Exception e) {
            EasyAutoCyclerMod.LOGGER.error("Failed to load registry for suggestions", e);
            this.enchantmentSuggestions = List.of();
            this.itemSuggestions = List.of();
        }

        int contentWidth = Math.min(540, this.width - 20);
        int guiLeft = (this.width - contentWidth) / 2;
        int currentY = 48;

        int addButtonWidth = Math.min(116, (contentWidth - PADDING * 2) / 3);
        int matchButtonWidth = 64;

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.easyautocycler.filters.add_compact"),
                button -> openFilterEditor(null))
                .pos(guiLeft, currentY)
                .size(addButtonWidth, BUTTON_HEIGHT)
                .build());

        this.matchModeCycleButton = CycleButton.<Boolean>builder(value ->
                        Component.translatable(value
                                ? "gui.easyautocycler.filters.match_or_short"
                                : "gui.easyautocycler.filters.match_and_short"), matchAny)
                .withValues(true, false)
                .displayOnlyValue()
                .create(guiLeft + addButtonWidth + PADDING, currentY,
                        matchButtonWidth, BUTTON_HEIGHT,
                        Component.empty(),
                        (cycleButton, newValue) -> {
                            matchAny = newValue;
                            updateMatchModeTooltip();
                        });
        this.addRenderableWidget(this.matchModeCycleButton);
        updateMatchModeTooltip();

        int bulkButtonX = guiLeft + addButtonWidth + PADDING + matchButtonWidth + PADDING;
        this.bulkModeButton = Button.builder(bulkModeLabel(nextBulkMode), this::applyNextBulkMode)
                .pos(bulkButtonX, currentY)
                .size(guiLeft + contentWidth - bulkButtonX, BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(this.bulkModeButton);

        currentY += BUTTON_HEIGHT + 9;
        int saveButtonY = this.height - 30;
        int filtersListHeight = saveButtonY - currentY - 5;
        this.filterListWidget = new FilterListWidget(this.minecraft, guiLeft, currentY, contentWidth, filtersListHeight);
        refreshFiltersList();
        this.addRenderableWidget(this.filterListWidget);

        int bottomButtonWidth = (contentWidth - PADDING * 2) / 3;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.easyautocycler.config.save"), this::onSave)
                .pos(guiLeft, saveButtonY).size(bottomButtonWidth, BUTTON_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.easyautocycler.config.clear_all"), this::onClear)
                .pos(guiLeft + bottomButtonWidth + PADDING, saveButtonY).size(bottomButtonWidth, BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                .pos(guiLeft + (bottomButtonWidth + PADDING) * 2, saveButtonY)
                .size(contentWidth - (bottomButtonWidth + PADDING) * 2, BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int contentWidth = Math.min(540, this.width - 20);
        int guiLeft = (this.width - contentWidth) / 2;
        boolean dirty = hasUnsavedChanges();
        Component renderedTitle = dirty ? this.title.copy().append(Component.literal(" *").withStyle(ChatFormatting.GOLD)) : this.title;
        graphics.text(this.font, renderedTitle, guiLeft, 10, 0xFFF4F6F8, false);
        Component summary = dirty ? Component.translatable("gui.easyautocycler.config.unsaved")
                : Component.translatable("gui.easyautocycler.config.summary", filters.size());
        graphics.text(this.font, summary, guiLeft, 23, dirty ? 0xFFFFC857 : 0xFFAAB2BF, false);

        if (filters.isEmpty()) {
            Component noFiltersMsg = Component.translatable("gui.easyautocycler.filters.no_filters")
                    .withStyle(ChatFormatting.GRAY);
            int msgX = this.width / 2 - this.font.width(noFiltersMsg) / 2;
            int msgY = this.height / 2;
            graphics.text(this.font, noFiltersMsg, msgX, msgY, 0xFFAAAAAA, true);
        }

    }

    private void onSave(Button button) {
        AutomationManager.INSTANCE.setMatchAny(professionId, matchModeCycleButton.getValue());
        AutomationManager.INSTANCE.setFilterEntries(professionId, filters);

        this.sendMessageToPlayer(Component.translatable("chat.easyautocycler.config_saved")
                .withStyle(ChatFormatting.GREEN));
        this.onClose();
    }

    private void onClear(Button button) {
        filters.clear();
        matchAny = true;
        refreshFiltersList();

        if (this.matchModeCycleButton != null) this.matchModeCycleButton.setValue(true);

        this.sendMessageToPlayer(Component.translatable("chat.easyautocycler.config_cleared_unsaved")
                .withStyle(ChatFormatting.YELLOW));
    }

    private void setAllModes(FilterEntry.Mode mode) {
        filters.forEach(filter -> filter.setMode(mode));
        refreshFiltersList();
    }

    private void applyNextBulkMode(Button button) {
        setAllModes(nextBulkMode);
        nextBulkMode = switch (nextBulkMode) {
            case ON -> FilterEntry.Mode.OFF;
            case OFF -> FilterEntry.Mode.AUTO;
            case AUTO -> FilterEntry.Mode.ON;
        };
        button.setMessage(bulkModeLabel(nextBulkMode));
    }

    private static Component bulkModeLabel(FilterEntry.Mode mode) {
        return Component.translatable(switch (mode) {
            case ON -> "gui.easyautocycler.filters.all_on";
            case OFF -> "gui.easyautocycler.filters.all_off";
            case AUTO -> "gui.easyautocycler.filters.all_auto";
        });
    }

    private void sendMessageToPlayer(Component message) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(message);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.previousScreen);
        }
    }

    private void updateMatchModeTooltip() {
        if (this.matchModeCycleButton != null) {
            this.matchModeCycleButton.setTooltip(Tooltip.create(Component.translatable(matchAny
                    ? "gui.easyautocycler.filters.match_any.tooltip"
                    : "gui.easyautocycler.filters.match_all.tooltip")));
        }
    }

    private boolean hasUnsavedChanges() {
        if (matchAny != originalMatchAny || filters.size() != originalFilters.size()) return true;
        for (int index = 0; index < filters.size(); index++) {
            if (!sameFilter(filters.get(index), originalFilters.get(index))) return true;
        }
        return false;
    }

    private static boolean sameFilter(FilterEntry left, FilterEntry right) {
        return left.getMode() == right.getMode()
                && left.getTradeScope() == right.getTradeScope()
                && Objects.equals(left.getItemId(), right.getItemId())
                && left.getMinCount() == right.getMinCount()
                && Objects.equals(left.getEnchantmentId(), right.getEnchantmentId())
                && left.getEnchantmentLevel() == right.getEnchantmentLevel()
                && left.getItemEnchantments().equals(right.getItemEnchantments())
                && Objects.equals(left.getPaymentItemId(), right.getPaymentItemId())
                && left.getMinPrice() == right.getMinPrice()
                && left.getMaxPrice() == right.getMaxPrice();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void openFilterEditor(@Nullable FilterEntry filterToEdit) {
        if (filterToEdit == null) {
            FilterEntry newFilter = new FilterEntry();
            FilterEditorScreen editorScreen = new FilterEditorScreen(this, newFilter,
                    librarianMode, enchantedItemTargetsSupported, enchantmentNameResolver, itemNameResolver,
                    librarianMode ? enchantmentSuggestions : itemSuggestions, enchantmentSuggestions, index -> {
                filters.add(newFilter);
                refreshFiltersList();
            });
            Minecraft.getInstance().gui.setScreen(editorScreen);
        } else {
            int filterIndex = filters.indexOf(filterToEdit);
            if (filterIndex >= 0) {
                FilterEntry filterCopy = new FilterEntry(filterToEdit);
                FilterEditorScreen editorScreen = new FilterEditorScreen(this, filterCopy,
                        librarianMode, enchantedItemTargetsSupported, enchantmentNameResolver, itemNameResolver,
                        librarianMode ? enchantmentSuggestions : itemSuggestions, enchantmentSuggestions, index -> {
                    filters.set(filterIndex, filterCopy);
                    refreshFiltersList();
                });
                Minecraft.getInstance().gui.setScreen(editorScreen);
            }
        }
    }

    private void refreshFiltersList() {
        if (this.filterListWidget == null) {
            return;
        }

        this.filterListWidget.replaceEntries(filters.stream()
                .map(filter -> new FilterListWidget.FilterEntryRow(filter, filterDisplayName(filter),
                        this::openFilterEditor, removedFilter -> {
                    filters.remove(removedFilter);
                    refreshFiltersList();
                }))
                .collect(Collectors.toList()));
    }

    private Component filterDisplayName(FilterEntry filter) {
        if (librarianMode) {
            String enchantmentName = filter.getEnchantmentId() == null || enchantmentNameResolver == null
                    ? null : enchantmentNameResolver.displayName(filter.getEnchantmentId());
            return filter.getDisplayName(enchantmentName);
        }
        String itemName = filter.getItemId() == null || itemNameResolver == null
                ? null : itemNameResolver.displayName(filter.getItemId());
        List<String> itemEnchantmentNames = enchantmentNameResolver == null ? List.of()
                : filter.getItemEnchantments().stream()
                .map(target -> enchantmentNameResolver.displayName(target.id())).toList();
        return filter.getDisplayName(null, itemName, itemEnchantmentNames);
    }

    private static class FilterListWidget extends ContainerObjectSelectionList<FilterListWidget.FilterEntryRow> {
        private static final int SCROLLBAR_WIDTH_WITH_PADDING = 12;
        private final int rowWidth;

        FilterListWidget(Minecraft minecraft, int x, int y, int width, int height) {
            super(minecraft, width, height, y, FILTER_ROW_HEIGHT);
            this.rowWidth = width - SCROLLBAR_WIDTH_WITH_PADDING;
            this.updateSizeAndPosition(width, height, x, y);
            this.centerListVertically = false;
        }

        @Override
        public int getRowWidth() {
            return this.rowWidth;
        }

        @Override
        protected int scrollBarX() {
            return this.getRight() - this.scrollbarWidth();
        }

        private static class FilterEntryRow extends ContainerObjectSelectionList.Entry<FilterEntryRow> {
            private final CycleButton<FilterEntry.Mode> toggleButton;
            private final CycleButton<FilterEntry.TradeScope> scopeButton;
            private final Button filterButton;
            private final Button deleteButton;

            FilterEntryRow(FilterEntry filter, Component displayName,
                           java.util.function.Consumer<FilterEntry> editAction,
                           java.util.function.Consumer<FilterEntry> deleteAction) {
                this.toggleButton = CycleButton.<FilterEntry.Mode>builder(value ->
                                Component.literal(value.name()).withStyle(switch (value) {
                                    case ON -> ChatFormatting.GREEN;
                                    case OFF -> ChatFormatting.GRAY;
                                    case AUTO -> ChatFormatting.GOLD;
                                }),
                                filter.getMode())
                        .withValues(FilterEntry.Mode.ON, FilterEntry.Mode.OFF, FilterEntry.Mode.AUTO)
                        .displayOnlyValue()
                        .create(0, 0, 42, 20, Component.empty(),
                                (cycleButton, newValue) -> {
                                    filter.setMode(newValue);
                                    cycleButton.setTooltip(Tooltip.create(modeTooltip(newValue)));
                                });
                this.toggleButton.setTooltip(Tooltip.create(modeTooltip(filter.getMode())));
                this.scopeButton = CycleButton.<FilterEntry.TradeScope>builder(
                                FilterEntryRow::scopeLabel, filter.getTradeScope())
                        .withValues(FilterEntry.TradeScope.INITIAL, FilterEntry.TradeScope.LATER,
                                FilterEntry.TradeScope.ALL)
                        .displayOnlyValue()
                        .create(0, 0, 76, 20, Component.empty(),
                                (cycleButton, newValue) -> {
                                    filter.setTradeScope(newValue);
                                    cycleButton.setTooltip(Tooltip.create(scopeTooltip(newValue)));
                                });
                this.scopeButton.setTooltip(Tooltip.create(scopeTooltip(filter.getTradeScope())));
                this.filterButton = Button.builder(displayName, button -> editAction.accept(filter))
                        .pos(0, 0)
                        .size(100, 26)
                        .build();
                this.deleteButton = Button.builder(Component.literal("X").withStyle(ChatFormatting.RED), button -> deleteAction.accept(filter))
                        .pos(0, 0)
                        .size(20, 20)
                        .build();
            }

            private static Component modeTooltip(FilterEntry.Mode mode) {
                return Component.translatable(switch (mode) {
                    case ON -> "gui.easyautocycler.filters.mode.on.tooltip";
                    case OFF -> "gui.easyautocycler.filters.mode.off.tooltip";
                    case AUTO -> "gui.easyautocycler.filters.mode.auto.tooltip";
                });
            }

            private static Component scopeLabel(FilterEntry.TradeScope scope) {
                return Component.translatable(switch (scope) {
                    case INITIAL -> "gui.easyautocycler.filters.scope.initial";
                    case LATER -> "gui.easyautocycler.filters.scope.later";
                    case ALL -> "gui.easyautocycler.filters.scope.all";
                });
            }

            private static Component scopeTooltip(FilterEntry.TradeScope scope) {
                return Component.translatable(switch (scope) {
                    case INITIAL -> "gui.easyautocycler.filters.scope.initial.tooltip";
                    case LATER -> "gui.easyautocycler.filters.scope.later.tooltip";
                    case ALL -> "gui.easyautocycler.filters.scope.all.tooltip";
                });
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                int y = this.getContentY() + 5;
                this.toggleButton.setPosition(this.getContentX(), y + 3);
                this.toggleButton.extractRenderState(graphics, mouseX, mouseY, a);

                this.scopeButton.setPosition(this.toggleButton.getRight() + PADDING, y + 3);
                this.scopeButton.extractRenderState(graphics, mouseX, mouseY, a);

                this.deleteButton.setPosition(this.getContentRight() - this.deleteButton.getWidth(), y + 3);
                this.deleteButton.extractRenderState(graphics, mouseX, mouseY, a);

                int filterButtonX = this.scopeButton.getRight() + PADDING;
                int filterButtonRight = this.deleteButton.getX() - PADDING;
                this.filterButton.setPosition(filterButtonX, y);
                this.filterButton.setWidth(filterButtonRight - filterButtonX);
                this.filterButton.extractRenderState(graphics, mouseX, mouseY, a);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(this.toggleButton, this.scopeButton, this.filterButton, this.deleteButton);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(this.toggleButton, this.scopeButton, this.filterButton, this.deleteButton);
            }
        }
    }
}
