package com.uncraftbar.easyautocycler.gui;

import com.uncraftbar.easyautocycler.filter.FilterEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Editor for either an enchanted-book target or another villager's item target. */
public class FilterEditorScreen extends Screen {

    @Nullable private final Screen previousScreen;
    private final FilterEntry filter;
    private final Consumer<Integer> onSave;
    private final boolean librarianMode;
    private final boolean enchantedItemTargetsSupported;
    @Nullable private final EnchantmentNameResolver enchantmentNameResolver;
    @Nullable private final ItemNameResolver itemNameResolver;
    private final List<String> targetSuggestions;
    private final List<String> enchantmentSuggestions;

    private SuggestingEditBox enchantmentIdInput;
    private EditBox enchantmentLevelInput;
    private EditBox minPriceInput;
    private EditBox maxPriceInput;
    private SuggestingEditBox itemEnchantmentInput;
    private EditBox itemEnchantmentLevelInput;
    private Component statusText = Component.empty();
    private String initialEnchantmentId;
    private String initialEnchantmentLevel;
    private String initialMinPrice;
    private String initialMaxPrice;
    private String initialItemEnchantment;
    private String initialItemEnchantmentLevel;
    private boolean normalizingInputs;

    private static final int INPUT_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int EDITOR_WIDTH = 390;
    private static final int EDITOR_HEIGHT = 170;
    private static final int ENCHANTED_ITEM_EDITOR_HEIGHT = 209;

    public FilterEditorScreen(@Nullable Screen previousScreen, FilterEntry filter,
                               boolean librarianMode,
                               boolean enchantedItemTargetsSupported,
                               @Nullable EnchantmentNameResolver enchantmentNameResolver,
                               @Nullable ItemNameResolver itemNameResolver,
                               List<String> targetSuggestions,
                               List<String> enchantmentSuggestions,
                               Consumer<Integer> onSave) {
        super(Component.translatable(librarianMode
                ? "gui.easyautocycler.filter.title"
                : "gui.easyautocycler.filter.title.other"));
        this.previousScreen = previousScreen;
        this.filter = filter;
        this.onSave = onSave;
        this.librarianMode = librarianMode;
        this.enchantedItemTargetsSupported = enchantedItemTargetsSupported;
        this.enchantmentNameResolver = enchantmentNameResolver;
        this.itemNameResolver = itemNameResolver;
        this.targetSuggestions = targetSuggestions;
        this.enchantmentSuggestions = enchantmentSuggestions;
    }

