package com.uncraftbar.easyautocycler.filter;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * Represents a single filter entry in the automated trade cycling system.
 * Each entry contains the criteria for a trade that should be detected.
 */
public class FilterEntry {
    private boolean enabled = true;
    
    // Item-related criteria
    private Identifier itemId;
    private int minCount = 1;
    
    // Enchantment-related criteria
    private Identifier enchantmentId;
    private int enchantmentLevel = 1;
    
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
        this.enabled = other.enabled;
        this.itemId = other.itemId;
        this.minCount = other.minCount;
        this.enchantmentId = other.enchantmentId;
        this.enchantmentLevel = other.enchantmentLevel;
        this.paymentItemId = other.paymentItemId;
        this.minPrice = other.minPrice;
        this.maxPrice = other.maxPrice;
    }

    // Getters and setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
        MutableComponent component = Component.empty();

        if (enchantmentId != null) {
            component.append(Component.literal(enchantmentId.getPath()).withStyle(ChatFormatting.AQUA));
            component.append(Component.literal("  Lv. " + enchantmentLevel).withStyle(ChatFormatting.GRAY));

            if (itemId != null) {
                component.append(Component.literal("  •  ").withStyle(ChatFormatting.DARK_GRAY));
                component.append(Component.literal(itemId.getPath()).withStyle(ChatFormatting.GOLD));
            }
        } else if (itemId != null) {
            component.append(Component.literal(itemId.getPath()).withStyle(ChatFormatting.GOLD));
        } else {
            component.append(Component.literal("Empty filter").withStyle(ChatFormatting.RED));
        }

        if (itemId != null && minCount > 1) {
            component.append(Component.literal("  ×" + minCount).withStyle(ChatFormatting.GRAY));
        }
        component.append(Component.literal("  •  " + minPrice + "-" + maxPrice + " ").withStyle(ChatFormatting.DARK_GRAY));
        component.append(Component.literal(paymentItemId == null ? "emeralds" : paymentItemId.getPath())
                .withStyle(ChatFormatting.GREEN));

        return component;
    }
}
