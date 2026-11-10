
package com.paneedah.mwc.client.handlers;

import com.cheba.effect.NonAttenuatedPositionedSound;
import com.paneedah.mwc.network.messages.GunshotSoundMessage;
import com.paneedah.weaponlib.ClientEventHandler;
import com.paneedah.weaponlib.Weapon;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import static com.paneedah.mwc.ProjectConstants.LOGGER;

@Mod.EventBusSubscriber(Side.CLIENT)
public final class ClientGunshotSoundHandler {

    private ClientGunshotSoundHandler() {}

    /*
     * Initial jitter buffer: 92 milliseconds
     */
    private static final long INITIAL_BUFFER_NANOS = 92_000_000L;

    /*
     * Additional safety margin
     */
    private static final long BUFFER_MARGIN_NANOS = 10_000_000L;

    /*
     * Maximum adaptive buffer: 250 milliseconds
     */
    private static final long MAX_BUFFER_NANOS = 250_000_000L;

    /*
     * Reset a shooter's timeline after one second without shots
     */
    private static final long RESET_AFTER_IDLE_NANOS =
            1_000_000_000L;

    /*
     * Wait this long after the last buffer increase before
     * starting to reduce the buffer
     */
    private static final long BUFFER_STABLE_TIME_NANOS =
            2_000_000_000L;

    /*
     * Reduce the buffer by at most 1 millisecond per received shot.
     *
     * At 900 RPM this gives approximately 15 milliseconds
     * of reduction per second
     */
    private static final long BUFFER_DECREASE_STEP_NANOS =
            1_000_000L;

    /*
     * Keep pending shots at least this far in the future
     * when reducing the buffer
     */
    private static final long MIN_PENDING_LEAD_NANOS =
            5_000_000L;

    /*
     * Used if a non-monotonic timestamp reaches the client
     */
    private static final long MIN_SOURCE_INTERVAL_NANOS =
            1_000_000L;

    private static final int MAX_PENDING_SHOTS = 512;

    private static final boolean DEBUG_GUNSHOT_TIMING = false;

    // дистанции шмали
    private static final double TIER_STEREO_CLOSE = 7.0;
    private static final double TIER_CLOSE = 20.0;
    private static final double TIER_MEDIUM = 80.0;
    private static final double TIER_FAR = 120.0;
    private static final double TIER_VERY_FAR = 220.0;

    // дистанции тихой шмали
    private static final double SILENCED_TIER_STEREO_CLOSE = 7.0;
    private static final double SILENCED_TIER_CLOSE = 20.0;
    private static final double SILENCED_TIER_MEDIUM = 80.0;

    // outdoor volume
    private static final float VOL_STEREO_CLOSE_OUTDOOR = 1.00f;
    private static final float VOL_CLOSE_OUTDOOR        = 1.00f;
    private static final float VOL_MEDIUM_OUTDOOR       = 3.1f;
    private static final float VOL_FAR_OUTDOOR          = 2.75f;
    private static final float VOL_VERY_FAR_OUTDOOR     = 2.55f;

    // indoor volume
    private static final float VOL_STEREO_CLOSE_INDOOR  = 1.00f;
    private static final float VOL_CLOSE_INDOOR         = 1.0f;
    private static final float VOL_MEDIUM_INDOOR        = 4.0f;
    private static final float VOL_FAR_INDOOR           = 6.0f;

    // Silenced volume outdoor
    private static final float SILENCED_VOL_STEREO_CLOSE_OUTDOOR = 1.00f;
    private static final float SILENCED_VOL_CLOSE_OUTDOOR        = 1.0f;
    private static final float SILENCED_VOL_MEDIUM_OUTDOOR       = 1.2f;

    // Silenced volume indoor
    private static final float SILENCED_VOL_STEREO_CLOSE_INDOOR  = 1.10f;
    private static final float SILENCED_VOL_CLOSE_INDOOR         = 1.10f;
    private static final float SILENCED_VOL_MEDIUM_INDOOR        = 1.50f;

    /*
     * All shooters share one playback queue.
     *
     * The queue contains local playback deadlines, not
     * timestamps taken from different client machines
     */
    private static final PriorityQueue<QueuedShot> PENDING_SHOTS =
            new PriorityQueue<>(
                    Comparator.comparingLong(shot -> shot.playAtNanos)
            );

