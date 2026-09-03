package com.uncraftbar.easyautocycler.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class MinecraftScreenCompat {
    private MinecraftScreenCompat() {
    }

    public static Screen getScreen(Minecraft minecraft) {
        return minecraft.screen;
    }

    public static void setScreen(Minecraft minecraft, Screen screen) {
        minecraft.setScreen(screen);
    }
}
