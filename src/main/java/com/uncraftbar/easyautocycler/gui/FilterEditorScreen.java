package com.uncraftbar.easyautocycler.gui;

import com.uncraftbar.easyautocycler.filter.FilterEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** Editor for one exact enchanted-book target and its undiscounted emerald-price range. */
public class FilterEditorScreen extends Screen {

    @Nullable private final Screen previousScreen;
    private final FilterEntry filter;
    private final Consumer<Integer> onSave;
    private final List<String> enchantmentSuggestions;

    private SuggestingEditBox enchantmentIdInput;
    private EditBox enchantmentLevelInput;
    private EditBox minPriceInput;
    private EditBox maxPriceInput;
    private Component statusText = Component.empty();
    private String initialEnchantmentId;
    private String initialEnchantmentLevel;
    private String initialMinPrice;
    private String initialMaxPrice;

    private static final int INPUT_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int EDITOR_WIDTH = 390;
    private static final int EDITOR_HEIGHT = 170;

    public FilterEditorScreen(@Nullable Screen previousScreen, FilterEntry filter,
                              List<String> enchantmentSuggestions, List<String> ignoredItemSuggestions,
                              Consumer<Integer> onSave) {
        super(Component.translatable("gui.easyautocycler.filter.title"));
        this.previousScreen = previousScreen;
        this.filter = filter;
        this.onSave = onSave;
        this.enchantmentSuggestions = enchantmentSuggestions;
    }

    @Override
    protected void init() {
        super.init();
        int contentWidth = Math.min(EDITOR_WIDTH - 24, this.width - 24);
        int left = (this.width - contentWidth) / 2;
        int top = Math.max(0, (this.height - EDITOR_HEIGHT) / 2);
        int gap = 12;
        int columnWidth = (contentWidth - gap) / 2;
        int firstY = top + 49;

        enchantmentIdInput = new SuggestingEditBox(this.font, left, firstY, columnWidth, INPUT_HEIGHT,
                Component.translatable("gui.easyautocycler.filter.enchantment_id"), enchantmentSuggestions);
        enchantmentIdInput.setMaxLength(256);
        if (filter.getEnchantmentId() != null) enchantmentIdInput.setValue(filter.getEnchantmentId().toString());
        this.addRenderableWidget(enchantmentIdInput);

        enchantmentLevelInput = new EditBox(this.font, left + columnWidth + gap, firstY, columnWidth, INPUT_HEIGHT,
                Component.translatable("gui.easyautocycler.filter.enchantment_level"));
        enchantmentLevelInput.setValue(String.valueOf(filter.getEnchantmentLevel()));
        this.addRenderableWidget(enchantmentLevelInput);

        minPriceInput = new EditBox(this.font, left, firstY + 39, columnWidth, INPUT_HEIGHT,
                Component.translatable("gui.easyautocycler.filter.min_price"));
        minPriceInput.setValue(String.valueOf(filter.getMinPrice()));
        this.addRenderableWidget(minPriceInput);

        maxPriceInput = new EditBox(this.font, left + columnWidth + gap, firstY + 39, columnWidth, INPUT_HEIGHT,
                Component.translatable("gui.easyautocycler.filter.max_price"));
        maxPriceInput.setValue(String.valueOf(filter.getMaxPrice()));
        this.addRenderableWidget(maxPriceInput);

        initialEnchantmentId = enchantmentIdInput.getValue();
        initialEnchantmentLevel = enchantmentLevelInput.getValue();
        initialMinPrice = minPriceInput.getValue();
        initialMaxPrice = maxPriceInput.getValue();

        int bottomY = Math.min(this.height - BUTTON_HEIGHT - 8, top + EDITOR_HEIGHT - 28);
        int buttonWidth = (contentWidth - gap) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.easyautocycler.filter.save"), b -> saveFilter())
                .pos(left, bottomY).size(buttonWidth, BUTTON_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .pos(left + buttonWidth + gap, bottomY).size(buttonWidth, BUTTON_HEIGHT).build());
    }

    private void saveFilter() {
        String idText = enchantmentIdInput.getValue().trim();
        Identifier id;
        try {
            id = Identifier.parse(idText);
            boolean exists = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .get(ResourceKey.create(Registries.ENCHANTMENT, id)).isPresent();
            if (!exists) throw new IllegalArgumentException("unknown enchantment");
        } catch (RuntimeException exception) {
            setError(Component.translatable("gui.easyautocycler.filter.error.invalid_enchantment_id", idText));
            return;
        }

        Integer level = parseRange(enchantmentLevelInput, 1, 255,
                "gui.easyautocycler.filter.error.invalid_level");
        if (level == null) return;
        Integer minPrice = parseRange(minPriceInput, 1, 64,
                "gui.easyautocycler.filter.error.invalid_price");
        if (minPrice == null) return;
        Integer maxPrice = parseRange(maxPriceInput, 1, 64,
                "gui.easyautocycler.filter.error.invalid_price");
        if (maxPrice == null) return;
        if (minPrice > maxPrice) {
            setError(Component.translatable("gui.easyautocycler.filter.error.price_range"));
            return;
        }

        filter.setEnchantmentId(id);
        filter.setEnchantmentLevel(level);
        filter.setMinPrice(minPrice);
        filter.setMaxPrice(maxPrice);
        filter.setItemId(null);
        filter.setPaymentItemId(null);
        filter.setMinCount(1);
        onSave.accept(0);
        onClose();
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
        int top = Math.max(0, (this.height - EDITOR_HEIGHT) / 2);
        Component title = hasUnsavedChanges()
                ? this.title.copy().append(Component.literal(" *").withStyle(ChatFormatting.GOLD)) : this.title;
        graphics.text(this.font, title, left, top + 9, 0xFFF4F6F8, false);
        Component subtitle = statusText.getString().isEmpty()
                ? Component.translatable("gui.easyautocycler.filter.subtitle") : statusText;
        graphics.text(this.font, subtitle, left, top + 22,
                statusText.getString().isEmpty() ? 0xFFAAB2BF : 0xFFD9534F, false);
        drawLabel(graphics, enchantmentIdInput, "gui.easyautocycler.filter.enchantment_id_short");
        drawLabel(graphics, enchantmentLevelInput, "gui.easyautocycler.filter.enchantment_level");
        drawLabel(graphics, minPriceInput, "gui.easyautocycler.filter.min_price");
        drawLabel(graphics, maxPriceInput, "gui.easyautocycler.filter.max_price");
        enchantmentIdInput.extractSuggestionList(graphics, mouseX, mouseY);
    }

    private boolean hasUnsavedChanges() {
        return initialEnchantmentId != null && (!initialEnchantmentId.equals(enchantmentIdInput.getValue())
                || !initialEnchantmentLevel.equals(enchantmentLevelInput.getValue())
                || !initialMinPrice.equals(minPriceInput.getValue())
                || !initialMaxPrice.equals(maxPriceInput.getValue()));
    }

    private void drawLabel(GuiGraphicsExtractor graphics, EditBox input, String key) {
        graphics.text(this.font, Component.translatable(key), input.getX(), input.getY() - 11, 0xFFAAB2BF, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (enchantmentIdInput != null && enchantmentIdInput.clickSuggestion(event)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(previousScreen); }
    @Override public boolean isPauseScreen() { return false; }
}
