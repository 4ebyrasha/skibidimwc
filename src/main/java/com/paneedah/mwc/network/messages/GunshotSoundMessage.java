package com.paneedah.mwc.network.messages;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

@Getter
@NoArgsConstructor
public final class GunshotSoundMessage implements IMessage {

    private double x;
    private double y;
    private double z;

    private int weaponId;

    private boolean silenced;
    private boolean isBurst;
    private boolean shooterIndoor;

    private float pitch;
    private float baseVolume;

    private int shooterEntityId;
    private boolean showMuzzleFlash;


    private long clientShotTimestampNanos;

    private boolean hasTimestamp;

    public GunshotSoundMessage(
            double x,
            double y,
            double z,
            int weaponId,
            boolean silenced,
            boolean isBurst,
            boolean shooterIndoor,
            float pitch,
            float baseVolume,
            int shooterEntityId,
            boolean showMuzzleFlash,
            long clientShotTimestampNanos
    ) {

        this.x = x;
        this.y = y;
        this.z = z;

        this.weaponId = weaponId;

        this.silenced = silenced;
        this.isBurst = isBurst;
        this.shooterIndoor = shooterIndoor;

        this.pitch = pitch;
        this.baseVolume = baseVolume;

        this.shooterEntityId = shooterEntityId;
        this.showMuzzleFlash = showMuzzleFlash;

        this.clientShotTimestampNanos = clientShotTimestampNanos;
        this.hasTimestamp = true;
    }

    public boolean hasTimestamp() {
        return hasTimestamp;
    }

    @Override
    public void fromBytes(ByteBuf buf) {

        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();

        weaponId = buf.readInt();

        silenced = buf.readBoolean();
        isBurst = buf.readBoolean();
        shooterIndoor = buf.readBoolean();

        pitch = buf.readFloat();
        baseVolume = buf.readFloat();

        shooterEntityId = -1;
        showMuzzleFlash = false;

        clientShotTimestampNanos = 0L;
        hasTimestamp = false;

        /*
         * Old packet: 39 bytes.
         * New packet: 52 bytes.
         *
         * Additional:
         * int  shooterEntityId          4 bytes
         * bool showMuzzleFlash          1 byte
         * long clientShotTimestamp     8 bytes
         */
        int remaining = buf.readableBytes();

        if (remaining >= 13) {

            shooterEntityId = buf.readInt();
            showMuzzleFlash = buf.readBoolean();

            clientShotTimestampNanos = buf.readLong();

            hasTimestamp = true;

        } else if (remaining != 0) {

            throw new IllegalStateException(
                    "Invalid GunshotSoundMessage extension: "
                            + remaining
                            + " bytes remaining"
            );
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {

        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);

        buf.writeInt(weaponId);

        buf.writeBoolean(silenced);
        buf.writeBoolean(isBurst);
        buf.writeBoolean(shooterIndoor);

        buf.writeFloat(pitch);
        buf.writeFloat(baseVolume);

        buf.writeInt(shooterEntityId);
        buf.writeBoolean(showMuzzleFlash);

        buf.writeLong(clientShotTimestampNanos);
    }
}
