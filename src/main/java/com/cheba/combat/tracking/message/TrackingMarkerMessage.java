package com.cheba.combat.tracking.message;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class TrackingMarkerMessage implements IMessage {

    private int entityId;
    private long epoch;
    private int sequence;

    public TrackingMarkerMessage() {
    }

    public TrackingMarkerMessage(
            int entityId,
            long epoch,
            int sequence
    ) {
        this.entityId = entityId;
        this.epoch = epoch;
        this.sequence = sequence;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        entityId = buffer.readInt();
        epoch = buffer.readLong();
        sequence = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeLong(epoch);
        buffer.writeInt(sequence);
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
}