    @Override
    protected void init() {
        super.init();
        int contentWidth = Math.min(EDITOR_WIDTH - 24, this.width - 24);
        int left = (this.width - contentWidth) / 2;
        int editorHeight = editorHeight();
        int top = Math.max(0, (this.height - editorHeight) / 2);
        int gap = 12;
        int columnWidth = (contentWidth - gap) / 2;
        int firstY = top + 49;

        enchantmentIdInput = new SuggestingEditBox(this.font, left, firstY, columnWidth, INPUT_HEIGHT,
                Component.translatable(librarianMode
                        ? "gui.easyautocycler.filter.enchantment_id"
                        : "gui.easyautocycler.filter.item_name"), targetSuggestions);
        enchantmentIdInput.setMaxLength(256);
        enchantmentIdInput.setHint(Component.translatable(librarianMode
                ? "gui.easyautocycler.filter.hint.enchantment"
                : "gui.easyautocycler.filter.hint.item"));
        if (librarianMode && filter.getEnchantmentId() != null && enchantmentNameResolver != null) {
            enchantmentIdInput.setValue(enchantmentNameResolver.preferredInput(filter.getEnchantmentId()));
        } else if (!librarianMode && filter.getItemId() != null && itemNameResolver != null) {
            enchantmentIdInput.setValue(itemNameResolver.preferredInput(filter.getItemId()));
        }
        this.addRenderableWidget(enchantmentIdInput);

        enchantmentLevelInput = new EditBox(this.font, left + columnWidth + gap, firstY, columnWidth, INPUT_HEIGHT,
                Component.translatable(librarianMode
                        ? "gui.easyautocycler.filter.enchantment_level"
                        : "gui.easyautocycler.filter.min_count"));
        enchantmentLevelInput.setHint(Component.translatable(librarianMode
                ? "gui.easyautocycler.filter.hint.level"
                : "gui.easyautocycler.filter.hint.count"));
        if (librarianMode && filter.getEnchantmentId() != null) {
            enchantmentLevelInput.setValue(String.valueOf(filter.getEnchantmentLevel()));
        } else if (!librarianMode && filter.getItemId() != null) {
            enchantmentLevelInput.setValue(String.valueOf(filter.getMinCount()));
        }
        this.addRenderableWidget(enchantmentLevelInput);

        minPriceInput = new EditBox(this.font, left, firstY + 39, columnWidth, INPUT_HEIGHT,
                Component.translatable("gui.easyautocycler.filter.min_price"));
        minPriceInput.setHint(Component.translatable("gui.easyautocycler.filter.hint.min_price"));
        if ((librarianMode && filter.getEnchantmentId() != null)
                || (!librarianMode && filter.getItemId() != null)) {
            minPriceInput.setValue(String.valueOf(filter.getMinPrice()));
        }
        this.addRenderableWidget(minPriceInput);

        maxPriceInput = new EditBox(this.font, left + columnWidth + gap, firstY + 39, columnWidth, INPUT_HEIGHT,
                Component.translatable("gui.easyautocycler.filter.max_price"));
        maxPriceInput.setHint(Component.translatable("gui.easyautocycler.filter.hint.max_price"));
        if ((librarianMode && filter.getEnchantmentId() != null)
                || (!librarianMode && filter.getItemId() != null)) {
            maxPriceInput.setValue(String.valueOf(filter.getMaxPrice()));
        }
        this.addRenderableWidget(maxPriceInput);

        if (!librarianMode && enchantedItemTargetsSupported) {
            int enchantmentY = firstY + 78;
            itemEnchantmentInput = new SuggestingEditBox(this.font, left, enchantmentY,
                    columnWidth, INPUT_HEIGHT,
                    Component.translatable("gui.easyautocycler.filter.optional_item_enchantment"),
                    enchantmentSuggestions);
            itemEnchantmentInput.setMaxLength(256);
            itemEnchantmentInput.setCommaSeparatedValues(true);
            itemEnchantmentInput.setHint(Component.translatable(
                    "gui.easyautocycler.filter.hint.optional_enchantment"));
            if (!filter.getItemEnchantments().isEmpty() && enchantmentNameResolver != null) {
                itemEnchantmentInput.setValue(filter.getItemEnchantments().stream()
                        .map(target -> enchantmentNameResolver.preferredInput(target.id()))
                        .collect(Collectors.joining(", ")));
            }
            this.addRenderableWidget(itemEnchantmentInput);

            itemEnchantmentLevelInput = new EditBox(this.font, left + columnWidth + gap,
                    enchantmentY, columnWidth, INPUT_HEIGHT,
                    Component.translatable("gui.easyautocycler.filter.optional_enchantment_level"));
            itemEnchantmentLevelInput.setHint(Component.translatable(
                    "gui.easyautocycler.filter.hint.optional_level"));
            if (!filter.getItemEnchantments().isEmpty()) {
                itemEnchantmentLevelInput.setValue(filter.getItemEnchantments().stream()
                        .map(target -> String.valueOf(target.level()))
                        .collect(Collectors.joining(", ")));
            }
            this.addRenderableWidget(itemEnchantmentLevelInput);
        }

        enchantmentIdInput.setChangeListener(value -> normalizeInputs());
        enchantmentLevelInput.setResponder(value -> normalizeInputs());
        minPriceInput.setResponder(value -> normalizeInputs());
        maxPriceInput.setResponder(value -> normalizeInputs());
        if (itemEnchantmentInput != null) itemEnchantmentInput.setChangeListener(value -> normalizeInputs());
        if (itemEnchantmentLevelInput != null) itemEnchantmentLevelInput.setResponder(value -> normalizeInputs());

        initialEnchantmentId = enchantmentIdInput.getValue();
        initialEnchantmentLevel = enchantmentLevelInput.getValue();
        initialMinPrice = minPriceInput.getValue();
        initialMaxPrice = maxPriceInput.getValue();
        initialItemEnchantment = valueOf(itemEnchantmentInput);
        initialItemEnchantmentLevel = valueOf(itemEnchantmentLevelInput);

        int bottomY = Math.min(this.height - BUTTON_HEIGHT - 8, top + editorHeight - 28);
        int buttonWidth = (contentWidth - gap) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.easyautocycler.filter.save"), b -> saveFilter())
                .pos(left, bottomY).size(buttonWidth, BUTTON_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .pos(left + buttonWidth + gap, bottomY).size(buttonWidth, BUTTON_HEIGHT).build());
    }