    /*
     * Each shooter has an independent timestamp timeline
     */
    private static final Map<Integer, ShooterTimeline> SHOOTER_TIMELINES =
            new HashMap<>();

    private static World queueWorld;

    /*
     * Per-shooter timeline.
     *
     * sourceOrigin:
     *     Timestamp of the first shot on the shooter's client.
     *
     * receiveOrigin:
     *     Local time when the receiving client got that shot.
     *
     * playbackOrigin:
     *     Local playback timeline. It can move forward when
     *     a packet arrives after its scheduled deadline
     */
    private static final class ShooterTimeline {

        private final World world;

        private final long sourceOrigin;
        private final long receiveOrigin;

        private long playbackOrigin;

        /*
         * Last effective source timestamp.
         *
         * This value is kept monotonic even if an invalid
         * timestamp somehow reaches this handler
         */
        private long lastSourceTimestamp;

        private long lastPacketReceiveNanos;

        private long adaptiveBufferNanos =
                INITIAL_BUFFER_NANOS;

        /*
         * Time of the last buffer increase
         */
        private long lastBufferIncreaseNanos;

        /*
         * Diagnostics
         */
        private long lastSourceIntervalNanos = -1L;
        private long lastReceiveIntervalNanos = -1L;

        private long lastDispatchedSourceTimestamp;
        private long lastDispatchedClientNanos;

        private boolean hasDispatched;

        private ShooterTimeline(
                World world,
                long sourceTimestamp,
                long receiveTimestamp
        ) {

            this.world = world;

            this.sourceOrigin = sourceTimestamp;
            this.receiveOrigin = receiveTimestamp;

            this.playbackOrigin = receiveTimestamp;

            this.lastSourceTimestamp = sourceTimestamp;
            this.lastPacketReceiveNanos = receiveTimestamp;

            this.lastBufferIncreaseNanos = receiveTimestamp;
        }
    }

    private static final class QueuedShot {

        private final GunshotSoundMessage message;
        private final int shooterId;
        private final long enqueueNanos;

        private long playAtNanos;

        private QueuedShot(
                GunshotSoundMessage message,
                int shooterId,
                long playAtNanos,
                long enqueueNanos
        ) {

            this.message = message;
            this.shooterId = shooterId;
            this.playAtNanos = playAtNanos;
            this.enqueueNanos = enqueueNanos;
        }
    }

    /*
     * Called by GunshotSoundMessageHandler.
     *
     * packetReceiveNanos is captured on the Netty thread
     * before NetworkUtil transfers execution to the client thread
     */
    public static void enqueue(
            GunshotSoundMessage msg,
            long packetReceiveNanos
    ) {

        Minecraft mc = Minecraft.getMinecraft();

        if (mc.player == null || mc.world == null) {
            return;
        }

        if (queueWorld != mc.world) {

            clearQueue();

            queueWorld = mc.world;
        }

        long enqueueNanos = System.nanoTime();

        int shooterId = msg.getShooterEntityId();

        long playAt = calculatePlaybackTime(
                mc,
                msg,
                packetReceiveNanos,
                enqueueNanos
        );

        if (DEBUG_GUNSHOT_TIMING && msg.hasTimestamp()) {

            ShooterTimeline timeline =
                    SHOOTER_TIMELINES.get(shooterId);

            long sourceInterval = timeline == null
                    ? -1L
                    : timeline.lastSourceIntervalNanos;

            long receiveInterval = timeline == null
                    ? -1L
                    : timeline.lastReceiveIntervalNanos;

            long dispatchDelay = enqueueNanos - packetReceiveNanos;

            long plannedWait = playAt - enqueueNanos;

            long buffer = timeline == null
                    ? INITIAL_BUFFER_NANOS
                    : timeline.adaptiveBufferNanos;

            LOGGER.info(
                    "[GunshotTiming][CLIENT-QUEUE] shooter={} clientShotDtMs={} packetDtMs={} nettyToClientMs={} plannedWaitMs={} bufferMs={}",
                    shooterId,
                    formatMs(sourceInterval),
                    formatMs(receiveInterval),
                    formatMs(dispatchDelay),
                    formatMs(plannedWait),
                    formatMs(buffer)
            );
        }

        if (PENDING_SHOTS.size() >= MAX_PENDING_SHOTS) {

            QueuedShot dropped = PENDING_SHOTS.poll();

            if (DEBUG_GUNSHOT_TIMING && dropped != null) {

                LOGGER.warn(
                        "[GunshotTiming] Pending queue overflow. Dropped shot from shooter={}",
                        dropped.shooterId
                );
            }
        }

        PENDING_SHOTS.add(
                new QueuedShot(
                        msg,
                        shooterId,
                        playAt,
                        enqueueNanos
                )
        );
    }

