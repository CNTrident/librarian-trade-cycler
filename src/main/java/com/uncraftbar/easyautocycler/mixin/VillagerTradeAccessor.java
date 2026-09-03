package com.uncraftbar.easyautocycler.mixin;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.trading.VillagerTrade;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(VillagerTrade.class)
public interface VillagerTradeAccessor {
    @Accessor("wants")
    TradeCost easyAutoCycler$getWants();

    @Accessor("additionalWants")
    Optional<TradeCost> easyAutoCycler$getAdditionalWants();

    @Accessor("gives")
    ItemStackTemplate easyAutoCycler$getGives();
}
