package com.uncraftbar.easyautocycler;

import com.mojang.blaze3d.platform.InputConstants;
import com.uncraftbar.easyautocycler.compat.MinecraftScreenCompat;
import com.uncraftbar.easyautocycler.gui.ConfigScreen;
import com.uncraftbar.easyautocycler.gui.CustomImageButton;
import com.uncraftbar.easyautocycler.config.ClientConfig;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ClientEventHandler {

    private static final Identifier CONFIG_BUTTON_NORMAL_RL = Identifier.fromNamespaceAndPath(EasyAutoCyclerMod.MODID, "textures/gui/config_button.png");
    private static final Identifier CONFIG_BUTTON_HOVER_RL = Identifier.fromNamespaceAndPath(EasyAutoCyclerMod.MODID, "textures/gui/config_button_highlighted.png");
    private static final Identifier PLAY_BUTTON_NORMAL_RL = Identifier.fromNamespaceAndPath(EasyAutoCyclerMod.MODID, "textures/gui/play_button.png");
    private static final Identifier PLAY_BUTTON_HOVER_RL = Identifier.fromNamespaceAndPath(EasyAutoCyclerMod.MODID, "textures/gui/play_button_highlighted.png");

    /**
     * Register per-MerchantScreen keyboard handler. Called from BEFORE_INIT (earliest point
     * at which ScreenKeyboardEvents exist on the fresh screen instance, per Fabric docs).
     * The toggle keybind doesn't fire through KeyMapping.consumeClick() while a Screen has input
     * focus, so we hook the screen's key-press event directly.
     */
    public static void onScreenBeforeInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof MerchantScreen)
                || !AutomationManager.INSTANCE.isSupportedVillagerTradeScreen(screen)) return;

        ScreenKeyboardEvents.beforeKeyPress(screen).register((s, event) -> {
            if (Keybindings.toggleAutoTradeKey == null) return;
            InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(Keybindings.toggleAutoTradeKey);
            if (bound.getType() != InputConstants.Type.KEYSYM) return;
            if (event.key() != bound.getValue()) return;
            AutomationManager.INSTANCE.toggle();
        });
    }

    public static void onScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof MerchantScreen merchantScreen)) {
            return;
        }
        if (!AutomationManager.INSTANCE.isSupportedVillagerTradeScreen(merchantScreen)) return;

        ClientConfig.Config clientConfig = ClientConfig.load();
        ClientConfig.ButtonLocation buttonLocation = clientConfig.parsedButtonLocation();
        if (buttonLocation == ClientConfig.ButtonLocation.NONE) return;
        int leftPos = (merchantScreen.width - 276) / 2;
        int topPos = (merchantScreen.height - 166) / 2;
        int buttonWidth = 18;
        int buttonHeight = 18;
        int buttonPadding = 2;
        int anchorX = buttonLocation == ClientConfig.ButtonLocation.TOP_RIGHT ? 250 : 107;
        int cycleButtonPosX = leftPos + anchorX + clientConfig.buttonOffsetX;
        int cycleButtonPosY = topPos + 8 + clientConfig.buttonOffsetY;
        int cycleButtonHeight = 14;
        int configButtonX = cycleButtonPosX;
        int configButtonY = cycleButtonPosY + cycleButtonHeight + buttonPadding;
        int toggleButtonX = cycleButtonPosX;
        int toggleButtonY = configButtonY + buttonHeight + buttonPadding;

        CustomImageButton configButton = new CycleAwareImageButton(
                    merchantScreen,
                configButtonX, configButtonY, buttonWidth, buttonHeight,
                CONFIG_BUTTON_NORMAL_RL, CONFIG_BUTTON_HOVER_RL,
                Component.translatable("gui.easyautocycler.button.config.tooltip"),
                (button) -> MinecraftScreenCompat.setScreen(Minecraft.getInstance(),
                        new ConfigScreen(merchantScreen, ConfigScreen.titleFor(merchantScreen))));

        CustomImageButton toggleButton = new CycleAwareImageButton(
                    merchantScreen,
                toggleButtonX, toggleButtonY, buttonWidth, buttonHeight,
                PLAY_BUTTON_NORMAL_RL, PLAY_BUTTON_HOVER_RL,
                Component.translatable("gui.easyautocycler.button.toggle.tooltip"),
                (button) -> AutomationManager.INSTANCE.toggle());

        Screens.getWidgets(screen).add(configButton);
        Screens.getWidgets(screen).add(toggleButton);
    }

    private static class CycleAwareImageButton extends CustomImageButton {
        private final MerchantScreen merchantScreen;
        private boolean cycleAvailable;

        CycleAwareImageButton(MerchantScreen merchantScreen, int x, int y, int width, int height,
                              Identifier textureNormal, Identifier textureHover,
                              Component tooltip, OnPress onPress) {
            super(x, y, width, height, textureNormal, textureHover, tooltip, onPress);
            this.merchantScreen = merchantScreen;
        }

        private void refreshCycleAvailability() {
            // Keep visible=true: Minecraft stops invoking the inner render hook for invisible widgets,
            // which would permanently prevent a temporarily unavailable button from recovering.
            this.cycleAvailable = AutomationManager.INSTANCE.isRunning()
                    || AutomationManager.INSTANCE.canCycleTrades(this.merchantScreen.getMenu());
            this.active = this.cycleAvailable;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
            refreshCycleAvailability();
            if (this.cycleAvailable) {
                super.extractContents(graphics, mouseX, mouseY, partialTicks);
            }
        }

        @Override
        public void onPress(InputWithModifiers input) {
            refreshCycleAvailability();
            if (this.cycleAvailable) {
                super.onPress(input);
            }
        }
    }

}