    /*
     * Calculate playback time using the original
     * client-side shot timestamp
     */
    private static long calculatePlaybackTime(
            Minecraft mc,
            GunshotSoundMessage msg,
            long receiveTime,
            long enqueueTime
    ) {

        /*
         * Old packet without timestamp:
         * play immediately
         */
        if (!msg.hasTimestamp()) {
            return enqueueTime;
        }

        int shooterId = msg.getShooterEntityId();

        long sourceTimestamp =
                msg.getClientShotTimestampNanos();

        ShooterTimeline timeline =
                SHOOTER_TIMELINES.get(shooterId);

        /*
         * Reset only when:
         * this is the first shot;
         * the world changed;
         * the shooter was idle for more than one second.
         *
         * There is intentionally no maximum timeline duration
         */
        boolean resetTimeline =
                timeline == null
                        || timeline.world != mc.world
                        || receiveTime - timeline.lastPacketReceiveNanos
                        > RESET_AFTER_IDLE_NANOS;

        if (resetTimeline) {

            timeline = resetTimeline(
                    mc.world,
                    shooterId,
                    sourceTimestamp,
                    receiveTime
            );
        }

        /*
         * Keep the effective timestamp monotonic.
         *
         * The server validates timestamps before forwarding them.
         * This is an additional client-side safeguard against
         * duplicate or out-of-order timestamps.
         *
         * Importantly, an invalid timestamp does NOT reset
         * the timeline or remove pending shots
         */
        long effectiveSourceTimestamp = sourceTimestamp;

        if (effectiveSourceTimestamp <= timeline.lastSourceTimestamp) {

            long receiveInterval =
                    receiveTime - timeline.lastPacketReceiveNanos;

            if (receiveInterval < MIN_SOURCE_INTERVAL_NANOS) {
                receiveInterval = MIN_SOURCE_INTERVAL_NANOS;
            }

            effectiveSourceTimestamp =
                    timeline.lastSourceTimestamp + receiveInterval;
        }

        /*
         * Preserve the intervals from the shooter's clock
         */
        long sourceOffset =
                effectiveSourceTimestamp - timeline.sourceOrigin;

        /*
         * Delivery drift relative to the first packet
         */
        long clientOffset =
                receiveTime - timeline.receiveOrigin;

        long relativeTransit =
                clientOffset - sourceOffset;

        /*
         * Save diagnostic intervals before updating the timeline
         */
        timeline.lastSourceIntervalNanos =
                effectiveSourceTimestamp - timeline.lastSourceTimestamp;

        timeline.lastReceiveIntervalNanos =
                receiveTime - timeline.lastPacketReceiveNanos;

        /*
         * Adapt the jitter buffer for this shooter only
         */
        adaptBuffer(
                shooterId,
                timeline,
                relativeTransit,
                enqueueTime
        );

        timeline.lastSourceTimestamp = effectiveSourceTimestamp;
        timeline.lastPacketReceiveNanos = receiveTime;

        /*
         * Reconstruct the playback deadline
         */
        long scheduledTime =
                timeline.playbackOrigin
                        + sourceOffset
                        + timeline.adaptiveBufferNanos;

        /*
         * A packet cannot be played before it arrives.
         *
         * If it arrived too late, rebase this shooter's playback
         * timeline and move all of their pending events equally.
         * This prevents subsequent shots from being compressed
         */
        if (scheduledTime < receiveTime) {

            long lateness =
                    receiveTime - scheduledTime;

            timeline.playbackOrigin += lateness;

            shiftPendingShots(
                    shooterId,
                    lateness,
                    enqueueTime
            );

            scheduledTime = receiveTime;

            if (DEBUG_GUNSHOT_TIMING) {

                LOGGER.info(
                        "[GunshotTiming][REBASE] shooter={} lateByMs={}",
                        shooterId,
                        formatMs(lateness)
                );
            }
        }

        return scheduledTime;
    }

