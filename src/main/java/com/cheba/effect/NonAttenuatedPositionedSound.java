package com.cheba.effect;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;

public class NonAttenuatedPositionedSound extends PositionedSoundRecord {
    public NonAttenuatedPositionedSound(SoundEvent sound,
                                        SoundCategory category,
                                        float volume,
                                        float pitch,
                                        float x, float y, float z) {
        super(sound, category, volume, pitch, x, y, z);
        this.attenuationType = ISound.AttenuationType.NONE; // отключение затухания
    }
}
