package com.cheba;

import com.paneedah.mwc.instancing.PlayerWeaponInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.paneedah.mwc.MWC;

@SuppressWarnings("unused")
// hold to run handler
@SideOnly(Side.CLIENT)
public class Run {

    private final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side != Side.CLIENT || event.phase != TickEvent.Phase.START) {
            return;
        }

        if (mc.player == null || mc.currentScreen != null) {
            return;
        }

        EntityPlayerSP player = mc.player;

        boolean canSprint = player.getFoodStats().getFoodLevel() > 6
                && !player.isSneaking()
                && !player.isInWater()
                && !player.isElytraFlying()
                && !player.isPlayerSleeping()
                && !player.isRiding()
                && !player.capabilities.isFlying;

        boolean sprintKeyPressed = mc.gameSettings.keyBindSprint.isKeyDown();

        boolean isAiming = false;
        try {
            PlayerWeaponInstance weaponInstance = MWC.modContext.getMainHeldWeapon();
            if (weaponInstance != null) {
                isAiming = weaponInstance.isAimed();
            }
        } catch (Exception ignored) {
        }

        player.setSprinting(sprintKeyPressed && canSprint && player.movementInput.moveForward > 0 && !isAiming);

        player.movementInput.moveForward = Math.min(player.movementInput.moveForward, 0.9F);
    }
}