package com.cheba.knockback;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.Capability.IStorage;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nonnull;
import java.util.UUID;

public class KnockbackCapability implements ICapabilitySerializable<NBTBase> {

    private static final UUID IMMUNITY_MODIFIER_ID =
            UUID.fromString("9d7a8c2e-5f31-4b6a-8e20-1c3d5a7b9f11");

    private static final AttributeModifier IMMUNITY_MODIFIER =
            new AttributeModifier(
                    IMMUNITY_MODIFIER_ID,
                    "MWC knockback immunity",
                    1.0D,
                    0
            );

    public interface State {

        boolean isImmune();

        void setImmune(boolean immune);
    }

    public static class StateImpl implements State {

        private boolean immune = true;

        @Override
        public boolean isImmune() {
            return immune;
        }

        @Override
        public void setImmune(boolean immune) {
            this.immune = immune;
        }
    }

    public static class Storage implements IStorage<State> {

        @Override
        public NBTBase writeNBT(
                Capability<State> capability,
                State instance,
                EnumFacing side
        ) {
            NBTTagCompound compound = new NBTTagCompound();
            compound.setBoolean("immune", instance.isImmune());
            return compound;
        }

        @Override
        public void readNBT(
                Capability<State> capability,
                State instance,
                EnumFacing side,
                NBTBase nbt
        ) {
            if (!(nbt instanceof NBTTagCompound)) {
                return;
            }

            NBTTagCompound compound = (NBTTagCompound) nbt;

            // Old player data without this key keeps the default value.
            if (compound.hasKey("immune", 1)) {
                instance.setImmune(compound.getBoolean("immune"));
            }
        }
    }

    @CapabilityInject(State.class)
    private static Capability<State> CAPABILITY = null; // don't make this final

    private static boolean registered;

    private final State instance = CAPABILITY.getDefaultInstance();

    public static void register() {
        if (registered) {
            return;
        }

        CapabilityManager.INSTANCE.register(
                State.class,
                new Storage(),
                StateImpl::new
        );

        registered = true;
    }

    public static State get(EntityPlayer player) {
        if (player == null || CAPABILITY == null) {
            return null;
        }

        return player.getCapability(CAPABILITY, null);
    }

    /**
     * Changes knockback immunity and immediately applies it on the server.
     */
    public static void setImmune(EntityPlayer player, boolean immune) {
        if (player == null || player.world.isRemote) {
            return;
        }

        State state = get(player);

        if (state == null) {
            return;
        }

        state.setImmune(immune);
        apply(player);
    }

    /**
     * Applies only this capability's modifier.
     * Other mods' base values and modifiers remain untouched.
     */
    public static void apply(EntityPlayer player) {
        if (player == null || player.world.isRemote) {
            return;
        }

        State state = get(player);

        if (state == null) {
            return;
        }

        IAttributeInstance attribute = player.getEntityAttribute(
                SharedMonsterAttributes.KNOCKBACK_RESISTANCE
        );

        if (attribute == null) {
            return;
        }

        if (attribute.getModifier(IMMUNITY_MODIFIER_ID) != null) {
            attribute.removeModifier(IMMUNITY_MODIFIER_ID);
        }

        if (state.isImmune()) {
            attribute.applyModifier(IMMUNITY_MODIFIER);
        }
    }

    public static void copy(EntityPlayer original, EntityPlayer replacement) {
        if (original == null || replacement == null) {
            return;
        }

        State originalState = get(original);
        State replacementState = get(replacement);

        if (originalState == null || replacementState == null) {
            return;
        }

        replacementState.setImmune(originalState.isImmune());
    }

    @Override
    public boolean hasCapability(
            @Nonnull Capability<?> capability,
            EnumFacing facing
    ) {
        return capability == CAPABILITY;
    }

    @Override
    public <T> T getCapability(
            @Nonnull Capability<T> capability,
            EnumFacing facing
    ) {
        return capability == CAPABILITY
                ? CAPABILITY.cast(instance)
                : null;
    }

    @Override
    public NBTBase serializeNBT() {
        return CAPABILITY.getStorage().writeNBT(
                CAPABILITY,
                instance,
                null
        );
    }

    @Override
    public void deserializeNBT(NBTBase nbt) {
        CAPABILITY.getStorage().readNBT(
                CAPABILITY,
                instance,
                null,
                nbt
        );
    }
}