    private int editorHeight() {
        return !librarianMode && enchantedItemTargetsSupported
                ? ENCHANTED_ITEM_EDITOR_HEIGHT : EDITOR_HEIGHT;
    }

    private void saveFilter() {
        normalizeInputs();
        normalizePriceRange();
        String idText = enchantmentIdInput.getValue().trim();
        Identifier id = librarianMode && enchantmentNameResolver != null
                ? enchantmentNameResolver.resolve(idText)
                : itemNameResolver == null ? null : itemNameResolver.resolve(idText);
        if (id == null) {
            setError(Component.translatable(librarianMode
                    ? "gui.easyautocycler.filter.error.invalid_enchantment_id"
                    : "gui.easyautocycler.filter.error.invalid_item_name", idText));
            return;
        }

        int maximumLevel = librarianMode && enchantmentNameResolver != null
                ? enchantmentNameResolver.maxLevel(id) : 64;
        Integer level = parseRange(enchantmentLevelInput, 1, maximumLevel,
                librarianMode ? "gui.easyautocycler.filter.error.invalid_level"
                        : "gui.easyautocycler.filter.error.invalid_count");
        if (level == null) return;
        int maximumMinimumPrice = librarianMode && enchantmentNameResolver != null
                ? enchantmentNameResolver.maxTradePrice(id, level) : 64;
        Integer minPrice = parseRange(minPriceInput, 1, maximumMinimumPrice,
                "gui.easyautocycler.filter.error.invalid_price");
        if (minPrice == null) return;
        Integer maxPrice = parseRange(maxPriceInput, 1, 64,
                "gui.easyautocycler.filter.error.invalid_price");
        if (maxPrice == null) return;
        if (minPrice > maxPrice) minPrice = maxPrice;

        List<FilterEntry.EnchantmentTarget> itemEnchantments = parseItemEnchantments(id);
        if (itemEnchantments == null) return;

        if (librarianMode) {
            filter.setEnchantmentId(id);
            filter.setEnchantmentLevel(level);
            filter.setItemId(null);
            filter.setMinCount(1);
            filter.setItemEnchantments(List.of());
        } else {
            filter.setItemId(id);
            filter.setMinCount(level);
            filter.setEnchantmentId(null);
            filter.setEnchantmentLevel(1);
            filter.setItemEnchantments(itemEnchantments);
        }
        filter.setMinPrice(minPrice);
        filter.setMaxPrice(maxPrice);
        filter.setPaymentItemId(null);
        onSave.accept(0);
        onClose();
    }

    @Nullable
    private List<FilterEntry.EnchantmentTarget> parseItemEnchantments(Identifier itemId) {
        if (librarianMode || !enchantedItemTargetsSupported || itemEnchantmentInput == null
                || itemEnchantmentInput.getValue().isBlank()) return List.of();
        if (enchantmentNameResolver == null || itemNameResolver == null) return null;

        String[] names = splitValues(itemEnchantmentInput.getValue());
        String[] levels = splitValues(valueOf(itemEnchantmentLevelInput));
        if (names.length != levels.length) {
            setError(Component.translatable("gui.easyautocycler.filter.error.enchantment_count_mismatch"));
            return null;
        }

        List<FilterEntry.EnchantmentTarget> result = new ArrayList<>();
        List<String> normalizedLevels = new ArrayList<>();
        Set<Identifier> seen = new HashSet<>();
        for (int index = 0; index < names.length; index++) {
            Identifier enchantmentId = enchantmentNameResolver.resolve(names[index]);
            if (enchantmentId == null) {
                setError(Component.translatable("gui.easyautocycler.filter.error.invalid_enchantment_id",
                        names[index].trim()));
                return null;
            }
            if (!seen.add(enchantmentId)) {
                setError(Component.translatable("gui.easyautocycler.filter.error.duplicate_enchantment"));
                return null;
            }
            if (!enchantmentNameResolver.canEnchant(enchantmentId, itemNameResolver.defaultStack(itemId))) {
                setError(Component.translatable(
                        "gui.easyautocycler.filter.error.incompatible_enchantment"));
                return null;
            }
            Integer parsedLevel = parseInteger(levels[index]);
            if (parsedLevel == null) {
                setError(Component.translatable("gui.easyautocycler.filter.error.invalid_level",
                        levels[index].trim()));
                return null;
            }
            int level = Math.max(1, Math.min(enchantmentNameResolver.maxLevel(enchantmentId), parsedLevel));
            result.add(new FilterEntry.EnchantmentTarget(enchantmentId, level));
            normalizedLevels.add(String.valueOf(level));
        }
        setValueIfChanged(itemEnchantmentLevelInput, String.join(", ", normalizedLevels));
        return result;
    }

