package com.cheba.m;

import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.Display;

@SideOnly(Side.CLIENT)
public class CustomGuiMainMenu extends GuiScreen {

    private static final ResourceLocation BACKGROUND = new ResourceLocation("m", "textures/gui/bg.png");
    // TODO: вывести айпишники в конфиг
    //private static final String SERVER_IP = "legendofzone.mcmod.ru";
    //private static final String SERVER_IP = "127.0.0.1:25802";
    private static final String SERVER_IP = "moscow-3.cloudblaze.org:20102";
    private static final String SERVER_NAME = "LegendOfZone";

    @Override
    public void initGui() {
        Display.setTitle("LegendOfZone");

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int leftX = centerX - 225;

        addButton(new GuiButton(0, leftX, centerY - 50, 120, 20,
                I18n.format("menu.multiplayer")));

        addButton(new GuiButton(1, leftX, centerY - 20, 120, 20,
                I18n.format("menu.options")));

        addButton(new GuiButton(2, leftX, centerY + 10, 120, 20,
                I18n.format("menu.quit")));

        addButton(new GuiButton(4, leftX, centerY + 40, 120, 20,
                I18n.format("fml.menu.mods")));

        addButton(new GuiButtonLanguage(3, leftX, centerY + 70));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        mc.getTextureManager().bindTexture(BACKGROUND);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        drawModalRectWithCustomSizedTexture(
                0, 0, 0, 0,
                this.width, this.height,
                this.width, this.height
        );

        this.drawString(this.fontRenderer,
                "LegendOfZone 0.02.1",
                5, this.height - 10, 0xFFFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0:
                ServerData serverData =
                        new ServerData(SERVER_NAME, SERVER_IP, false);

                this.mc.displayGuiScreen(
                        new GuiConnecting(this, this.mc, serverData)
                );
                break;

            case 1:
                this.mc.displayGuiScreen(
                        new GuiOptions(this, this.mc.gameSettings)
                );
                break;

            case 2:
                this.mc.shutdown();
                break;

            case 3:
                this.mc.displayGuiScreen(
                        new GuiLanguage(
                                this,
                                this.mc.gameSettings,
                                this.mc.getLanguageManager()
                        )
                );
                break;

            case 4:
                this.mc.displayGuiScreen(new GuiModList(this));
                break;
        }
    }
}