    /*
     * Create a new timeline for one shooter
     */
    private static ShooterTimeline resetTimeline(
            World world,
            int shooterId,
            long sourceTimestamp,
            long receiveTimestamp
    ) {

        removePendingShots(shooterId);

        ShooterTimeline timeline =
                new ShooterTimeline(
                        world,
                        sourceTimestamp,
                        receiveTimestamp
                );

        SHOOTER_TIMELINES.put(
                shooterId,
                timeline
        );

        if (DEBUG_GUNSHOT_TIMING) {

            LOGGER.info(
                    "[GunshotTiming][TIMELINE] Reset shooter={} buffer={} ms",
                    shooterId,
                    formatMs(INITIAL_BUFFER_NANOS)
            );
        }

        return timeline;
    }

    /*
     * Adapt the jitter buffer for one shooter.
     *
     * Increases are immediate.
     * Decreases are gradual and happen only after a stable period
     */
    private static void adaptBuffer(
            int shooterId,
            ShooterTimeline timeline,
            long relativeTransit,
            long nowNanos
    ) {

        /*
         * Calculate the buffer currently required by network delivery
         */
        long requiredBuffer = INITIAL_BUFFER_NANOS;

        if (relativeTransit > 0L) {

            if (relativeTransit >
                    MAX_BUFFER_NANOS - BUFFER_MARGIN_NANOS) {

                requiredBuffer = MAX_BUFFER_NANOS;

            } else {

                requiredBuffer =
                        relativeTransit + BUFFER_MARGIN_NANOS;

                if (requiredBuffer < INITIAL_BUFFER_NANOS) {
                    requiredBuffer = INITIAL_BUFFER_NANOS;
                }
            }
        }

        if (requiredBuffer > MAX_BUFFER_NANOS) {
            requiredBuffer = MAX_BUFFER_NANOS;
        }

        /*
         * Increase immediately when network delivery requires
         * a larger buffer
         */
        if (requiredBuffer > timeline.adaptiveBufferNanos) {

            long increase =
                    requiredBuffer - timeline.adaptiveBufferNanos;

            timeline.adaptiveBufferNanos = requiredBuffer;

            timeline.lastBufferIncreaseNanos = nowNanos;

            /*
             * Move pending shots forward by the same amount
             */
            shiftPendingShots(
                    shooterId,
                    increase,
                    nowNanos
            );

            if (DEBUG_GUNSHOT_TIMING) {

                LOGGER.info(
                        "[GunshotTiming][BUFFER] shooter={} increasedToMs={}",
                        shooterId,
                        formatMs(timeline.adaptiveBufferNanos)
                );
            }

            return;
        }

        /*
         * No reduction is required if the current buffer
         * is already at or below the required value
         */
        if (requiredBuffer >= timeline.adaptiveBufferNanos) {
            return;
        }

        /*
         * Wait until the network has been stable for two seconds
         * after the last buffer increase
         */
        if (nowNanos - timeline.lastBufferIncreaseNanos
                < BUFFER_STABLE_TIME_NANOS) {
            return;
        }

        /*
         * Reduce by at most 1 millisecond per received shot
         */
        long requestedDecrease = Math.min(
                timeline.adaptiveBufferNanos - requiredBuffer,
                BUFFER_DECREASE_STEP_NANOS
        );

        if (requestedDecrease <= 0L) {
            return;
        }

        /*
         * Shift already queued shots backwards by the exact
         * amount by which the buffer is reduced.
         *
         * The shift is limited if a pending shot is too close
         * to its playback deadline
         */
        long appliedShift = shiftPendingShots(
                shooterId,
                -requestedDecrease,
                nowNanos
        );

        if (appliedShift < 0L) {

            timeline.adaptiveBufferNanos += appliedShift;

            if (DEBUG_GUNSHOT_TIMING) {

                LOGGER.info(
                        "[GunshotTiming][BUFFER] shooter={} decreasedToMs={}",
                        shooterId,
                        formatMs(timeline.adaptiveBufferNanos)
                );
            }
        }
    }