    private static String[] splitValues(String value) {
        return value.trim().split("\\s*[,，]\\s*", -1);
    }

    private void normalizeInputs() {
        if (normalizingInputs || enchantmentIdInput == null || enchantmentLevelInput == null
                || minPriceInput == null || maxPriceInput == null) return;

        normalizingInputs = true;
        try {
            Identifier enchantmentId = librarianMode && enchantmentNameResolver != null
                    ? enchantmentNameResolver.resolve(enchantmentIdInput.getValue()) : null;
            int maximumLevel = librarianMode
                    ? enchantmentId == null || enchantmentNameResolver == null
                    ? EnchantmentNameResolver.DEFAULT_MAX_LEVEL : enchantmentNameResolver.maxLevel(enchantmentId)
                    : 64;
            clampNumericInput(enchantmentLevelInput, 1, maximumLevel);
            clampNumericInput(maxPriceInput, 1, 64);

            Integer level = parseInteger(enchantmentLevelInput.getValue());
            int maximumMinimumPrice = librarianMode && enchantmentId != null && level != null
                    ? enchantmentNameResolver.maxTradePrice(enchantmentId, level) : 64;
            clampNumericInput(minPriceInput, 1, maximumMinimumPrice);

            normalizeItemEnchantmentLevels();

        } finally {
            normalizingInputs = false;
        }
    }

    private void normalizeItemEnchantmentLevels() {
        if (itemEnchantmentInput == null || itemEnchantmentLevelInput == null
                || enchantmentNameResolver == null || itemEnchantmentInput.getValue().isBlank()) return;
        String[] names = splitValues(itemEnchantmentInput.getValue());
        String[] levels = splitValues(itemEnchantmentLevelInput.getValue());
        if (names.length != levels.length) return;
        List<String> normalized = new ArrayList<>();
        for (int index = 0; index < names.length; index++) {
            Identifier id = enchantmentNameResolver.resolve(names[index]);
            Integer level = parseInteger(levels[index]);
            if (id == null || level == null) return;
            normalized.add(String.valueOf(Math.max(1,
                    Math.min(enchantmentNameResolver.maxLevel(id), level))));
        }
        setValueIfChanged(itemEnchantmentLevelInput, String.join(", ", normalized));
    }

    private void normalizePriceRange() {
        if (normalizingInputs) return;
        normalizingInputs = true;
        try {
            Integer minimumPrice = parseInteger(minPriceInput.getValue());
            Integer maximumPrice = parseInteger(maxPriceInput.getValue());
            if (minimumPrice != null && maximumPrice != null && minimumPrice > maximumPrice) {
                setValueIfChanged(minPriceInput, String.valueOf(maximumPrice));
            }
        } finally {
            normalizingInputs = false;
        }
    }

    private static void clampNumericInput(EditBox input, int minimum, int maximum) {
        Integer value = parseInteger(input.getValue());
        if (value == null) return;
        int clamped = Math.max(minimum, Math.min(maximum, value));
        setValueIfChanged(input, String.valueOf(clamped));
    }

