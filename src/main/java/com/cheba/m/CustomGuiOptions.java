package com.cheba.m;

import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class CustomGuiOptions extends GuiOptions {

    public CustomGuiOptions(GuiScreen parentScreen, GameSettings gameSettings) {
        super(parentScreen, gameSettings);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // черный фон
        drawRect(0, 0, this.width, this.height, 0xFF000000);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
