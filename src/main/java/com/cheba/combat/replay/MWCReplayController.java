package com.cheba.combat.replay;

import com.cheba.combat.logging.CombatLog;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovementInput;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public final class MWCReplayController {

    private static final Minecraft MC = Minecraft.getMinecraft();

    private static final int MAGIC = 0x4D575250;
    private static final int VERSION = 1;

    private static final int MAX_FRAMES = 20 * 60 * 10;
    private static final int HEADER_BYTES = 40;
    private static final int FRAME_BYTES = 19;
    private static final long MAX_FILE_BYTES =
            HEADER_BYTES + (long) MAX_FRAMES * FRAME_BYTES;

    private static final double START_POSITION_TOLERANCE = 0.75D;

    private static final List<Frame> recordingFrames =
            new ArrayList<>();

    private static boolean recording;
    private static String recordingName;
    private static int recordingDimension;

    private static double recordingStartX;
    private static double recordingStartY;
    private static double recordingStartZ;

    private static boolean playing;
    private static ReplayData playbackData;
    private static int playbackFrameIndex;
    private static long playbackStartNanos;
    private static boolean sprintingBeforePlayback;

    public MWCReplayController() {
    }

    public static boolean startRecording(String name) {
        if (!isValidName(name)) {
            message("Invalid replay name. Use 1-32 letters, digits, _ or -.");
            return false;
        }

        if (recording || playing) {
            message("Another replay operation is already active.");
            return false;
        }

        if (!isPlayerReady()) {
            message("You must be alive and not riding an entity.");
            return false;
        }

        recordingFrames.clear();

        recordingName = name;
        recordingDimension = MC.player.dimension;

        recordingStartX = MC.player.posX;
        recordingStartY = MC.player.posY;
        recordingStartZ = MC.player.posZ;

        recording = true;

        CombatLog.info(
                "MWCReplayController",
                "startRecording",
                "Name=" + name
                        + " | Dimension=" + recordingDimension
                        + " | Position=" + recordingStartX + ","
                        + recordingStartY + "," + recordingStartZ
        );

        message("Recording started: " + name);
        return true;
    }

    public static boolean stopRecording() {
        if (!recording) {
            return false;
        }

        finishRecording("manual stop", true);
        return true;
    }

    public static boolean startPlayback(
            String name,
            long delayMillis
    ) {
        if (!isValidName(name)) {
            message("Invalid replay name.");
            return false;
        }

        if (delayMillis < 0L || delayMillis > 60_000L) {
            message("Delay must be between 0 and 60000 ms.");
            return false;
        }

        if (recording || playing) {
            message("Another replay operation is already active.");
            return false;
        }

        if (!isPlayerReady()) {
            message("You must be alive and not riding an entity.");
            return false;
        }

        ReplayData data;

        try {
            data = loadReplay(name);
        } catch (IOException exception) {
            CombatLog.error(
                    "MWCReplayController",
                    "startPlayback",
                    "Could not load replay: " + name,
                    exception
            );

            message("Could not load replay: " + exception.getMessage());
            return false;
        }

        if (MC.player.dimension != data.dimension) {
            message("Replay belongs to another dimension.");
            return false;
        }

        double distanceSquared = distanceSquared(
                MC.player.posX,
                MC.player.posY,
                MC.player.posZ,
                data.startX,
                data.startY,
                data.startZ
        );

        if (distanceSquared
                > START_POSITION_TOLERANCE
                * START_POSITION_TOLERANCE) {
            message("Wrong starting position. Move to the recording start.");
            return false;
        }

        playbackData = data;
        playbackFrameIndex = 0;
        sprintingBeforePlayback = MC.player.isSprinting();

        playbackStartNanos =
                System.nanoTime() + delayMillis * 1_000_000L;

        playing = true;

        CombatLog.info(
                "MWCReplayController",
                "startPlayback",
                "Name=" + name
                        + " | Frames=" + data.frames.size()
                        + " | DelayMs=" + delayMillis
        );

        message("Playback scheduled: " + name
                + " | Delay=" + delayMillis + " ms"
                + " | Frames=" + data.frames.size());

        return true;
    }

    public static boolean cancelPlayback() {
        if (!playing) {
            return false;
        }

        cancelPlaybackInternal("manual cancel", true);
        return true;
    }

    public static String getStatus() {
        if (recording) {
            return "RECORDING " + recordingName
                    + " | Frames=" + recordingFrames.size()
                    + "/" + MAX_FRAMES;
        }

        if (playing && playbackData != null) {
            long remainingNanos = Math.max(
                    0L,
                    playbackStartNanos - System.nanoTime()
            );

            return "PLAYING " + playbackData.name
                    + " | Frame=" + playbackFrameIndex
                    + "/" + playbackData.frames.size()
                    + " | DelayRemainingMs="
                    + remainingNanos / 1_000_000L;
        }

        return "IDLE";
    }

    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        EntityPlayer player = event.getEntityPlayer();

        if (player != MC.player) {
            return;
        }

        MovementInput input = event.getMovementInput();

        if (recording) {
            if (!isPlayerReady()
                    || player.dimension != recordingDimension) {
                finishRecording("player state changed", true);
                return;
            }

            if (recordingFrames.size() >= MAX_FRAMES) {
                finishRecording("maximum duration reached", true);
                return;
            }

            recordingFrames.add(new Frame(
                    input.moveForward,
                    input.moveStrafe,
                    input.jump,
                    input.sneak,
                    player.isSprinting(),
                    player.rotationYaw,
                    player.rotationPitch
            ));

            return;
        }

        if (!playing || playbackData == null) {
            return;
        }

        if (!isPlayerReady()
                || player.dimension != playbackData.dimension) {
            cancelPlaybackInternal("player state changed", true);
            return;
        }

        if (playbackFrameIndex >= playbackData.frames.size()) {
            finishPlayback();
            return;
        }

        if (System.nanoTime() < playbackStartNanos) {
            input.moveForward = 0.0F;
            input.moveStrafe = 0.0F;
            input.jump = false;
            input.sneak = false;

            player.setSprinting(false);
            return;
        }

        Frame frame = playbackData.frames.get(playbackFrameIndex++);

        input.moveForward = frame.moveForward;
        input.moveStrafe = frame.moveStrafe;
        input.jump = frame.jump;
        input.sneak = frame.sneak;

        player.setSprinting(frame.sprinting);

        player.rotationYaw = frame.yaw;
        player.rotationPitch = frame.pitch;

        player.prevRotationYaw = frame.yaw;
        player.prevRotationPitch = frame.pitch;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (MC.player == null || MC.world == null) {
            if (recording) {
                finishRecording("player or world unavailable", false);
            }

            if (playing) {
                cancelPlaybackInternal(
                        "player or world unavailable",
                        false
                );
            }

            return;
        }

        if (recording
                && (!isPlayerReady()
                || MC.player.dimension != recordingDimension)) {
            finishRecording("player state changed", true);
        }

        if (playing
                && (!isPlayerReady()
                || MC.player.dimension != playbackData.dimension)) {
            cancelPlaybackInternal("player state changed", true);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld() != MC.world) {
            return;
        }

        if (recording) {
            finishRecording("world unload", false);
        }

        if (playing) {
            cancelPlaybackInternal("world unload", false);
        }
    }

    private static void finishRecording(
            String reason,
            boolean notify
    ) {
        if (!recording) {
            return;
        }

        recording = false;

        String name = recordingName;
        List<Frame> frames = new ArrayList<>(recordingFrames);

        recordingName = null;
        recordingFrames.clear();

        if (frames.isEmpty()) {
            if (notify) {
                message("Recording discarded: no frames were captured.");
            }

            CombatLog.warn(
                    "MWCReplayController",
                    "finishRecording",
                    "Empty recording discarded | Name=" + name
                            + " | Reason=" + reason
            );
            return;
        }

        try {
            saveReplay(
                    name,
                    recordingDimension,
                    recordingStartX,
                    recordingStartY,
                    recordingStartZ,
                    frames
            );

            CombatLog.info(
                    "MWCReplayController",
                    "finishRecording",
                    "Saved=" + name
                            + " | Frames=" + frames.size()
                            + " | Reason=" + reason
            );

            if (notify) {
                message("Recording saved: " + name
                        + " | Frames=" + frames.size());
            }

        } catch (IOException exception) {
            CombatLog.error(
                    "MWCReplayController",
                    "finishRecording",
                    "Could not save replay: " + name,
                    exception
            );

            if (notify) {
                message("Could not save replay: " + exception.getMessage());
            }
        }
    }

    private static void finishPlayback() {
        if (!playing) {
            return;
        }

        String name = playbackData == null
                ? "unknown"
                : playbackData.name;

        int totalFrames = playbackData == null
                ? playbackFrameIndex
                : playbackData.frames.size();

        playing = false;
        playbackData = null;
        playbackFrameIndex = 0;

        if (MC.player != null) {
            MC.player.setSprinting(sprintingBeforePlayback);
        }

        CombatLog.info(
                "MWCReplayController",
                "finishPlayback",
                "Completed=" + name
                        + " | Frames=" + totalFrames
        );

        message("Playback completed: " + name);
    }

    private static void cancelPlaybackInternal(
            String reason,
            boolean notify
    ) {
        if (!playing) {
            return;
        }

        String name = playbackData == null
                ? "unknown"
                : playbackData.name;

        int frame = playbackFrameIndex;

        playing = false;
        playbackData = null;
        playbackFrameIndex = 0;

        if (MC.player != null) {
            MC.player.setSprinting(sprintingBeforePlayback);
        }

        CombatLog.warn(
                "MWCReplayController",
                "cancelPlayback",
                "Cancelled=" + name
                        + " | Frame=" + frame
                        + " | Reason=" + reason
        );

        if (notify) {
            message("Playback cancelled: " + reason);
        }
    }

    private static void saveReplay(
            String name,
            int dimension,
            double startX,
            double startY,
            double startZ,
            List<Frame> frames
    ) throws IOException {

        Path directory = getReplayDirectory();
        Files.createDirectories(directory);

        Path target = directory.resolve(name + ".mwr");
        Path temporary = directory.resolve(name + ".mwr.tmp");

        try {
            try (DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(
                            Files.newOutputStream(
                                    temporary,
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING,
                                    StandardOpenOption.WRITE
                            )
                    )
            )) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(dimension);

                output.writeDouble(startX);
                output.writeDouble(startY);
                output.writeDouble(startZ);

                output.writeInt(frames.size());

                for (Frame frame : frames) {
                    output.writeFloat(frame.moveForward);
                    output.writeFloat(frame.moveStrafe);

                    output.writeBoolean(frame.jump);
                    output.writeBoolean(frame.sneak);
                    output.writeBoolean(frame.sprinting);

                    output.writeFloat(frame.yaw);
                    output.writeFloat(frame.pitch);
                }
            }

            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static ReplayData loadReplay(String name)
            throws IOException {

        Path file = getReplayDirectory().resolve(name + ".mwr");

        if (!Files.isRegularFile(file)) {
            throw new IOException("Replay file not found: " + name);
        }

        long fileSize = Files.size(file);

        if (fileSize < HEADER_BYTES + FRAME_BYTES
                || fileSize > MAX_FILE_BYTES) {
            throw new IOException("Invalid replay file size.");
        }

        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file))
        )) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Invalid replay file header.");
            }

            if (input.readInt() != VERSION) {
                throw new IOException("Unsupported replay version.");
            }

            int dimension = input.readInt();

            double startX = input.readDouble();
            double startY = input.readDouble();
            double startZ = input.readDouble();

            if (!isValidPosition(startX)
                    || !isValidPosition(startY)
                    || !isValidPosition(startZ)) {
                throw new IOException("Invalid replay start position.");
            }

            int frameCount = input.readInt();

            if (frameCount < 1 || frameCount > MAX_FRAMES) {
                throw new IOException("Invalid replay frame count.");
            }

            long expectedSize =
                    HEADER_BYTES + (long) frameCount * FRAME_BYTES;

            if (fileSize != expectedSize) {
                throw new IOException("Replay file is incomplete or corrupted.");
            }

            List<Frame> frames = new ArrayList<>(frameCount);

            for (int i = 0; i < frameCount; i++) {
                float moveForward = input.readFloat();
                float moveStrafe = input.readFloat();

                boolean jump = input.readBoolean();
                boolean sneak = input.readBoolean();
                boolean sprinting = input.readBoolean();

                float yaw = input.readFloat();
                float pitch = input.readFloat();

                if (!Float.isFinite(moveForward)
                        || !Float.isFinite(moveStrafe)
                        || Math.abs(moveForward) > 1.0F
                        || Math.abs(moveStrafe) > 1.0F
                        || !Float.isFinite(yaw)
                        || !Float.isFinite(pitch)
                        || pitch < -90.0F
                        || pitch > 90.0F) {
                    throw new IOException(
                            "Invalid replay frame at index " + i
                    );
                }

                frames.add(new Frame(
                        moveForward,
                        moveStrafe,
                        jump,
                        sneak,
                        sprinting,
                        yaw,
                        pitch
                ));
            }

            if (input.read() != -1) {
                throw new IOException("Unexpected replay file data.");
            }

            return new ReplayData(
                    name,
                    dimension,
                    startX,
                    startY,
                    startZ,
                    frames
            );
        }
    }

    private static Path getReplayDirectory() {
        return Loader.instance()
                .getConfigDir()
                .toPath()
                .resolve("mwc")
                .resolve("replays");
    }

    private static boolean isValidName(String name) {
        if (name == null || name.isEmpty() || name.length() > 32) {
            return false;
        }

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            boolean valid =
                    c >= 'a' && c <= 'z'
                            || c >= 'A' && c <= 'Z'
                            || c >= '0' && c <= '9'
                            || c == '_'
                            || c == '-';

            if (!valid) {
                return false;
            }
        }

        return true;
    }

    private static boolean isPlayerReady() {
        return MC.player != null
                && MC.world != null
                && !MC.player.isDead
                && !MC.player.isRiding();
    }

    private static boolean isValidPosition(double value) {
        return !Double.isNaN(value)
                && !Double.isInfinite(value)
                && Math.abs(value) <= 30_000_000.0D;
    }

    private static double distanceSquared(
            double ax, double ay, double az,
            double bx, double by, double bz
    ) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;

        return dx * dx + dy * dy + dz * dz;
    }

    private static void message(String value) {
        if (MC.player != null) {
            MC.player.sendMessage(new TextComponentString(
                    "[MWC Replay] " + value
            ));
        }
    }

    private static final class Frame {

        private final float moveForward;
        private final float moveStrafe;

        private final boolean jump;
        private final boolean sneak;
        private final boolean sprinting;

        private final float yaw;
        private final float pitch;

        private Frame(
                float moveForward,
                float moveStrafe,
                boolean jump,
                boolean sneak,
                boolean sprinting,
                float yaw,
                float pitch
        ) {
            this.moveForward = moveForward;
            this.moveStrafe = moveStrafe;
            this.jump = jump;
            this.sneak = sneak;
            this.sprinting = sprinting;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class ReplayData {

        private final String name;
        private final int dimension;

        private final double startX;
        private final double startY;
        private final double startZ;

        private final List<Frame> frames;

        private ReplayData(
                String name,
                int dimension,
                double startX,
                double startY,
                double startZ,
                List<Frame> frames
        ) {
            this.name = name;
            this.dimension = dimension;
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.frames = frames;
        }
    }
}