    @Nullable
    private static Integer parseInteger(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static void setValueIfChanged(EditBox input, String value) {
        if (!input.getValue().equals(value)) {
            input.setValue(value);
            input.moveCursorToEnd(false);
        }
    }

    @Nullable
    private Integer parseRange(EditBox input, int min, int max, String errorKey) {
        try {
            int value = Integer.parseInt(input.getValue().trim());
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            setError(Component.translatable(errorKey, input.getValue()));
            return null;
        }
    }

    private void setError(Component message) {
        statusText = message.copy().withStyle(ChatFormatting.RED);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int contentWidth = Math.min(EDITOR_WIDTH - 24, this.width - 24);
        int left = (this.width - contentWidth) / 2;
        int top = Math.max(0, (this.height - editorHeight()) / 2);
        Component title = hasUnsavedChanges()
                ? this.title.copy().append(Component.literal(" *").withStyle(ChatFormatting.GOLD)) : this.title;
        graphics.text(this.font, title, left, top + 9, 0xFFF4F6F8, false);
        Component subtitle = statusText.getString().isEmpty()
                ? Component.translatable(librarianMode
                ? "gui.easyautocycler.filter.subtitle"
                : enchantedItemTargetsSupported
                ? "gui.easyautocycler.filter.subtitle.other.enchantable"
                : "gui.easyautocycler.filter.subtitle.other") : statusText;
        graphics.text(this.font, subtitle, left, top + 22,
                statusText.getString().isEmpty() ? 0xFFAAB2BF : 0xFFD9534F, false);
        drawLabel(graphics, enchantmentIdInput, librarianMode
                ? "gui.easyautocycler.filter.enchantment_id_short"
                : "gui.easyautocycler.filter.item_name_short");
        drawLabel(graphics, enchantmentLevelInput, librarianMode
                ? "gui.easyautocycler.filter.enchantment_level"
                : "gui.easyautocycler.filter.min_count");
        drawLabel(graphics, minPriceInput, "gui.easyautocycler.filter.min_price");
        drawLabel(graphics, maxPriceInput, "gui.easyautocycler.filter.max_price");
        if (itemEnchantmentInput != null && itemEnchantmentLevelInput != null) {
            drawLabel(graphics, itemEnchantmentInput,
                    "gui.easyautocycler.filter.optional_item_enchantment");
            drawLabel(graphics, itemEnchantmentLevelInput,
                    "gui.easyautocycler.filter.optional_enchantment_level");
        }
        enchantmentIdInput.extractSuggestionList(graphics, mouseX, mouseY);
        if (itemEnchantmentInput != null) {
            itemEnchantmentInput.extractSuggestionList(graphics, mouseX, mouseY);
        }
    }

    private boolean hasUnsavedChanges() {
        return initialEnchantmentId != null && (!initialEnchantmentId.equals(enchantmentIdInput.getValue())
                || !initialEnchantmentLevel.equals(enchantmentLevelInput.getValue())
                || !initialMinPrice.equals(minPriceInput.getValue())
                || !initialMaxPrice.equals(maxPriceInput.getValue())
                || !initialItemEnchantment.equals(valueOf(itemEnchantmentInput))
                || !initialItemEnchantmentLevel.equals(valueOf(itemEnchantmentLevelInput)));
    }

    private static String valueOf(@Nullable EditBox input) {
        return input == null ? "" : input.getValue();
    }

    private void drawLabel(GuiGraphicsExtractor graphics, EditBox input, String key) {
        graphics.text(this.font, Component.translatable(key), input.getX(), input.getY() - 11, 0xFFAAB2BF, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (enchantmentIdInput != null && enchantmentIdInput.clickSuggestion(event)) return true;
        if (itemEnchantmentInput != null && itemEnchantmentInput.clickSuggestion(event)) return true;
        boolean maximumPriceWasFocused = maxPriceInput != null && maxPriceInput.isFocused();
        boolean handled = super.mouseClicked(event, doubleClick);
        if (maximumPriceWasFocused && !maxPriceInput.isFocused()) normalizePriceRange();
        return handled;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean maximumPriceWasFocused = maxPriceInput != null && maxPriceInput.isFocused();
        boolean handled = super.keyPressed(event);
        if (maximumPriceWasFocused && !maxPriceInput.isFocused()) normalizePriceRange();
        return handled;
    }

    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(previousScreen); }
    @Override public boolean isPauseScreen() { return false; }
}
