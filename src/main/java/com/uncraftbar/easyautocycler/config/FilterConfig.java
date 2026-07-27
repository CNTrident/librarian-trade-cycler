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
import java.util.List;

/**
 * Handles saving and loading filter configurations to/from JSON files
 */
public class FilterConfig {
    private static final String CONFIG_FILE = "config/easyautocycler-filters.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public static class Config {
        public List<FilterData> filters = new ArrayList<>();
        public boolean matchAny = true;
    }
    
    public static class FilterData {
        public boolean enabled = true;
        public String itemId;
        public int minCount = 1;
        public String enchantmentId;
        public int enchantmentLevel = 1;
        public String paymentItemId;
        public int minPrice = 1;
        public int maxPrice = 64;
    }
    
    /**
     * Save filters to configuration file
     */
    public static void saveFilters(List<FilterEntry> filters, boolean matchAny) {
        try {
            Config config = new Config();
            config.matchAny = matchAny;
            
            for (FilterEntry filter : filters) {
                FilterData data = new FilterData();
                data.enabled = filter.isEnabled();
                data.itemId = filter.getItemId() != null ? filter.getItemId().toString() : null;
                data.minCount = filter.getMinCount();
                data.enchantmentId = filter.getEnchantmentId() != null ? filter.getEnchantmentId().toString() : null;
                data.enchantmentLevel = filter.getEnchantmentLevel();
                data.paymentItemId = filter.getPaymentItemId() != null ? filter.getPaymentItemId().toString() : null;
                data.minPrice = filter.getMinPrice();
                data.maxPrice = filter.getMaxPrice();
                config.filters.add(data);
            }
            
            File configFile = new File(CONFIG_FILE);
            configFile.getParentFile().mkdirs(); // Ensure config directory exists
            
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(config, writer);
            }
            
            EasyAutoCyclerMod.LOGGER.info("Saved {} filters to configuration file", filters.size());
            
        } catch (IOException e) {
            EasyAutoCyclerMod.LOGGER.error("Failed to save filter configuration", e);
        }
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
            filter.setEnabled(data.enabled);
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
