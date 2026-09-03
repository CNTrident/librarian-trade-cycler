package com.uncraftbar.easyautocycler.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uncraftbar.easyautocycler.EasyAutoCyclerMod;
import com.uncraftbar.easyautocycler.filter.FilterEntry;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles saving and loading filter configurations to/from JSON files
 */
public class FilterConfig {
    private static final String CONFIG_FILE = "config/easyautocycler-filters.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public static class Config {
        public List<FilterData> filters = new ArrayList<>();
        public boolean matchAny = true;
        public List<FilterData> otherVillagerFilters = new ArrayList<>();
        public boolean otherVillagerMatchAny = true;
        public Map<String, List<FilterData>> professionFilters = new HashMap<>();
        public Map<String, Boolean> professionMatchAny = new HashMap<>();
    }
    
    public static class FilterData {
        public String mode;
        public String tradeScope;
        // Retained for backward compatibility with configurations from 1.2.1 and older.
        public boolean enabled = true;
        public String itemId;
        public int minCount = 1;
        public String enchantmentId;
        public int enchantmentLevel = 1;
        public String paymentItemId;
        public int minPrice = 1;
        public int maxPrice = 64;
        public List<EnchantmentTargetData> itemEnchantments = new ArrayList<>();
    }

    public static class EnchantmentTargetData {
        public String enchantmentId;
        public int level = 1;
    }
    
    /**
     * Save filters to configuration file
     */
    public static void saveFilters(List<FilterEntry> filters, boolean matchAny,
                                   List<FilterEntry> otherVillagerFilters,
                                   boolean otherVillagerMatchAny,
                                   Map<String, List<FilterEntry>> professionFilters,
                                   Map<String, Boolean> professionMatchAny) {
        try {
            Config config = new Config();
            config.matchAny = matchAny;
            config.otherVillagerMatchAny = otherVillagerMatchAny;
            config.filters = filtersToData(filters);
            config.otherVillagerFilters = filtersToData(otherVillagerFilters);
            professionFilters.forEach((profession, entries) ->
                    config.professionFilters.put(profession, filtersToData(entries)));
            config.professionMatchAny.putAll(professionMatchAny);
            
            File configFile = new File(CONFIG_FILE);
            configFile.getParentFile().mkdirs(); // Ensure config directory exists
            
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(config, writer);
            }
            
            EasyAutoCyclerMod.LOGGER.info("Saved {} librarian and {} other-villager filters",
                    filters.size(), otherVillagerFilters.size());
            
        } catch (IOException e) {
            EasyAutoCyclerMod.LOGGER.error("Failed to save filter configuration", e);
        }
    }

    private static List<FilterData> filtersToData(List<FilterEntry> filters) {
        List<FilterData> result = new ArrayList<>();
        for (FilterEntry filter : filters) {
            FilterData data = new FilterData();
            data.mode = filter.getMode().name();
            data.tradeScope = filter.getTradeScope().name();
            data.enabled = filter.isEnabled();
            data.itemId = filter.getItemId() != null ? filter.getItemId().toString() : null;
            data.minCount = filter.getMinCount();
            data.enchantmentId = filter.getEnchantmentId() != null ? filter.getEnchantmentId().toString() : null;
            data.enchantmentLevel = filter.getEnchantmentLevel();
            data.paymentItemId = filter.getPaymentItemId() != null ? filter.getPaymentItemId().toString() : null;
            data.minPrice = filter.getMinPrice();
            data.maxPrice = filter.getMaxPrice();
            for (FilterEntry.EnchantmentTarget target : filter.getItemEnchantments()) {
                EnchantmentTargetData targetData = new EnchantmentTargetData();
                targetData.enchantmentId = target.id().toString();
                targetData.level = target.level();
                data.itemEnchantments.add(targetData);
            }
            result.add(data);
        }
        return result;
    }
    
    /**
     * Load filters from configuration file
     */
    public static Config loadFilters() {
        File configFile = new File(CONFIG_FILE);
        
        if (!configFile.exists()) {
            EasyAutoCyclerMod.LOGGER.info("No filter configuration file found, starting with empty filters");
            return new Config(); // Return empty config
        }
        
        try (FileReader reader = new FileReader(configFile)) {
            Config config = GSON.fromJson(reader, Config.class);
            if (config == null) {
                EasyAutoCyclerMod.LOGGER.warn("Configuration file was empty or invalid, starting with empty filters");
                return new Config();
            }
            if (config.filters == null) config.filters = new ArrayList<>();
            if (config.otherVillagerFilters == null) config.otherVillagerFilters = new ArrayList<>();
            if (config.professionFilters == null) config.professionFilters = new HashMap<>();
            if (config.professionMatchAny == null) config.professionMatchAny = new HashMap<>();
            EasyAutoCyclerMod.LOGGER.info("Loaded {} filters from configuration file", config.filters.size());
            return config;
            
        } catch (IOException e) {
            EasyAutoCyclerMod.LOGGER.error("Failed to load filter configuration", e);
            return new Config();
        }
    }
    
    /**
     * Convert FilterData back to FilterEntry
     */
    public static List<FilterEntry> dataToFilters(List<FilterData> filterData) {
        List<FilterEntry> filters = new ArrayList<>();
        
        for (FilterData data : filterData) {
            FilterEntry filter = new FilterEntry();
            if (data.mode == null || data.mode.isBlank()) {
                filter.setEnabled(data.enabled);
            } else {
                try {
                    filter.setMode(FilterEntry.Mode.valueOf(data.mode.trim().toUpperCase(java.util.Locale.ROOT)));
                } catch (IllegalArgumentException exception) {
                    EasyAutoCyclerMod.LOGGER.warn("Invalid filter mode in config: {}; using legacy enabled value", data.mode);
                    filter.setEnabled(data.enabled);
                }
            }
            if (data.tradeScope != null && !data.tradeScope.isBlank()) {
                try {
                    filter.setTradeScope(FilterEntry.TradeScope.valueOf(
                            data.tradeScope.trim().toUpperCase(java.util.Locale.ROOT)));
                } catch (IllegalArgumentException exception) {
                    EasyAutoCyclerMod.LOGGER.warn(
                            "Invalid filter trade scope in config: {}; using INITIAL", data.tradeScope);
                }
            }
            filter.setMinCount(data.minCount);
            filter.setEnchantmentLevel(data.enchantmentLevel);
            filter.setMinPrice(Math.max(1, data.minPrice));
            filter.setMaxPrice(data.maxPrice);
            
            if (data.itemId != null && !data.itemId.isEmpty()) {
                try {
                    filter.setItemId(Identifier.parse(data.itemId));
                } catch (Exception e) {
                    EasyAutoCyclerMod.LOGGER.warn("Invalid item ID in config: {}", data.itemId);
                }
            }
            
            if (data.enchantmentId != null && !data.enchantmentId.isEmpty()) {
                try {
                    filter.setEnchantmentId(Identifier.parse(data.enchantmentId));
                } catch (Exception e) {
                    EasyAutoCyclerMod.LOGGER.warn("Invalid enchantment ID in config: {}", data.enchantmentId);
                }
            }

            List<FilterEntry.EnchantmentTarget> itemEnchantments = new ArrayList<>();
            if (data.itemEnchantments != null) {
                for (EnchantmentTargetData targetData : data.itemEnchantments) {
                    if (targetData == null || targetData.enchantmentId == null) continue;
                    try {
                        itemEnchantments.add(new FilterEntry.EnchantmentTarget(
                                Identifier.parse(targetData.enchantmentId), Math.max(1, targetData.level)));
                    } catch (Exception e) {
                        EasyAutoCyclerMod.LOGGER.warn("Invalid item enchantment ID in config: {}",
                                targetData.enchantmentId);
                    }
                }
            }
            // 1.6.0 stored an item's optional enchantment in the librarian fields.
            if (filter.getItemId() != null && itemEnchantments.isEmpty()
                    && filter.getEnchantmentId() != null) {
                itemEnchantments.add(new FilterEntry.EnchantmentTarget(
                        filter.getEnchantmentId(), filter.getEnchantmentLevel()));
            }
            if (filter.getItemId() != null) {
                filter.setItemEnchantments(itemEnchantments);
                filter.setEnchantmentId(null);
                filter.setEnchantmentLevel(1);
            }
            
            if (data.paymentItemId != null && !data.paymentItemId.isEmpty()) {
                try {
                    filter.setPaymentItemId(Identifier.parse(data.paymentItemId));
                } catch (Exception e) {
                    EasyAutoCyclerMod.LOGGER.warn("Invalid payment item ID in config: {}", data.paymentItemId);
                }
            }
            
            filters.add(filter);
        }
        
        return filters;
    }
}
