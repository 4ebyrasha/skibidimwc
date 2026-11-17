package com.paneedah.mwc.network.messages;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@NoArgsConstructor
public final class TryFireMessage implements IMessage {

    public static final int MAX_TARGETS = 16;
    public static final int MAX_SNAPSHOTS_PER_TARGET = 4;

    private boolean isBurst;
    private boolean isAimed;

    private long clientShotTimestampNanos;
    private boolean hasClientShotTimestamp;

    private float shotYaw;
    private float shotPitch;
    private boolean hasShotDirection;

    private List<TargetSnapshots> targetSnapshots =
            Collections.emptyList();

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
            long timestamp,
            float shotYaw,
            float shotPitch
    ) {
        this(isBurst, isAimed, timestamp);
        this.shotYaw = shotYaw;
        this.shotPitch = shotPitch;
        this.hasShotDirection = true;
    }

    public TryFireMessage(
            boolean isBurst,
            boolean isAimed,
            long timestamp,
            float shotYaw,
            float shotPitch,
            List<TargetSnapshots> snapshots
    ) {
        this(isBurst, isAimed, timestamp, shotYaw, shotPitch);

        if (snapshots != null) {
            this.targetSnapshots = new ArrayList<>(
                    snapshots.subList(
                            0,
                            Math.min(snapshots.size(), MAX_TARGETS)
                    )
            );
        }
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

        targetSnapshots = Collections.emptyList();

        if (buf.readableBytes() >= 8) {
            clientShotTimestampNanos = buf.readLong();
            hasClientShotTimestamp = true;
        }

        if (buf.readableBytes() >= 8) {
            shotYaw = buf.readFloat();
            shotPitch = buf.readFloat();
            hasShotDirection = true;
        }

        if (buf.isReadable()) {
            int targetCount = buf.readUnsignedByte();

            if (targetCount > MAX_TARGETS) {
                throw new IllegalStateException(
                        "Too many viewer snapshot targets: " + targetCount
                );
            }

            List<TargetSnapshots> targets =
                    new ArrayList<>(targetCount);

            for (int i = 0; i < targetCount; i++) {
                int entityId = buf.readInt();
                long epoch = buf.readLong();
                int sequence = buf.readInt();
                int snapshotCount = buf.readUnsignedByte();

                if (entityId < 0 || epoch <= 0L || sequence < 0
                        || snapshotCount == 0
                        || snapshotCount > MAX_SNAPSHOTS_PER_TARGET) {
                    throw new IllegalStateException(
                            "Invalid viewer snapshot metadata"
                    );
                }

                List<Snapshot> snapshots =
                        new ArrayList<>(snapshotCount);

                for (int j = 0; j < snapshotCount; j++) {
                    long timestamp = buf.readLong();

                    AxisAlignedBB box = new AxisAlignedBB(
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble()
                    );

                    if (!isValidBox(box)) {
                        throw new IllegalStateException(
                                "Invalid viewer snapshot bounding box"
                        );
                    }

                    snapshots.add(new Snapshot(timestamp, box));
                }

                targets.add(new TargetSnapshots(
                        entityId,
                        epoch,
                        sequence,
                        snapshots
                ));
            }

            targetSnapshots = targets;
        }

        if (buf.isReadable()) {
            throw new IllegalStateException(
                    "Invalid TryFireMessage: "
                            + buf.readableBytes() + " bytes remaining"
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

        int targetCount = Math.min(
                targetSnapshots.size(),
                MAX_TARGETS
        );

        buf.writeByte(targetCount);

        for (int i = 0; i < targetCount; i++) {
            TargetSnapshots target = targetSnapshots.get(i);

            buf.writeInt(target.entityId);
            buf.writeLong(target.epoch);
            buf.writeInt(target.sequence);

            int count = Math.min(
                    target.snapshots.size(),
                    MAX_SNAPSHOTS_PER_TARGET
            );

            buf.writeByte(count);

            for (int j = 0; j < count; j++) {
                Snapshot snapshot = target.snapshots.get(j);

                AxisAlignedBB box = snapshot.boundingBox;

                buf.writeLong(snapshot.timestampNanos);

                buf.writeDouble(box.minX);
                buf.writeDouble(box.minY);
                buf.writeDouble(box.minZ);
                buf.writeDouble(box.maxX);
                buf.writeDouble(box.maxY);
                buf.writeDouble(box.maxZ);
            }
        }
    }

    private static boolean isValidBox(AxisAlignedBB box) {
        return Double.isFinite(box.minX)
                && Double.isFinite(box.minY)
                && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX)
                && Double.isFinite(box.maxY)
                && Double.isFinite(box.maxZ)
                && box.minX <= box.maxX
                && box.minY <= box.maxY
                && box.minZ <= box.maxZ;
    }

    public static final class TargetSnapshots {

        private final int entityId;
        private final long epoch;
        private final int sequence;
        private final List<Snapshot> snapshots;

        public TargetSnapshots(
                int entityId,
                long epoch,
                int sequence,
                List<Snapshot> snapshots
        ) {
            this.entityId = entityId;
            this.epoch = epoch;
            this.sequence = sequence;
            this.snapshots = snapshots;
        }

        public int getEntityId() {
            return entityId;
        }

        public long getEpoch() {
            return epoch;
        }

        public int getSequence() {
            return sequence;
        }

        public List<Snapshot> getSnapshots() {
            return snapshots;
        }
    }

    public static final class Snapshot {

        private final long timestampNanos;
        private final AxisAlignedBB boundingBox;

        public Snapshot(long timestampNanos, AxisAlignedBB boundingBox) {
            this.timestampNanos = timestampNanos;
            this.boundingBox = boundingBox;
        }

        public long getTimestampNanos() {
            return timestampNanos;
        }

        public AxisAlignedBB getBoundingBox() {
            return boundingBox;
        }
    }
}
