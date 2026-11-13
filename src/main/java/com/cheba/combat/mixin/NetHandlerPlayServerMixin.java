package com.cheba.combat.mixin;

import com.cheba.combat.history.CombatHistoryManager;
import com.cheba.combat.logging.CombatLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.CPacketPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Connects vanilla server-side player movement processing to the combat
 * history system.
 *
 * <p>The mixin observes movement processing without replacing or modifying
 * vanilla movement validation. A snapshot is recorded only after vanilla
 * updates the last accepted Z coordinate.</p>
 *
 * <p>Server-authoritative position corrections invalidate the existing
 * history because snapshots on opposite sides of a teleport or correction
 * must never be interpolated as one continuous movement sequence.</p>
 *
 * <p>All callbacks execute as part of the server-side network handler.
 * The history manager is therefore expected to be accessed exclusively
 * from the logical server thread.</p>
 */
@Mixin(NetHandlerPlayServer.class)
public abstract class NetHandlerPlayServerMixin {

    /**
     * The player controlled by this network handler.
     *
     * <p>This field is owned by {@link NetHandlerPlayServer}. The shadow
     * declaration mirrors its visibility and type.</p>
     */
    @Shadow
    public EntityPlayerMP player;

    /**
     * Records the player's authoritative state after an accepted movement
     * packet has updated vanilla's {@code lastGoodZ} field.
     *
     * <p>The injection is placed after the field write, rather than at the
     * beginning or end of {@code processPlayer}. This avoids recording
     * client-provided positions from movement packets rejected by vanilla.</p>
     *
     * <p>Vanilla first uses {@code PacketThreadUtil.checkThreadAndEnqueue}
     * to transfer packet processing to the server thread. Consequently,
     * this callback records state on the logical server thread.</p>
     *
     * @param packetIn movement packet being processed by vanilla
     * @param callbackInfo Mixin callback context
     */
    @Inject(
            method = "processPlayer(Lnet/minecraft/network/play/client/CPacketPlayer;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/network/NetHandlerPlayServer;lastGoodZ:D",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER,
                    ordinal = 0
            ),
            require = 1
    )
    private void mwcCombat$recordAcceptedMovement(
            CPacketPlayer packetIn,
            CallbackInfo callbackInfo
    ) {
        if (player == null || player.isDead || player.world == null) {
            CombatLog.warn(
                    "NetHandlerPlayServerMixin",
                    "recordAcceptedMovement",
                    "Snapshot skipped: player is unavailable"
            );
            return;
        }

        CombatHistoryManager.recordSnapshot(player);
    }

    /**
     * Invalidates movement history before vanilla applies a server-side
     * position correction.
     *
     * <p>The six-argument overload is the implementation accepting relative
     * position and rotation flags. The five-argument overload delegates to
     * it, so injecting into this implementation covers both call paths.</p>
     *
     * <p>Invalidation prevents historical hit registration from using
     * positions recorded before a discontinuity in the player's movement.</p>
     *
     * @param x requested X coordinate
     * @param y requested Y coordinate
     * @param z requested Z coordinate
     * @param yaw requested yaw
     * @param pitch requested pitch
     * @param relativeSet flags describing relative position and rotation
     * @param callbackInfo Mixin callback context
     */
    @Inject(
            method = "setPlayerLocation(DDDFFLjava/util/Set;)V",
            at = @At("HEAD"),
            require = 1
    )
    private void mwcCombat$invalidateHistoryOnCorrection(
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            java.util.Set<?> relativeSet,
            CallbackInfo callbackInfo
    ) {
        if (player == null) {
            return;
        }

        CombatHistoryManager.invalidate(
                player.getUniqueID(),
                "server_position_correction"
        );

        CombatLog.debug(
                "NetHandlerPlayServerMixin",
                "invalidateHistoryOnCorrection",
                "Player=" + player.getUniqueID()
                        + " | TargetPosition=" + x + ", " + y + ", " + z
                        + " | TargetRotation=" + yaw + ", " + pitch
        );
    }
}
