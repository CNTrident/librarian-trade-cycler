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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ConfigScreen extends Screen {

    @Nullable
    private final Screen previousScreen;

    private FilterListWidget filterListWidget;
    private List<String> enchantmentSuggestions = List.of();
    private List<String> itemSuggestions = List.of();

    private List<FilterEntry> filters = new ArrayList<>();
    private final List<FilterEntry> originalFilters = new ArrayList<>();
    private boolean originalMatchAny;
    private CycleButton<Boolean> matchModeCycleButton;
    private boolean matchAny = true;

    private static final int PADDING = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int FILTER_ROW_HEIGHT = 38;

    public ConfigScreen(@Nullable Screen previousScreen, Component title) {
        super(title);
        this.previousScreen = previousScreen;

        this.filters.addAll(AutomationManager.INSTANCE.getFilterEntries());
        this.originalFilters.clear();
        this.originalFilters.addAll(this.filters.stream().map(FilterEntry::new).collect(Collectors.toList()));
        this.originalMatchAny = AutomationManager.INSTANCE.isMatchAny();
        this.matchAny = AutomationManager.INSTANCE.isMatchAny();
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
            this.enchantmentSuggestions = this.minecraft.level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .listElementIds()
                    .map(k -> k.identifier().toString())
                    .sorted()
                    .collect(Collectors.toList());

            this.itemSuggestions = BuiltInRegistries.ITEM.keySet().stream()
                    .map(Identifier::toString)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            EasyAutoCyclerMod.LOGGER.error("Failed to load registry for suggestions", e);
            this.enchantmentSuggestions = List.of();
            this.itemSuggestions = List.of();
        }

        int contentWidth = Math.min(380, this.width - 20);
        int guiLeft = (this.width - contentWidth) / 2;
        int currentY = 48;

        int topButtonWidth = Math.min(116, (contentWidth - PADDING) / 2);
        int buttonSpacing = PADDING;
        int buttonsStartX = guiLeft;

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.easyautocycler.filters.add_compact"),
                button -> openFilterEditor(null))
                .pos(buttonsStartX, currentY)
                .size(topButtonWidth, BUTTON_HEIGHT)
                .build());

        this.matchModeCycleButton = CycleButton.<Boolean>builder(value ->
                        Component.translatable(value
                                ? "gui.easyautocycler.filters.match_any_compact"
                                : "gui.easyautocycler.filters.match_all_compact"), matchAny)
                .withValues(true, false)
                .displayOnlyValue()
                .create(buttonsStartX + topButtonWidth + buttonSpacing, currentY,
                        contentWidth - topButtonWidth - buttonSpacing, BUTTON_HEIGHT,
                        Component.empty(),
                        (cycleButton, newValue) -> {
                            matchAny = newValue;
                            updateMatchModeTooltip();
                        });
        this.addRenderableWidget(this.matchModeCycleButton);
        updateMatchModeTooltip();

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
        int contentWidth = Math.min(380, this.width - 20);
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
        AutomationManager.INSTANCE.setMatchAny(matchModeCycleButton.getValue());
        AutomationManager.INSTANCE.setFilterEntries(filters);

        this.sendMessageToPlayer(Component.literal("Configuration saved!").withStyle(ChatFormatting.GREEN));
        this.onClose();
    }

    private void onClear(Button button) {
        filters.clear();
        matchAny = true;
        refreshFiltersList();

        if (this.matchModeCycleButton != null) this.matchModeCycleButton.setValue(true);

        this.sendMessageToPlayer(Component.literal("Configuration cleared (unsaved).").withStyle(ChatFormatting.YELLOW));
    }

    private void sendMessageToPlayer(Component message) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(message);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.previousScreen);
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
        return left.isEnabled() == right.isEnabled()
                && Objects.equals(left.getItemId(), right.getItemId())
                && left.getMinCount() == right.getMinCount()
                && Objects.equals(left.getEnchantmentId(), right.getEnchantmentId())
                && left.getEnchantmentLevel() == right.getEnchantmentLevel()
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
            FilterEditorScreen editorScreen = new FilterEditorScreen(this, newFilter, enchantmentSuggestions, itemSuggestions, index -> {
                filters.add(newFilter);
                refreshFiltersList();
            });
            Minecraft.getInstance().setScreen(editorScreen);
        } else {
            int filterIndex = filters.indexOf(filterToEdit);
            if (filterIndex >= 0) {
                FilterEntry filterCopy = new FilterEntry(filterToEdit);
                FilterEditorScreen editorScreen = new FilterEditorScreen(this, filterCopy, enchantmentSuggestions, itemSuggestions, index -> {
                    filters.set(filterIndex, filterCopy);
                    refreshFiltersList();
                });
                Minecraft.getInstance().setScreen(editorScreen);
            }
        }
    }

    private void refreshFiltersList() {
        if (this.filterListWidget == null) {
            return;
        }

        this.filterListWidget.replaceEntries(filters.stream()
                .map(filter -> new FilterListWidget.FilterEntryRow(filter, this::openFilterEditor, removedFilter -> {
                    filters.remove(removedFilter);
                    refreshFiltersList();
                }))
                .collect(Collectors.toList()));
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
            private final CycleButton<Boolean> toggleButton;
            private final Button filterButton;
            private final Button deleteButton;

            FilterEntryRow(FilterEntry filter, java.util.function.Consumer<FilterEntry> editAction, java.util.function.Consumer<FilterEntry> deleteAction) {
                this.toggleButton = CycleButton.<Boolean>builder(value ->
                                Component.literal(value ? "ON" : "OFF")
                                        .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                                filter.isEnabled())
                        .withValues(true, false)
                        .displayOnlyValue()
                        .create(0, 0, 38, 20, Component.empty(), (cycleButton, newValue) -> filter.setEnabled(newValue));
                this.filterButton = Button.builder(filter.getDisplayName(), button -> editAction.accept(filter))
                        .pos(0, 0)
                        .size(100, 26)
                        .build();
                this.deleteButton = Button.builder(Component.literal("X").withStyle(ChatFormatting.RED), button -> deleteAction.accept(filter))
                        .pos(0, 0)
                        .size(20, 20)
                        .build();
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                int y = this.getContentY() + 5;
                this.toggleButton.setPosition(this.getContentX(), y + 3);
                this.toggleButton.extractRenderState(graphics, mouseX, mouseY, a);

                this.deleteButton.setPosition(this.getContentRight() - this.deleteButton.getWidth(), y + 3);
                this.deleteButton.extractRenderState(graphics, mouseX, mouseY, a);

                int filterButtonX = this.toggleButton.getRight() + PADDING;
                int filterButtonRight = this.deleteButton.getX() - PADDING;
                this.filterButton.setPosition(filterButtonX, y);
                this.filterButton.setWidth(filterButtonRight - filterButtonX);
                this.filterButton.extractRenderState(graphics, mouseX, mouseY, a);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(this.toggleButton, this.filterButton, this.deleteButton);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(this.toggleButton, this.filterButton, this.deleteButton);
            }
        }
    }
}
