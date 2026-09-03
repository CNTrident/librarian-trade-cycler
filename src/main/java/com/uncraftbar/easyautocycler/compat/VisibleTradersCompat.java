package com.uncraftbar.easyautocycler.compat;

import com.uncraftbar.easyautocycler.EasyAutoCyclerMod;
import com.uncraftbar.easyautocycler.filter.FilterEntry;
import com.uncraftbar.easyautocycler.mixin.MerchantMenuAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;

import java.lang.reflect.Method;
import java.util.Arrays;

/** Optional, reflection-based bridge to Ramixin/VisibleTraders. */
public final class VisibleTradersCompat {
    private static final String MOD_ID = "visibletraders";
    private static final String ENABLE_COMBINED_METHOD = "visibleTraders$enableCombinedOffers";

    private static boolean initialized;
    private static boolean loaded;
    private static Method enableCombinedOffers;

    private VisibleTradersCompat() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        loaded = FabricLoader.getInstance().isModLoaded(MOD_ID);
        if (!loaded) {
            EasyAutoCyclerMod.LOGGER.info("VisibleTraders is not loaded; only initial-trade matching is available");
            return;
        }

        try {
            Class<?> duckClass = Class.forName("net.ramixin.visibletraders.ducks.ClientMerchantMenuDuck");
            enableCombinedOffers = Arrays.stream(duckClass.getMethods())
                    .filter(method -> method.getName().equals(ENABLE_COMBINED_METHOD)
                            && method.getParameterCount() == 0)
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException(ENABLE_COMBINED_METHOD));
            EasyAutoCyclerMod.LOGGER.info("VisibleTraders trade-scope integration enabled");
        } catch (ReflectiveOperationException exception) {
            loaded = false;
            EasyAutoCyclerMod.LOGGER.error("Failed to initialize VisibleTraders integration", exception);
        }
    }

    public static boolean isAvailable() {
        return loaded && enableCombinedOffers != null;
    }

    public static OfferSets captureOffers(MerchantMenu menu) {
        MerchantOffers initial = copy(((MerchantMenuAccessor) menu).easyAutoCycler$getTrader().getOffers());
        if (!isAvailable()) {
            return new OfferSets(initial, new MerchantOffers(), initial, false);
        }

        try {
            enableCombinedOffers.invoke(menu);
            MerchantOffers combined = copy(menu.getOffers());
            if (combined.size() <= initial.size()) {
                return new OfferSets(initial, new MerchantOffers(), initial, false);
            }

            MerchantOffers later = new MerchantOffers();
            later.addAll(combined.subList(initial.size(), combined.size()));
            MerchantOffers all = copy(initial);
            all.addAll(later);
            return new OfferSets(initial, later, all, true);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            EasyAutoCyclerMod.LOGGER.error("Failed to read VisibleTraders locked offers", exception);
            return new OfferSets(initial, new MerchantOffers(), initial, false);
        }
    }

    private static MerchantOffers copy(MerchantOffers source) {
        MerchantOffers result = new MerchantOffers();
        result.addAll(source);
        return result;
    }

    public record OfferSets(MerchantOffers initial, MerchantOffers later, MerchantOffers all,
                            boolean lockedOffersReady) {
        public MerchantOffers forScope(FilterEntry.TradeScope scope) {
            return switch (scope) {
                case INITIAL -> initial;
                case LATER -> later;
                case ALL -> all;
            };
        }
    }
}
