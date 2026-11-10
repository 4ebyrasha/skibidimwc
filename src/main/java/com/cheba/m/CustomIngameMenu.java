
package com.cheba.m;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;

@SideOnly(Side.CLIENT)
public class CustomIngameMenu extends GuiScreen {

    private static final ResourceLocation BACKGROUND =
            new ResourceLocation("m", "textures/gui/gbg.png");

    @Override
    public void initGui() {
        this.buttonList.clear();

        int centerX = this.width / 2;
        int startY = this.height / 4 + 24;
        int buttonWidth = 120;
        int buttonHeight = 20;
        int gap = 24;

        // Вернуться в игру
        addButton(new GuiButton(
                4,
                centerX - buttonWidth / 2,
                startY,
                buttonWidth,
                buttonHeight,
                I18n.format("menu.returnToGame")
        ));

        // Настройки игры
        addButton(new GuiButton(
                0,
                centerX - buttonWidth / 2,
                startY + gap,
                buttonWidth,
                buttonHeight,
                I18n.format("menu.options")
        ));

        // Настройки модов
        addButton(new GuiButton(
                2,
                centerX - buttonWidth / 2,
                startY + 2 * gap,
                buttonWidth,
                buttonHeight,
                I18n.format("fml.menu.mods")
        ));

        // Выход
        addButton(new GuiButton(
                1,
                centerX - buttonWidth / 2,
                startY + 3 * gap,
                buttonWidth,
                buttonHeight,
                I18n.format("menu.quit")
        ));
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

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 4:
                mc.displayGuiScreen(null);
                mc.setIngameFocus();
                break;

            case 0:
                mc.displayGuiScreen(
                        new CustomGuiOptions(this, mc.gameSettings)
                );
                break;

            case 2:
                mc.displayGuiScreen(new GuiModList(this));
                break;

            case 1:
                if (mc.world != null) {
                    mc.world.sendQuittingDisconnectingPacket();
                }

                mc.loadWorld(null);
                mc.displayGuiScreen(new GuiMainMenu());
                break;

            default:
                super.actionPerformed(button);
        }
    }
}