    /*
     * Shift pending events for one shooter.
     *
     * Positive shift:
     *     Move pending shots later.
     *
     * Negative shift:
     *     Move pending shots earlier.
     *
     * Other shooters use independent timelines and are not affected.
     *
     * Returns the actual applied shift
     */
    private static long shiftPendingShots(
            int shooterId,
            long requestedShiftNanos,
            long nowNanos
    ) {

        if (requestedShiftNanos == 0L) {
            return 0L;
        }

        if (PENDING_SHOTS.isEmpty()) {
            return requestedShiftNanos;
        }

        long appliedShift = requestedShiftNanos;

        /*
         * When moving shots earlier, make sure that no pending
         * shot is pushed too close to its playback deadline
         */
        if (requestedShiftNanos < 0L) {

            long earliestPendingShot = Long.MAX_VALUE;

            for (QueuedShot shot : PENDING_SHOTS) {

                if (shot.shooterId == shooterId
                        && shot.playAtNanos < earliestPendingShot) {

                    earliestPendingShot = shot.playAtNanos;
                }
            }

            if (earliestPendingShot != Long.MAX_VALUE) {

                long availableDecrease =
                        earliestPendingShot
                                - nowNanos
                                - MIN_PENDING_LEAD_NANOS;

                if (availableDecrease <= 0L) {
                    return 0L;
                }

                long requestedDecrease = -requestedShiftNanos;

                long allowedDecrease = Math.min(
                        requestedDecrease,
                        availableDecrease
                );

                appliedShift = -allowedDecrease;
            }
        }

        if (appliedShift == 0L) {
            return 0L;
        }

        List<QueuedShot> shots =
                new ArrayList<>(PENDING_SHOTS.size());

        while (!PENDING_SHOTS.isEmpty()) {

            QueuedShot shot = PENDING_SHOTS.poll();

            if (shot.shooterId == shooterId) {
                shot.playAtNanos += appliedShift;
            }

            shots.add(shot);
        }

        PENDING_SHOTS.addAll(shots);

        return appliedShift;
    }

    /*
     * Remove pending events for one shooter
     */
    private static void removePendingShots(int shooterId) {

        if (PENDING_SHOTS.isEmpty()) {
            return;
        }

        List<QueuedShot> shots =
                new ArrayList<>(PENDING_SHOTS.size());

        while (!PENDING_SHOTS.isEmpty()) {

            QueuedShot shot = PENDING_SHOTS.poll();

            if (shot.shooterId != shooterId) {
                shots.add(shot);
            }
        }

        PENDING_SHOTS.addAll(shots);
    }

    /*
     * Process pending events on the client render thread
     */
    // TODO: пересмотреть такой подход
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {

        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        if (mc.player == null || mc.world == null) {

            clearQueue();
            return;
        }

        if (queueWorld != mc.world) {

            clearQueue();

            queueWorld = mc.world;

            return;
        }

        long now = System.nanoTime();

        while (!PENDING_SHOTS.isEmpty()) {

            QueuedShot next = PENDING_SHOTS.peek();

            if (next == null || next.playAtNanos - now > 0L) {
                break;
            }

            PENDING_SHOTS.poll();

            long dispatchNanos = System.nanoTime();

            if (DEBUG_GUNSHOT_TIMING && next.message.hasTimestamp()) {

                ShooterTimeline timeline =
                        SHOOTER_TIMELINES.get(next.shooterId);

                if (timeline != null) {

                    long sourceTimestamp =
                            next.message.getClientShotTimestampNanos();

                    long sourceInterval = timeline.hasDispatched
                            ? sourceTimestamp - timeline.lastDispatchedSourceTimestamp
                            : -1L;

                    long playbackInterval = timeline.hasDispatched
                            ? dispatchNanos - timeline.lastDispatchedClientNanos
                            : -1L;

                    long lateness = Math.max(
                            0L,
                            dispatchNanos - next.playAtNanos
                    );

                    long queueResidence =
                            dispatchNanos - next.enqueueNanos;

                    LOGGER.info(
                            "[GunshotTiming][CLIENT-DISPATCH] shooter={} clientShotDtMs={} playbackDtMs={} latenessMs={} queueResidenceMs={} bufferMs={}",
                            next.shooterId,
                            formatMs(sourceInterval),
                            formatMs(playbackInterval),
                            formatMs(lateness),
                            formatMs(queueResidence),
                            formatMs(timeline.adaptiveBufferNanos)
                    );

                    timeline.lastDispatchedSourceTimestamp =
                            sourceTimestamp;

                    timeline.lastDispatchedClientNanos =
                            dispatchNanos;

                    timeline.hasDispatched = true;
                }
            }

            playShot(
                    mc,
                    next.message
            );
        }
    }

