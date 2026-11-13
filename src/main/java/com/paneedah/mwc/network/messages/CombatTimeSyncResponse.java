package com.paneedah.mwc.network.messages;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Server response containing the server-side timestamps for a request.
 */
public final class CombatTimeSyncResponse implements IMessage {

    private long nonce;
    private long clientSendNanos;
    private long serverReceiveNanos;
    private long serverSendNanos;

    public CombatTimeSyncResponse() {
    }

    public CombatTimeSyncResponse(
            long nonce,
            long clientSendNanos,
            long serverReceiveNanos,
            long serverSendNanos
    ) {
        this.nonce = nonce;
        this.clientSendNanos = clientSendNanos;
        this.serverReceiveNanos = serverReceiveNanos;
        this.serverSendNanos = serverSendNanos;
    }

    public long getNonce() {
        return nonce;
    }

    public long getClientSendNanos() {
        return clientSendNanos;
    }

    public long getServerReceiveNanos() {
        return serverReceiveNanos;
    }

    public long getServerSendNanos() {
        return serverSendNanos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf.readableBytes() != 32) {
            throw new IllegalStateException("Invalid CombatTimeSyncResponse payload");
        }

        nonce = buf.readLong();
        clientSendNanos = buf.readLong();
        serverReceiveNanos = buf.readLong();
        serverSendNanos = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(nonce);
        buf.writeLong(clientSendNanos);
        buf.writeLong(serverReceiveNanos);
        buf.writeLong(serverSendNanos);
    }
}
