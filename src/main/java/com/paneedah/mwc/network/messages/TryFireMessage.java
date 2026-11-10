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

    private float shotYaw;
    private float shotPitch;
    private boolean hasShotDirection;

    public TryFireMessage(boolean isBurst, boolean isAimed) {
        this(isBurst, isAimed, System.nanoTime());
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

    public TryFireMessage(
            boolean isBurst,
            boolean isAimed,
            long clientShotTimestampNanos,
            float shotYaw,
            float shotPitch
    ) {
        this(isBurst, isAimed, clientShotTimestampNanos);

        this.shotYaw = shotYaw;
        this.shotPitch = shotPitch;
        this.hasShotDirection = true;
    }

    public boolean hasClientShotTimestamp() {
        return hasClientShotTimestamp;
    }

    public boolean hasShotDirection() {
        return hasShotDirection;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        isBurst = buf.readBoolean();
        isAimed = buf.readBoolean();

        clientShotTimestampNanos = 0L;
        hasClientShotTimestamp = false;
        shotYaw = 0.0F;
        shotPitch = 0.0F;
        hasShotDirection = false;

        if (buf.readableBytes() >= 8) {
            clientShotTimestampNanos = buf.readLong();
            hasClientShotTimestamp = true;
        }

        if (buf.readableBytes() >= 8) {
            shotYaw = buf.readFloat();
            shotPitch = buf.readFloat();
            hasShotDirection = true;
        }

        if (buf.readableBytes() != 0) {
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

        if (hasShotDirection) {
            buf.writeFloat(shotYaw);
            buf.writeFloat(shotPitch);
        }
    }
}