    /*
     * Clear all pending events and timelines
     */
    private static void clearQueue() {

        PENDING_SHOTS.clear();

        SHOOTER_TIMELINES.clear();

        queueWorld = null;
    }

    private static String formatMs(long nanos) {

        if (nanos < 0L) {
            return "first";
        }

        return String.format(
                java.util.Locale.ROOT,
                "%.3f",
                nanos / 1_000_000.0
        );
    }

    /*
     * Calculate distance from listener's eye position
     * to shooter's sound position
     */
    private static double getListenerDistance(
            Minecraft mc,
            GunshotSoundMessage msg
    ) {

        double dx = mc.player.posX - msg.getX();

        double listenerEyeY =
                mc.player.posY + mc.player.getEyeHeight();

        double dy = listenerEyeY - msg.getY();

        double dz = mc.player.posZ - msg.getZ();

        return Math.sqrt(
                dx * dx
                        + dy * dy
                        + dz * dz
        );
    }

    /*
     * Play muzzle flash and sound
     */
    private static void playShot(
            Minecraft mc,
            GunshotSoundMessage msg
    ) {

        if (msg.isShowMuzzleFlash()
                && msg.getShooterEntityId() != mc.player.getEntityId()) {

            ClientEventHandler.uploadFlash(
                    msg.getShooterEntityId()
            );
        }

        Item item = Item.getItemById(msg.getWeaponId());

        if (!(item instanceof Weapon)) {
            return;
        }

        Weapon weapon = (Weapon) item;

        double dist = getListenerDistance(mc, msg);

        boolean indoor = msg.isShooterIndoor();

        SoundEvent sound = null;
        String selectedSlot = null;

        float volume;

        if (msg.isSilenced()) {
            if (dist > SILENCED_TIER_MEDIUM) {
                return;
            }

            if (indoor) {

                if (dist <= SILENCED_TIER_STEREO_CLOSE) {

                    selectedSlot = "stereoCloseSilencedIndoorSound";
                    sound = weapon.getStereoCloseSilencedIndoorSound();

                } else if (dist <= SILENCED_TIER_CLOSE) {

                    selectedSlot = "closeSilencedIndoorSound";
                    sound = weapon.getCloseSilencedIndoorSound();

                } else {

                    selectedSlot = "mediumSilencedIndoorSound";
                    sound = weapon.getMediumSilencedIndoorSound();
                }

            } else {

                if (dist <= SILENCED_TIER_STEREO_CLOSE) {

                    selectedSlot = "stereoCloseSilencedOutdoorSound";
                    sound = weapon.getStereoCloseSilencedOutdoorSound();

                } else if (dist <= SILENCED_TIER_CLOSE) {

                    selectedSlot = "closeSilencedOutdoorSound";
                    sound = weapon.getCloseSilencedOutdoorSound();

                } else {

                    selectedSlot = "mediumSilencedOutdoorSound";
                    sound = weapon.getMediumSilencedOutdoorSound();
                }
            }
            if (sound == null) {

                if (selectedSlot != null
                        && weapon.builder.isSoundDisabled(selectedSlot)) {
                    return;
                }

                sound = msg.isBurst()
                        && weapon.builder.isOneClickBurstAllowed
                        ? weapon.getSilencedBurstShootSound()
                        : weapon.getSilencedShootSound();
            }

            volume = msg.getBaseVolume()
                    * silencedVolumeForDist(dist, indoor);

        } else {
            if (indoor) {

                if (dist <= TIER_STEREO_CLOSE) {

                    selectedSlot = "stereoCloseIndoorSound";
                    sound = weapon.getStereoCloseIndoorSound();

                } else if (dist <= TIER_CLOSE) {

                    selectedSlot = "closeIndoorSound";
                    sound = weapon.getCloseIndoorSound();

                } else if (dist <= TIER_MEDIUM) {

                    selectedSlot = "mediumIndoorSound";
                    sound = weapon.getMediumIndoorSound();

                } else if (dist <= TIER_FAR) {

                    selectedSlot = "farIndoorSound";
                    sound = weapon.getFarIndoorSound();
                }

            } else {

                if (dist <= TIER_STEREO_CLOSE) {

                    selectedSlot = "stereoCloseOutdoorSound";
                    sound = weapon.getStereoCloseOutdoorSound();

                } else if (dist <= TIER_CLOSE) {

                    selectedSlot = "closeOutdoorSound";
                    sound = weapon.getCloseOutdoorSound();

                } else if (dist <= TIER_MEDIUM) {

                    selectedSlot = "mediumOutdoorSound";
                    sound = weapon.getMediumOutdoorSound();

                } else if (dist <= TIER_FAR) {

                    selectedSlot = "farOutdoorSound";
                    sound = weapon.getFarOutdoorSound();

                } else if (dist <= TIER_VERY_FAR) {

                    selectedSlot = "veryFarOutdoorSound";
                    sound = weapon.getVeryFarOutdoorSound();
                }
            }
            if (sound == null) {

                if (selectedSlot != null
                        && weapon.builder.isSoundDisabled(selectedSlot)) {
                    return;
                }

                sound = msg.isBurst()
                        && weapon.builder.isOneClickBurstAllowed
                        ? weapon.getBurstShootSound()
                        : weapon.getShootSound();
            }

            volume = msg.getBaseVolume()
                    * volumeForDist(dist, indoor);
        }

        if (sound == null) {
            return;
        }

        float pitch = msg.getPitch();

        if (indoor) {
            mc.player.world.playSound(
                    mc.player,
                    msg.getX(),
                    msg.getY(),
                    msg.getZ(),
                    sound,
                    SoundCategory.PLAYERS,
                    volume,
                    pitch
            );

        } else {

            // positional sound without vanilla attenuation.
            mc.getSoundHandler().playSound(
                    new NonAttenuatedPositionedSound(
                            sound,
                            SoundCategory.PLAYERS,
                            volume,
                            pitch,
                            (float) msg.getX(),
                            (float) msg.getY(),
                            (float) msg.getZ()
                    )
            );
        }
    }

