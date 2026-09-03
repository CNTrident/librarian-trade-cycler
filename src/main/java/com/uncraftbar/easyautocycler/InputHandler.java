package com.uncraftbar.easyautocycler;

import com.uncraftbar.easyautocycler.compat.MinecraftScreenCompat;
import com.uncraftbar.easyautocycler.gui.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;

public class InputHandler {

    public static void onClientTick(Minecraft client) {
        if (Keybindings.openConfigKey != null && Keybindings.openConfigKey.consumeClick()) {
            if (MinecraftScreenCompat.getScreen(client) instanceof MerchantScreen screen
                    && AutomationManager.INSTANCE.isSupportedVillagerTradeScreen(screen)) {
                MinecraftScreenCompat.setScreen(client, new ConfigScreen(screen, ConfigScreen.titleFor(screen)));
            }
        }

        if (Keybindings.toggleAutoTradeKey != null && Keybindings.toggleAutoTradeKey.consumeClick()) {
            if (MinecraftScreenCompat.getScreen(client) instanceof MerchantScreen screen
                    && AutomationManager.INSTANCE.isSupportedVillagerTradeScreen(screen)) {
                AutomationManager.INSTANCE.toggle();
            }
        }
    }
}
