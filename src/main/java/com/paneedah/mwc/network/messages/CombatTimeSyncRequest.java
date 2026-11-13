package com.paneedah.mwc.network.messages;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Client request for a server-clock synchronization sample.
 */
public final class CombatTimeSyncRequest implements IMessage {

    private long nonce;
    private long clientSendNanos;

    public CombatTimeSyncRequest() {
    }

    public CombatTimeSyncRequest(long nonce, long clientSendNanos) {
        this.nonce = nonce;
        this.clientSendNanos = clientSendNanos;
    }

    public long getNonce() {
        return nonce;
    }

    public long getClientSendNanos() {
        return clientSendNanos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf.readableBytes() != 16) {
            throw new IllegalStateException("Invalid CombatTimeSyncRequest payload");
        }

        nonce = buf.readLong();
        clientSendNanos = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(nonce);
        buf.writeLong(clientSendNanos);
    }
}