    private static float volumeForDist(double dist, boolean indoor) {

        if (indoor) {
            if (dist <= TIER_STEREO_CLOSE) {
                return VOL_STEREO_CLOSE_INDOOR;
            }

            if (dist <= TIER_CLOSE) {
                return VOL_CLOSE_INDOOR;
            }

            if (dist <= TIER_MEDIUM) {
                return VOL_MEDIUM_INDOOR;
            }

            return VOL_FAR_INDOOR;
        }

        if (dist <= TIER_STEREO_CLOSE) {
            return VOL_STEREO_CLOSE_OUTDOOR;
        }

        if (dist <= TIER_CLOSE) {
            return VOL_CLOSE_OUTDOOR;
        }

        if (dist <= TIER_MEDIUM) {
            return VOL_MEDIUM_OUTDOOR;
        }

        if (dist <= TIER_FAR) {
            return VOL_FAR_OUTDOOR;
        }

        return VOL_VERY_FAR_OUTDOOR;
    }

    private static float silencedVolumeForDist(double dist, boolean indoor) {

        if (indoor) {

            if (dist <= SILENCED_TIER_STEREO_CLOSE) {
                return SILENCED_VOL_STEREO_CLOSE_INDOOR;
            }

            if (dist <= SILENCED_TIER_CLOSE) {
                return SILENCED_VOL_CLOSE_INDOOR;
            }

            return SILENCED_VOL_MEDIUM_INDOOR;
        }

        if (dist <= SILENCED_TIER_STEREO_CLOSE) {
            return SILENCED_VOL_STEREO_CLOSE_OUTDOOR;
        }

        if (dist <= SILENCED_TIER_CLOSE) {
            return SILENCED_VOL_CLOSE_OUTDOOR;
        }

        return SILENCED_VOL_MEDIUM_OUTDOOR;
    }
}
