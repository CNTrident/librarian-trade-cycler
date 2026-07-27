package com.uncraftbar.easyautocycler.compat;

import com.uncraftbar.easyautocycler.EasyAutoCyclerMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.lang.reflect.Method;

/** Optional client bridge for VisibleTraders 2.3.0 and newer. */
public final class VisibleTradersCompat {

    private static final String MOD_ID = "visibletraders";
    private static final String ENABLE_COMBINED_OFFERS_METHOD = "visibleTraders$enableCombinedOffers";

    private final boolean loaded;
    private final String version;
    private Method enableCombinedOffers;
    private boolean methodLookupAttempted;

    public VisibleTradersCompat() {
        this.loaded = FabricLoader.getInstance().isModLoaded(MOD_ID);
        this.version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");

        if (loaded) {
            EasyAutoCyclerMod.LOGGER.info("VisibleTraders {} detected; future-trade filtering will be enabled", version);
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public String getVersion() {
        return version;
    }

    /**
     * Resolves the method injected into MerchantMenu by VisibleTraders. Reflection keeps
     * VisibleTraders optional and prevents its classes from becoming a hard dependency.
     */
    public boolean isIntegrationAvailable(MerchantMenu menu) {
        if (!loaded) return false;
        if (enableCombinedOffers != null) return true;
        if (methodLookupAttempted) return false;

        methodLookupAttempted = true;
        try {
            enableCombinedOffers = menu.getClass().getMethod(ENABLE_COMBINED_OFFERS_METHOD);
            return true;
        } catch (ReflectiveOperationException exception) {
            EasyAutoCyclerMod.LOGGER.error(
                    "VisibleTraders {} does not expose {}. Version 2.3.0 or newer is required.",
                    version, ENABLE_COMBINED_OFFERS_METHOD, exception);
            return false;
        }
    }

    public OfferView getOffers(MerchantMenu menu) {
        MerchantOffers unlockedOffers = menu.getOffers();
        if (!loaded) {
            return new OfferView(unlockedOffers, false, true);
        }
        if (!isIntegrationAvailable(menu)) {
            return new OfferView(unlockedOffers, true, false);
        }

        try {
            // VisibleTraders consumes this flag on the next getOffers() invocation.
            enableCombinedOffers.invoke(menu);
            MerchantOffers combinedOffers = menu.getOffers();
            boolean previewReady = combinedOffers.size() > unlockedOffers.size()
                    && startsWithCurrentUnlockedOffers(unlockedOffers, combinedOffers);
            return new OfferView(combinedOffers, true, previewReady);
        } catch (ReflectiveOperationException exception) {
            EasyAutoCyclerMod.LOGGER.error("Failed to request VisibleTraders combined offers", exception);
            enableCombinedOffers = null;
            methodLookupAttempted = true;
            return new OfferView(unlockedOffers, true, false);
        }
    }

    /** Rejects a larger combined list if it still belongs to the previous cycle. */
    private static boolean startsWithCurrentUnlockedOffers(MerchantOffers unlockedOffers,
                                                            MerchantOffers combinedOffers) {
        if (combinedOffers.size() < unlockedOffers.size()) return false;

        for (int index = 0; index < unlockedOffers.size(); index++) {
            MerchantOffer unlocked = unlockedOffers.get(index);
            MerchantOffer combined = combinedOffers.get(index);
            if (!ItemStack.matches(unlocked.getBaseCostA(), combined.getBaseCostA())
                    || !ItemStack.matches(unlocked.getCostB(), combined.getCostB())
                    || !ItemStack.matches(unlocked.getResult(), combined.getResult())) {
                return false;
            }
        }
        return true;
    }

    public record OfferView(MerchantOffers offers, boolean visibleTradersLoaded, boolean previewReady) {
    }
}
