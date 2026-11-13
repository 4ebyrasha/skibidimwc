package com.paneedah.mwc.network.messages;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Client report confirming when the synchronization response was received.
 */
public final class CombatTimeSyncReport implements IMessage {

    private long nonce;
    private long clientSendNanos;
    private long clientReceiveNanos;

    public CombatTimeSyncReport() {
    }

    public CombatTimeSyncReport(
            long nonce,
            long clientSendNanos,
            long clientReceiveNanos
    ) {
        this.nonce = nonce;
        this.clientSendNanos = clientSendNanos;
        this.clientReceiveNanos = clientReceiveNanos;
    }

    public long getNonce() {
        return nonce;
    }

    public long getClientSendNanos() {
        return clientSendNanos;
    }

    public long getClientReceiveNanos() {
        return clientReceiveNanos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf.readableBytes() != 24) {
            throw new IllegalStateException("Invalid CombatTimeSyncReport payload");
        }

        nonce = buf.readLong();
        clientSendNanos = buf.readLong();
        clientReceiveNanos = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(nonce);
        buf.writeLong(clientSendNanos);
        buf.writeLong(clientReceiveNanos);
    }
}
