package com.paneedah.mwc.network.messages;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

@Getter
@NoArgsConstructor
public final class TryFireMessage implements IMessage {

    private boolean isBurst;
    private boolean isAimed;

    private long clientShotTimestampNanos;

    private boolean hasClientShotTimestamp;

    public TryFireMessage(boolean isBurst, boolean isAimed) {
        this(
                isBurst,
                isAimed,
                System.nanoTime()
        );
    }

    public TryFireMessage(
            boolean isBurst,
            boolean isAimed,
            long clientShotTimestampNanos
    ) {

        this.isBurst = isBurst;
        this.isAimed = isAimed;

        this.clientShotTimestampNanos = clientShotTimestampNanos;
        this.hasClientShotTimestamp = true;
    }

    public boolean hasClientShotTimestamp() {
        return hasClientShotTimestamp;
    }

    @Override
    public void fromBytes(ByteBuf buf) {

        isBurst = buf.readBoolean();
        isAimed = buf.readBoolean();

        clientShotTimestampNanos = 0L;
        hasClientShotTimestamp = false;
        if (buf.readableBytes() >= 8) {

            clientShotTimestampNanos = buf.readLong();
            hasClientShotTimestamp = true;

        } else if (buf.readableBytes() != 0) {

            throw new IllegalStateException(
                    "Invalid TryFireMessage: "
                            + buf.readableBytes()
                            + " bytes remaining"
            );
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {

        buf.writeBoolean(isBurst);
        buf.writeBoolean(isAimed);

        buf.writeLong(clientShotTimestampNanos);
    }
}
