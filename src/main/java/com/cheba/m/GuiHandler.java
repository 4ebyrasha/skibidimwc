package com.cheba.m;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

// TODO: пофиксить эту дрисню старую
@SideOnly(Side.CLIENT)
public class GuiHandler {

    private static boolean isReplacing = false;

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof GuiMainMenu && !(current instanceof CustomGuiMainMenu)) {
            Minecraft.getMinecraft().displayGuiScreen(new CustomGuiMainMenu());
        } else if (current instanceof GuiIngameMenu && !(current instanceof CustomIngameMenu)) {
            Minecraft.getMinecraft().displayGuiScreen(new CustomIngameMenu());
        }
    }
}
