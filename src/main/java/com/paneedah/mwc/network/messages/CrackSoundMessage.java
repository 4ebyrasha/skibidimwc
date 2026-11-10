package com.paneedah.mwc.network.messages;

import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public final class CrackSoundMessage implements IMessage {

    private double x, y, z;
    private byte soundType; // 0 = WHIZZ, 1 = CRACK_CLOSE, 2 = CRACK_DISTANT
    private float volume;
    private float pitch;
    private float missDist; // расстояние промаха для виньетки
    private boolean subsonic; // дозвуковая пуля = виньетка слабее

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        soundType = buf.readByte();
        volume = buf.readFloat();
        pitch = buf.readFloat();
        missDist = buf.readFloat();
        subsonic = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeByte(soundType);
        buf.writeFloat(volume);
        buf.writeFloat(pitch);
        buf.writeFloat(missDist);
        buf.writeBoolean(subsonic);
    }
}
