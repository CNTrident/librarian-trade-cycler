package com.uncraftbar.easyautocycler.filter;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single filter entry in the automated trade cycling system.
 * Each entry contains the criteria for a trade that should be detected.
 */
public class FilterEntry {
    public record EnchantmentTarget(Identifier id, int level) {}

    public enum Mode {
        ON,
        OFF,
        AUTO
    }

    public enum TradeScope {
        INITIAL,
        LATER,
        ALL
    }

    private Mode mode = Mode.ON;
    private TradeScope tradeScope = TradeScope.INITIAL;
    
    // Item-related criteria
    private Identifier itemId;
    private int minCount = 1;
    
    // Enchantment-related criteria
    private Identifier enchantmentId;
    private int enchantmentLevel = 1;
    private List<EnchantmentTarget> itemEnchantments = new ArrayList<>();
    
    // Payment criteria
    private Identifier paymentItemId; // null = emeralds (default)
    private int minPrice = 1;
    private int maxPrice = 64;

    public FilterEntry() {
        // Default constructor
    }
    
    /**
     * Copy constructor to create a clone of an existing filter
     */
    public FilterEntry(FilterEntry other) {
        this.mode = other.mode;
        this.tradeScope = other.tradeScope;
        this.itemId = other.itemId;
        this.minCount = other.minCount;
        this.enchantmentId = other.enchantmentId;
        this.enchantmentLevel = other.enchantmentLevel;
        this.itemEnchantments = new ArrayList<>(other.itemEnchantments);
        this.paymentItemId = other.paymentItemId;
        this.minPrice = other.minPrice;
        this.maxPrice = other.maxPrice;
    }

    // Getters and setters
    public boolean isEnabled() {
        return mode != Mode.OFF;
    }

    public void setEnabled(boolean enabled) {
        this.mode = enabled ? Mode.ON : Mode.OFF;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.ON : mode;
    }

    public boolean isAutoDisable() {
        return mode == Mode.AUTO;
    }

    public TradeScope getTradeScope() {
        return tradeScope;
    }

    public void setTradeScope(TradeScope tradeScope) {
        this.tradeScope = tradeScope == null ? TradeScope.INITIAL : tradeScope;
    }

    public Identifier getItemId() {
        return itemId;
    }

    public void setItemId(Identifier itemId) {
        this.itemId = itemId;
    }

    public int getMinCount() {
        return minCount;
    }

    public void setMinCount(int minCount) {
        this.minCount = minCount;
    }

    public Identifier getEnchantmentId() {
        return enchantmentId;
    }

    public void setEnchantmentId(Identifier enchantmentId) {
        this.enchantmentId = enchantmentId;
    }

    public int getEnchantmentLevel() {
        return enchantmentLevel;
    }

    public void setEnchantmentLevel(int enchantmentLevel) {
        this.enchantmentLevel = enchantmentLevel;
    }

    public List<EnchantmentTarget> getItemEnchantments() {
        return List.copyOf(itemEnchantments);
    }

    public void setItemEnchantments(List<EnchantmentTarget> itemEnchantments) {
        this.itemEnchantments = itemEnchantments == null
                ? new ArrayList<>() : new ArrayList<>(itemEnchantments);
    }

    public Identifier getPaymentItemId() {
        return paymentItemId;
    }

    public void setPaymentItemId(Identifier paymentItemId) {
        this.paymentItemId = paymentItemId;
    }

    public int getMaxPrice() {
        return maxPrice;
    }

    public int getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(int minPrice) {
        this.minPrice = minPrice;
    }

    public void setMaxPrice(int maxPrice) {
        this.maxPrice = maxPrice;
    }
    
    /**
     * Checks if this filter is valid (has at least one criterion set)
     */
    public boolean isValid() {
        return itemId != null || enchantmentId != null;
    }
    
    /**
     * Creates a human-readable display name for this filter
     */
    public Component getDisplayName() {
        return getDisplayName(null, null, List.of());
    }

    public Component getDisplayName(String enchantmentDisplayName) {
        return getDisplayName(enchantmentDisplayName, null, List.of());
    }

    public Component getDisplayName(String enchantmentDisplayName, String itemDisplayName) {
        return getDisplayName(enchantmentDisplayName, itemDisplayName, List.of());
    }

    public Component getDisplayName(String enchantmentDisplayName, String itemDisplayName,
                                    List<String> itemEnchantmentDisplayNames) {
        MutableComponent component = Component.empty();

        if (itemId != null) {
            String itemName = itemDisplayName == null || itemDisplayName.isBlank()
                    ? itemId.getPath() : itemDisplayName;
            component.append(Component.literal(itemName).withStyle(ChatFormatting.GOLD));
            for (int index = 0; index < itemEnchantments.size(); index++) {
                EnchantmentTarget target = itemEnchantments.get(index);
                String enchantmentName = index < itemEnchantmentDisplayNames.size()
                        && !itemEnchantmentDisplayNames.get(index).isBlank()
                        ? itemEnchantmentDisplayNames.get(index) : target.id().getPath();
                component.append(Component.literal("  •  ").withStyle(ChatFormatting.DARK_GRAY));
                component.append(Component.literal(enchantmentName).withStyle(ChatFormatting.AQUA));
                component.append(Component.translatable("gui.easyautocycler.filter.display.level",
                        target.level()).withStyle(ChatFormatting.GRAY));
            }
        } else if (enchantmentId != null) {
            String name = enchantmentDisplayName == null || enchantmentDisplayName.isBlank()
                    ? enchantmentId.getPath() : enchantmentDisplayName;
            component.append(Component.literal(name).withStyle(ChatFormatting.AQUA));
            component.append(Component.translatable("gui.easyautocycler.filter.display.level",
                    enchantmentLevel).withStyle(ChatFormatting.GRAY));
        } else {
            component.append(Component.translatable("gui.easyautocycler.filter.empty")
                    .withStyle(ChatFormatting.RED));
        }

        if (itemId != null && minCount > 1) {
            component.append(Component.literal("  ×" + minCount).withStyle(ChatFormatting.GRAY));
        }
        component.append(Component.literal("  •  " + minPrice + "-" + maxPrice + " ").withStyle(ChatFormatting.DARK_GRAY));
        component.append((paymentItemId == null
                ? Component.translatable("gui.easyautocycler.filter.payment.emeralds")
                : Component.literal(paymentItemId.getPath())).withStyle(ChatFormatting.GREEN));

        return component;
    }
}
