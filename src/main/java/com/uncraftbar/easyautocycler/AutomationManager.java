package com.uncraftbar.easyautocycler;

import com.uncraftbar.easyautocycler.config.FilterConfig;
import com.uncraftbar.easyautocycler.compat.VisibleTradersCompat;
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
import net.minecraft.network.chat.contents.TranslatableContents;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class AutomationManager {

    public static final AutomationManager INSTANCE = new AutomationManager();

    static {
        INSTANCE.loadFiltersFromConfig();
    }

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private boolean waitingForOfferUpdate = false;
    private boolean waitingForVisibleTradersOffers = false;
    private boolean offerUpdateReadyForEvaluation = false;
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
                if (Minecraft.getInstance().getConnection() == null) return false;
                Minecraft.getInstance().getConnection().send(
                        new ServerboundCustomPayloadPacket((CustomPacketPayload) packet));
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
        VisibleTradersCompat.initialize();

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
    private List<FilterEntry> otherVillagerFilterEntries = new ArrayList<>();
    private final Map<String, List<FilterEntry>> professionFilterEntries = new HashMap<>();
    private boolean matchAny = true;
    private boolean otherVillagerMatchAny = true;
    private final Map<String, Boolean> professionMatchAny = new HashMap<>();
    private String runningProfessionKey = "minecraft:librarian";
    private FilterEntry lastMatchedFilter = null;
    private final List<FilterEntry> lastMatchedFilters = new ArrayList<>();

    private AutomationManager() {}

    public boolean isRunning() { return isRunning.get(); }

    public boolean isLibrarianTradeScreen(Screen screen) {
        Identifier professionId = getVillagerProfessionId(screen);
        return professionId != null && "librarian".equals(professionId.getPath());
    }

    public boolean isSupportedVillagerTradeScreen(Screen screen) {
        Identifier professionId = getVillagerProfessionId(screen);
        return professionId != null && !"none".equals(professionId.getPath())
                && !"nitwit".equals(professionId.getPath());
    }

    @Nullable
    public Identifier getVillagerProfessionId(Screen screen) {
        if (!(screen instanceof MerchantScreen merchantScreen)
                || !(merchantScreen.getTitle().getContents() instanceof TranslatableContents title)) return null;
        String key = title.getKey();
        String[] parts = key.split("\\.");
        if (parts.length < 4 || !"entity".equals(parts[0]) || !"villager".equals(parts[2])) return null;
        return Identifier.tryParse(parts[1] + ":" + parts[3]);
    }
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
        return filterEntries.stream().map(FilterEntry::new).collect(Collectors.toCollection(ArrayList::new));
    }

    public List<FilterEntry> getFilterEntries(Identifier professionId) {
        if (isLibrarianProfession(professionId)) return getFilterEntries();
        List<FilterEntry> entries = ensureProfessionProfile(professionId);
        return entries.stream().map(FilterEntry::new)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public void setFilterEntries(List<FilterEntry> entries) {
        setFilterEntries(Identifier.withDefaultNamespace("librarian"), entries);
    }

    public void setFilterEntries(Identifier professionId, List<FilterEntry> entries) {
        List<FilterEntry> copied = entries == null ? new ArrayList<>() : entries.stream()
                .map(FilterEntry::new).collect(Collectors.toCollection(ArrayList::new));
        if (!isLibrarianProfession(professionId)) {
            this.professionFilterEntries.put(professionId.toString(), copied);
            saveFiltersToConfig();
            return;
        }
        if (entries == null) {
            this.filterEntries = new ArrayList<>();
        } else {
            this.filterEntries = copied;
        }
        this.targetEnchantmentId = null;
        this.targetItemId = null;

        saveFiltersToConfig();
    }

    public boolean isMatchAny() { return matchAny; }

    public boolean isMatchAny(Identifier professionId) {
        if (isLibrarianProfession(professionId)) return matchAny;
        ensureProfessionProfile(professionId);
        return professionMatchAny.getOrDefault(professionId.toString(), otherVillagerMatchAny);
    }

    public void setMatchAny(boolean matchAny) { this.matchAny = matchAny; }

    public void setMatchAny(Identifier professionId, boolean value) {
        if (isLibrarianProfession(professionId)) this.matchAny = value;
        else this.professionMatchAny.put(professionId.toString(), value);
    }

    private static boolean isLibrarianProfession(@Nullable Identifier professionId) {
        return professionId != null && "librarian".equals(professionId.getPath());
    }

    private List<FilterEntry> ensureProfessionProfile(Identifier professionId) {
        String key = professionId.toString();
        return professionFilterEntries.computeIfAbsent(key, ignored -> otherVillagerFilterEntries.stream()
                .map(FilterEntry::new).collect(Collectors.toCollection(ArrayList::new)));
    }

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
        this.sendMessageToPlayer(Component.translatable("chat.easyautocycler.target_cleared"));
    }

    public void toggle() {
        if (isRunning.get()) {
            if (stopInternal("Toggled off by user")) {
                this.sendMessageToPlayer(Component.translatable(
                        "chat.easyautocycler.stopped_with_count", currentCycles));
            }
        } else {
            start();
        }
    }

    private void start() {
        Screen currentScreen = Minecraft.getInstance().gui.screen();

        if (!(currentScreen instanceof MerchantScreen)) {
            this.sendMessageToPlayer(Component.translatable("chat.easyautocycler.error.noscreen")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        if (!isSupportedVillagerTradeScreen(currentScreen)) {
            this.sendMessageToPlayer(Component.translatable(
                    "chat.easyautocycler.error.unsupported_merchant").withStyle(ChatFormatting.RED));
            return;
        }

        if (!initialized || !tradeCyclingLoaded) {
            this.sendMessageToPlayer(Component.translatable("chat.easyautocycler.error.trade_cycling_missing")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        Identifier currentProfession = getVillagerProfessionId(currentScreen);
        if (currentProfession == null) return;
        runningProfessionKey = currentProfession.toString();
        if (isLibrarianProfession(currentProfession)) migrateOldConfigToFilters();
        else ensureProfessionProfile(currentProfession);
        if (activeFilterEntries().isEmpty()) {
            this.sendMessageToPlayer(Component.translatable("chat.easyautocycler.error.noconfig").withStyle(ChatFormatting.RED));
            return;
        }
        if (activeFilterEntries().stream().noneMatch(FilterEntry::isEnabled)) {
            this.sendMessageToPlayer(Component.translatable(
                    "chat.easyautocycler.error.no_enabled_targets").withStyle(ChatFormatting.RED));
            return;
        }
        if (requiresFutureOffers() && !VisibleTradersCompat.isAvailable()) {
            this.sendMessageToPlayer(Component.translatable(
                    "chat.easyautocycler.error.visibletraders_required").withStyle(ChatFormatting.RED));
            return;
        }

        MerchantScreen merchantScreen = (MerchantScreen) currentScreen;
        if (!canCycleTrades(merchantScreen.getMenu())) {
            this.sendMessageToPlayer(Component.translatable("chat.easyautocycler.error.locked").withStyle(ChatFormatting.RED));
            return;
        }

        if (isRunning.compareAndSet(false, true)) {
            EasyAutoCyclerMod.LOGGER.debug("Starting network-synchronized villager trade cycling.");
            this.sendMessageToPlayer(Component.translatable("chat.easyautocycler.started"));
            this.waitingForOfferUpdate = false;
            this.waitingForVisibleTradersOffers = false;
            this.offerUpdateReadyForEvaluation = false;
            this.waitingForOfferTicks = 0;
            this.currentCycles = 0;
            this.lastMatchedFilter = null;
            this.lastMatchedFilters.clear();
            evaluateAndMaybeCycle(merchantScreen);
        }
    }

    public void stop(String reason) {
        stopInternal(reason);
    }

    private boolean stopInternal(String reason) {
        if (isRunning.compareAndSet(true, false)) {
            waitingForOfferUpdate = false;
            waitingForVisibleTradersOffers = false;
            offerUpdateReadyForEvaluation = false;
            waitingForOfferTicks = 0;
            EasyAutoCyclerMod.LOGGER.debug("Stopping villager trade cycling. Reason: {}", reason);
            return true;
        }
        return false;
    }

    public void clientTick() {
        if (!isRunning.get()) return;

        if (!(Minecraft.getInstance().gui.screen() instanceof MerchantScreen screen)) {
            stop("Screen closed");
            return;
        }

        if (waitingForOfferUpdate) {
            if (waitingForVisibleTradersOffers) {
                VisibleTradersCompat.OfferSets offerSets =
                        VisibleTradersCompat.captureOffers(screen.getMenu());
                if (offerSets.lockedOffersReady()) {
                    finishOfferUpdateWait();
                    return;
                }
            }
            waitingForOfferTicks++;
            if (waitingForOfferTicks >= OFFER_UPDATE_TIMEOUT_TICKS) {
                this.sendMessageToPlayer(Component.translatable(waitingForVisibleTradersOffers
                        ? "chat.easyautocycler.error.visibletraders_timeout"
                        : "chat.easyautocycler.error.offer_timeout"));
                EasyAutoCyclerMod.LOGGER.warn("No merchant-offers acknowledgement received after {} ticks", OFFER_UPDATE_TIMEOUT_TICKS);
                stop("Merchant offers update timed out");
            }
            return;
        }

        if (!offerUpdateReadyForEvaluation) return;
        offerUpdateReadyForEvaluation = false;
        evaluateAndMaybeCycle(screen);
    }

    public void onMerchantOffersUpdated(int containerId) {
        if (!isRunning.get() || !waitingForOfferUpdate) return;

        if (!(Minecraft.getInstance().gui.screen() instanceof MerchantScreen screen)) {
            stop("Screen closed");
            return;
        }
        if (screen.getMenu().containerId != containerId) return;

        waitingForOfferTicks = 0;
        // Do not send the next cycle from inside the packet handler. The next client
        // tick evaluates the offers that this packet has just applied, and only an
        // unsuccessful evaluation is allowed to issue another refresh request.
        if (requiresFutureOffers() && VisibleTradersCompat.isAvailable()) {
            waitingForVisibleTradersOffers = true;
            return;
        }
        finishOfferUpdateWait();
    }

    private void finishOfferUpdateWait() {
        waitingForOfferUpdate = false;
        waitingForVisibleTradersOffers = false;
        waitingForOfferTicks = 0;
        offerUpdateReadyForEvaluation = true;
    }

    private void evaluateAndMaybeCycle(MerchantScreen screen) {
        if (!isRunning.get() || waitingForOfferUpdate) return;

        VisibleTradersCompat.OfferSets offerSets = VisibleTradersCompat.captureOffers(screen.getMenu());
        if (requiresFutureOffers() && VisibleTradersCompat.isAvailable()
                && !offerSets.lockedOffersReady()) {
            waitingForOfferUpdate = true;
            waitingForVisibleTradersOffers = true;
            waitingForOfferTicks = 0;
            return;
        }
        MerchantOffers offers = offerSets.initial();

        List<FilterEntry> enabledFilters = activeFilterEntries().stream()
                .filter(FilterEntry::isEnabled)
                .collect(Collectors.toList());

        if (!enabledFilters.isEmpty() && checkTradesWithFilters(offerSets)) {
            int autoDisabledCount = disableMatchedAutoFilters();
            Component message;
            if (activeMatchAny()) {
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
            if (autoDisabledCount > 0) {
                this.sendMessageToPlayer(Component.translatable(
                        "chat.easyautocycler.auto_disabled", autoDisabledCount));
            }
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
            waitingForVisibleTradersOffers = false;
            waitingForOfferTicks = 0;
            currentCycles++;
            if (!sendCyclePacket()) {
                waitingForOfferUpdate = false;
                this.sendMessageToPlayer(Component.translatable("chat.easyautocycler.error.network")
                        .withStyle(ChatFormatting.RED));
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

    private boolean checkTradesWithFilters(VisibleTradersCompat.OfferSets offerSets) {
        if (activeFilterEntries().isEmpty()) return false;

        List<FilterEntry> enabledFilters = activeFilterEntries().stream()
                .filter(FilterEntry::isEnabled)
                .collect(Collectors.toList());

        if (enabledFilters.isEmpty()) return false;

        this.lastMatchedFilter = null;
        this.lastMatchedFilters.clear();

        for (FilterEntry filter : enabledFilters) {
            if (checkTradeWithFilter(offerSets.forScope(filter.getTradeScope()), filter)) {
                this.lastMatchedFilters.add(filter);
            }
        }

        if (lastMatchedFilters.isEmpty()) return false;
        this.lastMatchedFilter = lastMatchedFilters.get(0);
        return activeMatchAny() || lastMatchedFilters.size() == enabledFilters.size();
    }

    private boolean requiresFutureOffers() {
        return activeFilterEntries().stream().anyMatch(filter -> filter.isEnabled()
                && filter.getTradeScope() != FilterEntry.TradeScope.INITIAL);
    }

    private List<FilterEntry> activeFilterEntries() {
        if ("minecraft:librarian".equals(runningProfessionKey)) return filterEntries;
        return professionFilterEntries.getOrDefault(runningProfessionKey, List.of());
    }

    private boolean activeMatchAny() {
        return "minecraft:librarian".equals(runningProfessionKey)
                ? matchAny : professionMatchAny.getOrDefault(runningProfessionKey, otherVillagerMatchAny);
    }

    private int disableMatchedAutoFilters() {
        int disabledCount = 0;
        for (FilterEntry filter : lastMatchedFilters) {
            if (filter.isAutoDisable()) {
                filter.setMode(FilterEntry.Mode.OFF);
                disabledCount++;
            }
        }
        if (disabledCount > 0) saveFiltersToConfig();
        return disabledCount;
    }

    private boolean checkTradeWithFilter(MerchantOffers offers, FilterEntry filter) {
        for (MerchantOffer offer : offers) {
            if (offer.isOutOfStock()) continue;

            ItemStack resultStack = offer.getResult();
            // Cost A is the only cost affected by demand, reputation and special-price
            // adjustments. The base cost intentionally ignores discounts and increases.
            ItemStack costA = offer.getBaseCostA();
            ItemStack costB = offer.getCostB();

            if (filter.getItemId() != null) {
                boolean targetIsResult = matchesTargetItem(resultStack, filter);
                boolean targetIsCost = matchesTargetItem(costA, filter) || matchesTargetItem(costB, filter);
                boolean targetAndPriceMatch = targetIsResult
                        && (matchesPayment(costA, filter) || matchesPayment(costB, filter));
                targetAndPriceMatch |= targetIsCost && matchesPayment(resultStack, filter);
                if (!targetAndPriceMatch) continue;
            } else if (!matchesPayment(costA, filter) && !matchesPayment(costB, filter)) {
                continue;
            }

            if (!filter.getItemEnchantments().isEmpty()) {
                if (!matchesTargetItem(resultStack, filter)
                        || !matchesExactItemEnchantments(resultStack, filter.getItemEnchantments())) {
                    continue;
                }
            } else if (filter.getEnchantmentId() != null) {
                if (!resultStack.is(Items.ENCHANTED_BOOK) || !matchesEnchantmentOnStack(resultStack,
                        filter.getEnchantmentId(), filter.getEnchantmentLevel(), true)) {
                    continue;
                }
            }

            return true;
        }
        return false;
    }

    private boolean matchesTargetItem(ItemStack stack, FilterEntry filter) {
        if (stack.isEmpty() || filter.getItemId() == null || stack.getCount() < filter.getMinCount()) return false;
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(filter.getItemId());
    }

    private boolean matchesPayment(ItemStack stack, FilterEntry filter) {
        if (stack.isEmpty() || stack.getCount() < filter.getMinPrice()
                || stack.getCount() > filter.getMaxPrice()) return false;
        if (filter.getPaymentItemId() == null) return stack.is(Items.EMERALD);
        Item paymentItem = BuiltInRegistries.ITEM.getOptional(filter.getPaymentItemId()).orElse(null);
        return paymentItem != null && stack.is(paymentItem);
    }

    private boolean matchesExactItemEnchantments(ItemStack stack,
                                                 List<FilterEntry.EnchantmentTarget> targets) {
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null || enchantments.size() != targets.size()) return false;
        for (FilterEntry.EnchantmentTarget target : targets) {
            if (!enchantmentsMatch(enchantments, target.id(), target.level(), true)) return false;
        }
        return true;
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
        this.otherVillagerFilterEntries = FilterConfig.dataToFilters(config.otherVillagerFilters);
        this.otherVillagerMatchAny = config.otherVillagerMatchAny;
        this.professionFilterEntries.clear();
        config.professionFilters.forEach((profession, entries) ->
                this.professionFilterEntries.put(profession, FilterConfig.dataToFilters(entries)));
        this.professionMatchAny.clear();
        this.professionMatchAny.putAll(config.professionMatchAny);
    }

    public void saveFiltersToConfig() {
        FilterConfig.saveFilters(this.filterEntries, this.matchAny,
                this.otherVillagerFilterEntries, this.otherVillagerMatchAny,
                this.professionFilterEntries, this.professionMatchAny);
    }
}
