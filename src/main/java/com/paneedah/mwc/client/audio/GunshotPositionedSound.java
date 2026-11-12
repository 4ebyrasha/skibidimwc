package com.paneedah.mwc.client.audio;

import com.cheba.apistalkerbase.acoustics.sound.IAcousticGainAwareSound;
import com.cheba.apistalkerbase.acoustics.sound.IGunshotSound;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;

public final class GunshotPositionedSound
        extends PositionedSoundRecord
        implements IGunshotSound, IAcousticGainAwareSound {

    private volatile float acousticGain = 1.0F;

    public GunshotPositionedSound(
            SoundEvent sound,
            SoundCategory category,
            float volume,
            float pitch,
            float x,
            float y,
            float z,
            ISound.AttenuationType attenuationType
    ) {
        super(sound, category, volume, pitch, x, y, z);
        this.attenuationType = attenuationType;
    }

    @Override
    public void setAcousticGain(float gain) {
        this.acousticGain = Math.max(
                0.0F,
                Math.min(gain, 1.0F)
        );
    }

    @Override
    public float getAcousticGain() {
        return acousticGain;
    }
}
