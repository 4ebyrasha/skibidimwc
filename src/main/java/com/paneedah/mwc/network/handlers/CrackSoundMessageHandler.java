package com.paneedah.mwc.network.handlers;

import com.cheba.effect.BulletSuppressionEffect;
import com.paneedah.mwc.network.messages.CrackSoundMessage;
import com.paneedah.weaponlib.WeaponSpawnEntity;
import dev.redstudio.redcore.utils.NetworkUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public final class CrackSoundMessageHandler implements IMessageHandler<CrackSoundMessage, IMessage> {

    @Override
    @SideOnly(Side.CLIENT)
    public IMessage onMessage(final CrackSoundMessage message, final MessageContext context) {
        NetworkUtil.processMessage(context, () -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null) return;

            SoundEvent sound = null;
            switch (message.getSoundType()) {
                case 0: sound = WeaponSpawnEntity.WHIZZ_SOUND; break;
                case 1: sound = WeaponSpawnEntity.CRACK_CLOSE_SOUND; break;
                case 2: sound = WeaponSpawnEntity.CRACK_DISTANT_SOUND; break;
            }

            if (sound != null) {
                mc.player.world.playSound(mc.player,
                        message.getX(), message.getY(), message.getZ(),
                        sound, SoundCategory.MASTER, message.getVolume(), message.getPitch());
            }

            if (message.getMissDist() >= 0) {
                BulletSuppressionEffect.triggerFromBullet(message.getMissDist(), message.isSubsonic());
            }
        });
        return null;
    }
}
