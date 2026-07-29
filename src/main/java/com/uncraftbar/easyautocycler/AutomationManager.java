package com.uncraftbar.easyautocycler;

import com.uncraftbar.easyautocycler.config.FilterConfig;
import com.uncraftbar.easyautocycler.filter.FilterEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class AutomationManager {

    public static final AutomationManager INSTANCE = new AutomationManager();

    static {
        INSTANCE.loadFiltersFromConfig();
    }

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private boolean waitingForOfferUpdate = false;
    private int waitingForOfferTicks = 0;
    private int currentCycles = 0;
    private static final int MAX_CYCLES_SAFETY = 100_000;
    private static final int OFFER_UPDATE_TIMEOUT_TICKS = 100;

    private static boolean initialized = false;
    private static boolean tradeCyclingLoaded = false;
    private static Object tradeCyclingHandler = null;

    private static class TradeCyclingHandler {
        private final Method canCycleMethod;
        private final Constructor<?> packetConstructor;

        public TradeCyclingHandler() throws Exception {
            Class<?> buttonClass = Class.forName("de.maxhenkel.tradecycling.gui.CycleTradesButton");
            canCycleMethod = buttonClass.getMethod("canCycle", MerchantMenu.class);
            Class<?> packetClass = Class.forName("de.maxhenkel.tradecycling.net.CycleTradesPacket");
            packetConstructor = packetClass.getDeclaredConstructor();
        }

        public boolean canCycle(MerchantMenu menu) {
            try {
                return (boolean) canCycleMethod.invoke(null, menu);
            } catch (Exception e) {
                EasyAutoCyclerMod.LOGGER.error("Error calling Trade Cycling canCycle", e);
                return false;
            }
        }

        public boolean sendCyclePacket() {
            try {
                Object packet = packetConstructor.newInstance();
                if (Minecraft.getInstance().getConnection() != null) {
                    Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket((CustomPacketPayload) packet));
                }
                EasyAutoCyclerMod.LOGGER.trace("Sent Trade Cycling cycle packet");
                return true;
            } catch (Exception e) {
                EasyAutoCyclerMod.LOGGER.error("Failed to send Trade Cycling packet", e);
                return false;
            }
        }
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        tradeCyclingLoaded = FabricLoader.getInstance().isModLoaded("trade_cycling");

        EasyAutoCyclerMod.LOGGER.info("Trade Cycling mod is {}", tradeCyclingLoaded ? "loaded" : "not loaded");

        if (tradeCyclingLoaded) {
            try {
                tradeCyclingHandler = new TradeCyclingHandler();
                EasyAutoCyclerMod.LOGGER.info("Trade Cycling support enabled");
            } catch (Exception e) {
                tradeCyclingLoaded = false;
                EasyAutoCyclerMod.LOGGER.error("Failed to initialize Trade Cycling support: {}", e.getMessage());
            }
        }

        if (!tradeCyclingLoaded) {
            EasyAutoCyclerMod.LOGGER.warn("Trade Cycling mod not detected! This mod requires Trade Cycling to function.");
        }
    }

    public static final int MODE_ENCHANTMENT = 0;
    public static final int MODE_ITEM = 1;
    private int cycleMode = MODE_ENCHANTMENT;

    @Nullable private Identifier targetEnchantmentId = null;
    @Nullable private Identifier targetItemId = null;
    private int maxEmeraldCost = 64;
    private int targetLevel = 1;
    private int targetItemCount = 1;

    private List<FilterEntry> filterEntries = new ArrayList<>();
    private boolean matchAny = true;
    private FilterEntry lastMatchedFilter = null;

    private AutomationManager() {}

    public boolean isRunning() { return isRunning.get(); }
    @Nullable public Identifier getTargetEnchantmentId() { return targetEnchantmentId; }
    @Nullable public Identifier getTargetItemId() { return targetItemId; }
    public int getMaxEmeraldCost() { return maxEmeraldCost; }
    public int getTargetLevel() { return targetLevel; }
    public int getCycleMode() { return cycleMode; }
    public int getTargetItemCount() { return targetItemCount; }

    public void configureTarget(Identifier enchantmentId, int level, int emeraldCost) {
        this.targetEnchantmentId = enchantmentId;
        this.targetLevel = level;
        this.maxEmeraldCost = emeraldCost;
        this.cycleMode = MODE_ENCHANTMENT;

        FilterEntry entry = new FilterEntry();
        entry.setEnchantmentId(enchantmentId);
        entry.setEnchantmentLevel(level);
        entry.setMaxPrice(emeraldCost);

        this.filterEntries.clear();
        this.filterEntries.add(entry);
    }

    public void configureTargetItem(Identifier itemId, int itemCount, int emeraldCost) {
        this.targetItemId = itemId;
        this.targetItemCount = itemCount;
        this.maxEmeraldCost = emeraldCost;
        this.cycleMode = MODE_ITEM;

        FilterEntry entry = new FilterEntry();
        entry.setItemId(itemId);
        entry.setMinCount(itemCount);
        entry.setMaxPrice(emeraldCost);

        this.filterEntries.clear();
        this.filterEntries.add(entry);
    }

    public List<FilterEntry> getFilterEntries() {
        migrateOldConfigToFilters();
        return new ArrayList<>(filterEntries);
    }

    public void setFilterEntries(List<FilterEntry> entries) {
        if (entries == null) {
            this.filterEntries = new ArrayList<>();
        } else {
            this.filterEntries = new ArrayList<>(entries);
        }
        this.targetEnchantmentId = null;
        this.targetItemId = null;

        saveFiltersToConfig();
    }

    public boolean isMatchAny() { return matchAny; }

    public void setMatchAny(boolean matchAny) { this.matchAny = matchAny; }

    @Nullable
    public FilterEntry getLastMatchedFilter() { return lastMatchedFilter; }

    private void migrateOldConfigToFilters() {
        if (filterEntries.isEmpty() && (targetEnchantmentId != null || targetItemId != null)) {
            FilterEntry entry = new FilterEntry();

            if (targetEnchantmentId != null) {
                entry.setEnchantmentId(targetEnchantmentId);
                entry.setEnchantmentLevel(targetLevel);
            }

            if (targetItemId != null) {
                entry.setItemId(targetItemId);
                entry.setMinCount(targetItemCount);
            }

            entry.setMaxPrice(maxEmeraldCost);
            filterEntries.add(entry);
        }
    }

    public void clearTarget() {
        this.targetEnchantmentId = null;
        this.targetItemId = null;
        this.filterEntries.clear();
        this.lastMatchedFilter = null;
        this.sendMessageToPlayer(Component.literal("Cleared target trade. Automation will not stop automatically."));
    }

    public void toggle() {
        if (isRunning.get()) {
            stop("Toggled off by user");
        } else {
            start();
        }
    }

    private void start() {
        Screen currentScreen = Minecraft.getInstance().screen;

        if (!(currentScreen instanceof MerchantScreen)) {
            this.sendMessageToPlayer(Component.literal("Error: Villager trade screen not open."));
            return;
        }

        if (!initialized || !tradeCyclingLoaded) {
            this.sendMessageToPlayer(Component.literal("Error: Trade Cycling mod not detected.").withStyle(ChatFormatting.RED));
            return;
        }

        migrateOldConfigToFilters();
        if (filterEntries.isEmpty()) {
            this.sendMessageToPlayer(Component.translatable("chat.easyautocycler.error.noconfig").withStyle(ChatFormatting.RED));
            return;
        }

        MerchantScreen merchantScreen = (MerchantScreen) currentScreen;
        if (!canCycleTrades(merchantScreen.getMenu())) {
            this.sendMessageToPlayer(Component.translatable("chat.easyautocycler.error.locked").withStyle(ChatFormatting.RED));
            return;
        }

        if (isRunning.compareAndSet(false, true)) {
            EasyAutoCyclerMod.LOGGER.debug("Starting network-synchronized villager trade cycling.");
            this.sendMessageToPlayer(Component.literal("Auto-cycling started. Press button again to stop."));
            this.waitingForOfferUpdate = false;
            this.waitingForOfferTicks = 0;
            this.currentCycles = 0;
            this.lastMatchedFilter = null;
            evaluateAndMaybeCycle(merchantScreen);
        }
    }

    public void stop(String reason) {
        if (isRunning.compareAndSet(true, false)) {
            waitingForOfferUpdate = false;
            waitingForOfferTicks = 0;
            EasyAutoCyclerMod.LOGGER.debug("Stopping villager trade cycling. Reason: {}", reason);
        }
    }

    public void clientTick() {
        if (!isRunning.get()) return;

        if (!(Minecraft.getInstance().screen instanceof MerchantScreen screen)) {
            stop("Screen closed");
            return;
        }

        if (waitingForOfferUpdate) {
            waitingForOfferTicks++;
            if (waitingForOfferTicks >= OFFER_UPDATE_TIMEOUT_TICKS) {
                this.sendMessageToPlayer(Component.literal("§cTimed out waiting for updated villager trades."));
                EasyAutoCyclerMod.LOGGER.warn("No merchant-offers acknowledgement received after {} ticks", OFFER_UPDATE_TIMEOUT_TICKS);
                stop("Merchant offers update timed out");
            }
            return;
        }

        evaluateAndMaybeCycle(screen);
    }

    public void onMerchantOffersUpdated(int containerId) {
        if (!isRunning.get() || !waitingForOfferUpdate) return;

        if (!(Minecraft.getInstance().screen instanceof MerchantScreen screen)) {
            stop("Screen closed");
            return;
        }
        if (screen.getMenu().containerId != containerId) return;

        waitingForOfferUpdate = false;
        waitingForOfferTicks = 0;
        evaluateAndMaybeCycle(screen);
    }

    private void evaluateAndMaybeCycle(MerchantScreen screen) {
        if (!isRunning.get() || waitingForOfferUpdate) return;

        MerchantOffers offers = screen.getMenu().getOffers();

        List<FilterEntry> enabledFilters = filterEntries.stream()
                .filter(FilterEntry::isEnabled)
                .collect(Collectors.toList());

        if (!enabledFilters.isEmpty() && checkTradesWithFilters(offers)) {
            Component message;
            if (matchAny) {
                message = Component.translatable(
                        "chat.easyautocycler.found_with_count",
                        currentCycles,
                        this.lastMatchedFilter.getDisplayName());
            } else {
                message = Component.translatable(
                        "chat.easyautocycler.found_all_with_count",
                        currentCycles,
                        enabledFilters.size());
            }
            this.sendMessageToPlayer(message);
            playSuccessSound();
            stop("Target trade found with filter");
            return;
        } else if (enabledFilters.isEmpty()) {
            if (cycleMode == MODE_ENCHANTMENT && targetEnchantmentId != null && checkTradesForEnchantment(offers)) {
                this.sendMessageToPlayer(Component.translatable(
                        "chat.easyautocycler.found_legacy_with_count", currentCycles));
                playSuccessSound();
                stop("Target trade found");
                return;
            } else if (cycleMode == MODE_ITEM && targetItemId != null && checkTradesForItem(offers)) {
                this.sendMessageToPlayer(Component.translatable(
                        "chat.easyautocycler.item_found_with_count", currentCycles));
                playSuccessSound();
                stop("Target item trade found");
                return;
            }
        }

        if (canCycleTrades(screen.getMenu())) {
            if (currentCycles >= MAX_CYCLES_SAFETY) {
                this.sendMessageToPlayer(Component.translatable(
                        "chat.easyautocycler.max_cycles_reached", MAX_CYCLES_SAFETY));
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.getSoundManager() != null) {
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 1.0F));
                    }
                } catch (Exception e) {
                    EasyAutoCyclerMod.LOGGER.error("Failed to play safety-limit sound effect", e);
                }
                stop("Max cycles safety limit reached!");
                return;
            }

            waitingForOfferUpdate = true;
            waitingForOfferTicks = 0;
            currentCycles++;
            if (!sendCyclePacket()) {
                waitingForOfferUpdate = false;
                stop("Network error");
            }
        }
    }

    public boolean canCycleTrades(MerchantMenu menu) {
        if (!initialized) return false;

        if (tradeCyclingLoaded && tradeCyclingHandler != null) {
            return ((TradeCyclingHandler) tradeCyclingHandler).canCycle(menu);
        }
        return false;
    }

    private boolean sendCyclePacket() {
        if (tradeCyclingLoaded && tradeCyclingHandler != null) {
            return ((TradeCyclingHandler) tradeCyclingHandler).sendCyclePacket();
        }
        return false;
    }

    private boolean checkTradesForEnchantment(MerchantOffers offers) {
        if (targetEnchantmentId == null) return false;
        for (MerchantOffer offer : offers) {
            if (offer.isOutOfStock()) continue;

            ItemStack costA = offer.getBaseCostA();
            ItemStack costB = offer.getCostB();
            if (!((costA.is(Items.EMERALD) && costA.getCount() <= this.maxEmeraldCost)
                    || (costB.is(Items.EMERALD) && costB.getCount() <= this.maxEmeraldCost))) {
                continue;
            }

            if (matchesEnchantmentOnStack(offer.getResult(), targetEnchantmentId, targetLevel, true)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkTradesForItem(MerchantOffers offers) {
        if (targetItemId == null) return false;
        for (MerchantOffer offer : offers) {
            ItemStack resultStack = offer.getResult();
            Identifier itemIdInStack = BuiltInRegistries.ITEM.getKey(resultStack.getItem());
            if (!itemIdInStack.equals(targetItemId)) continue;

            if (offer.isOutOfStock()) continue;

            ItemStack costA = offer.getBaseCostA();
            ItemStack costB = offer.getCostB();
            if (!((costA.is(Items.EMERALD) && costA.getCount() <= this.maxEmeraldCost)
                    || (costB.is(Items.EMERALD) && costB.getCount() <= this.maxEmeraldCost))) {
                continue;
            }

            if (resultStack.getCount() >= this.targetItemCount) return true;
        }
        return false;
    }

    private void sendMessageToPlayer(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.sendSystemMessage(message);
        }
    }

    private void playSuccessSound() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.0F));
            }
        } catch (Exception e) {
            EasyAutoCyclerMod.LOGGER.error("Failed to play 'trade found' sound effect", e);
        }
    }

    private boolean checkTradesWithFilters(MerchantOffers offers) {
        if (filterEntries.isEmpty()) return false;

        List<FilterEntry> enabledFilters = filterEntries.stream()
                .filter(FilterEntry::isEnabled)
                .collect(Collectors.toList());

        if (enabledFilters.isEmpty()) return false;

        this.lastMatchedFilter = null;

        if (matchAny) {
            for (FilterEntry filter : enabledFilters) {
                if (checkTradeWithFilter(offers, filter)) {
                    this.lastMatchedFilter = filter;
                    return true;
                }
            }
            return false;
        } else {
            for (FilterEntry filter : enabledFilters) {
                if (!checkTradeWithFilter(offers, filter)) {
                    return false;
                }
            }
            this.lastMatchedFilter = enabledFilters.get(0);
            return true;
        }
    }

    private boolean checkTradeWithFilter(MerchantOffers offers, FilterEntry filter) {
        for (MerchantOffer offer : offers) {
            if (offer.isOutOfStock()) continue;

            ItemStack resultStack = offer.getResult();
            // Cost A is the only cost affected by demand, reputation and special-price
            // adjustments. The base cost intentionally ignores discounts and increases.
            ItemStack costA = offer.getBaseCostA();
            ItemStack costB = offer.getCostB();

            boolean priceMatches;
            if (filter.getPaymentItemId() == null) {
                priceMatches = (costA.is(Items.EMERALD)
                        && costA.getCount() >= filter.getMinPrice()
                        && costA.getCount() <= filter.getMaxPrice())
                        || (costB.is(Items.EMERALD)
                        && costB.getCount() >= filter.getMinPrice()
                        && costB.getCount() <= filter.getMaxPrice());
            } else {
                Item paymentItem = BuiltInRegistries.ITEM.getOptional(filter.getPaymentItemId()).orElse(null);
                priceMatches = paymentItem != null && (
                        (costA.is(paymentItem) && costA.getCount() >= filter.getMinPrice()
                                && costA.getCount() <= filter.getMaxPrice())
                                || (costB.is(paymentItem) && costB.getCount() >= filter.getMinPrice()
                                && costB.getCount() <= filter.getMaxPrice()));
            }

            if (!priceMatches) continue;

            if (filter.getItemId() != null) {
                Identifier itemIdInStack = BuiltInRegistries.ITEM.getKey(resultStack.getItem());
                if (!itemIdInStack.equals(filter.getItemId())) continue;
                if (resultStack.getCount() < filter.getMinCount()) continue;
            }

            if (filter.getEnchantmentId() != null) {
                if (!resultStack.is(Items.ENCHANTED_BOOK)
                        || !matchesEnchantmentOnStack(resultStack, filter.getEnchantmentId(), filter.getEnchantmentLevel(), true)) {
                    continue;
                }
            }

            return true;
        }
        return false;
    }

    private boolean matchesEnchantmentOnStack(ItemStack stack, Identifier targetId, int requiredLevel, boolean exactLevel) {
        if (stack.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
            if (stored != null && !stored.isEmpty() && enchantmentsMatch(stored, targetId, requiredLevel, exactLevel)) {
                return true;
            }
        }
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) {
            return enchantmentsMatch(enchantments, targetId, requiredLevel, exactLevel);
        }
        return false;
    }

    private boolean enchantmentsMatch(ItemEnchantments enchantments, Identifier targetId, int requiredLevel, boolean exactLevel) {
        for (Holder<Enchantment> enchHolder : enchantments.keySet()) {
            Identifier holderId = enchHolder.unwrapKey().map(k -> k.identifier()).orElse(null);
            if (holderId == null || !holderId.equals(targetId)) continue;
            int level = enchantments.getLevel(enchHolder);
            if (exactLevel ? (level == requiredLevel) : (level >= requiredLevel)) return true;
        }
        return false;
    }

    public void loadFiltersFromConfig() {
        FilterConfig.Config config = FilterConfig.loadFilters();
        this.filterEntries = FilterConfig.dataToFilters(config.filters);
        this.matchAny = config.matchAny;
    }

    public void saveFiltersToConfig() {
        FilterConfig.saveFilters(this.filterEntries, this.matchAny);
    }
}
