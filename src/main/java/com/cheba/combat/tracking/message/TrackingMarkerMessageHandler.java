package com.cheba.combat.tracking.message;

import com.cheba.combat.client.ClientTrackingSequenceManager;
import dev.redstudio.redcore.utils.NetworkUtil;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public final class TrackingMarkerMessageHandler
        implements IMessageHandler<TrackingMarkerMessage, IMessage> {

    @Override
    @SideOnly(Side.CLIENT)
    public IMessage onMessage(
            TrackingMarkerMessage message,
            MessageContext context
    ) {
        NetworkUtil.processMessage(context, () ->
                ClientTrackingSequenceManager.accept(
                        message.getEntityId(),
                        message.getEpoch(),
                        message.getSequence()
                )
        );

        return null;
    }
}
