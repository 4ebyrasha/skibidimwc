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
 * Integrates server-authoritative player movement tracking with Minecraft's
 * vanilla network handler.
 *
 * <p>Snapshots are recorded only after the server has accepted and applied
 * a movement packet. This prevents client-provided, rejected coordinates
 * from entering combat history.</p>
 *
 * <p>Server-side position corrections invalidate the previous history.
 * A correction creates a discontinuity in the player's movement timeline,
 * so snapshots from before it must not be used for hit registration.</p>
 *
 * <p>This mixin does not modify player movement, packet validation,
 * collision handling, or vanilla teleport behavior.</p>
 */
@Mixin(NetHandlerPlayServer.class)
public abstract class NetHandlerPlayServerMixin {

    /**
     * Player associated with this network handler.
     *
     * <p>The field is owned by {@link NetHandlerPlayServer}; this shadow
     * exists only to access the player from injected callbacks.</p>
     */
    @Shadow
    EntityPlayerMP player;

    /**
     * Records a snapshot after vanilla has successfully updated its last
     * accepted player position.
     *
     * <p>The injection is deliberately placed after the write to
     * {@code lastGoodZ}. In the vanilla movement handler, this write occurs
     * in the accepted movement path. Rejected movement packets return before
     * reaching it.</p>
     *
     * <p>{@code processPlayer} is invoked on the server thread after
     * {@code PacketThreadUtil.checkThreadAndEnqueue} has completed its
     * scheduling handoff.</p>
     *
     * @param packetIn the movement packet currently being processed
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
     * Invalidates the player's previous movement history before vanilla
     * applies a server-authoritative position correction or teleport.
     *
     * <p>The six-argument overload is the implementation that accepts
     * relative position and rotation flags. Vanilla's five-argument overload
     * delegates to it, so this hook covers both call paths without duplicate
     * injections.</p>
     *
     * <p>Invalidation is required because a teleport or correction breaks
     * continuity between the previous snapshots and the new position.</p>
     *
     * @param x target X coordinate
     * @param y target Y coordinate
     * @param z target Z coordinate
     * @param yaw target yaw
     * @param pitch target pitch
     * @param relativeSet position and rotation components interpreted
     *                    relative to the player's current state
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
