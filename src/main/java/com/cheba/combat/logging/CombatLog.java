package com.cheba.combat.logging;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CombatLog {

    private static final Logger LOGGER =
            LogManager.getLogger("MWC-Combat");

    private static final Object FILE_LOCK = new Object();

    private static final Map<String, FileState> FILES =
            new HashMap<>();

    private static final Set<String> FAILED_SIDES =
            new HashSet<>();

    private static final long MAX_FILE_SIZE =
            8L * 1024L * 1024L;

    private static final int BACKUP_COUNT = 3;

    private static volatile boolean infoEnabled = true;
    private static volatile boolean debugEnabled = true;
    private static volatile boolean traceEnabled = true;

    private static volatile String sessionId =
            UUID.randomUUID().toString().substring(0, 8);

    private CombatLog() {
    }

    public static void info(
            String component,
            String method,
            String message
    ) {
        if (infoEnabled) {
            log("INFO", component, method, message, null);
        }
    }

    public static void debug(
            String component,
            String method,
            String message
    ) {
        if (debugEnabled) {
            log("DEBUG", component, method, message, null);
        }
    }

    public static void trace(
            String component,
            String method,
            String message
    ) {
        if (traceEnabled) {
            log("TRACE", component, method, message, null);
        }
    }

    public static void warn(
            String component,
            String method,
            String message
    ) {
        log("WARN", component, method, message, null);
    }

    public static void error(
            String component,
            String method,
            String message,
            Throwable throwable
    ) {
        log("ERROR", component, method, message, throwable);
    }

    private static void log(
            String level,
            String component,
            String method,
            String message,
            Throwable throwable
    ) {
        String safeComponent = component == null
                ? "Unknown"
                : component;

        String safeMethod = method == null
                ? "Unknown"
                : method;

        String safeMessage = sanitize(message);

        if (throwable != null) {
            StringWriter stackTrace = new StringWriter();
            throwable.printStackTrace(new PrintWriter(stackTrace));

            safeMessage += " | Exception="
                    + sanitize(stackTrace.toString());
        }

        String line = Instant.now()
                + " | nano=" + System.nanoTime()
                + " | level=" + level
                + " | session=" + sessionId
                + " | [MWC Combat]["
                + safeComponent + "#" + safeMethod + "] "
                + safeMessage;

        switch (level) {
            case "INFO":
                LOGGER.info("[MWC Combat][{}#{}] {}",
                        safeComponent, safeMethod, safeMessage);
                break;

            case "DEBUG":
                LOGGER.debug("[MWC Combat][{}#{}] {}",
                        safeComponent, safeMethod, safeMessage);
                break;

            case "TRACE":
                LOGGER.trace("[MWC Combat][{}#{}] {}",
                        safeComponent, safeMethod, safeMessage);
                break;

            case "WARN":
                LOGGER.warn("[MWC Combat][{}#{}] {}",
                        safeComponent, safeMethod, safeMessage);
                break;

            case "ERROR":
                LOGGER.error("[MWC Combat][{}#{}] {}",
                        safeComponent, safeMethod, safeMessage,
                        throwable);
                break;

            default:
                break;
        }

        writeFile(line);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String getSideName() {
        try {
            Side side = FMLCommonHandler.instance().getEffectiveSide();

            if (side != null) {
                return side.isClient() ? "client" : "server";
            }
        } catch (Exception exception) {
            LOGGER.error(
                    "[MWC Combat] Could not determine effective side",
                    exception
            );
        }

        return "unknown";
    }

    private static void writeFile(String line) {
        String side = getSideName();

        synchronized (FILE_LOCK) {
            if (FAILED_SIDES.contains(side)) {
                return;
            }

            try {
                FileState state = FILES.get(side);

                if (state == null) {
                    state = openFile(side);
                    FILES.put(side, state);
                }

                byte[] encoded = (line + System.lineSeparator())
                        .getBytes(StandardCharsets.UTF_8);

                if (state.sizeBytes + encoded.length > MAX_FILE_SIZE
                        && state.sizeBytes > 0L) {
                    rotate(state);
                }

                state.writer.write(line);
                state.writer.newLine();
                state.writer.flush();

                state.sizeBytes += encoded.length;

            } catch (Exception exception) {
                FAILED_SIDES.add(side);

                FileState state = FILES.remove(side);

                if (state != null) {
                    closeQuietly(state.writer);
                }

                LOGGER.error(
                        "[MWC Combat] File logging disabled for side="
                                + side,
                        exception
                );
            }
        }
    }

    private static FileState openFile(String side)
            throws IOException {

        Path configDirectory =
                Loader.instance().getConfigDir().toPath();

        Path gameDirectory = configDirectory.getParent();

        if (gameDirectory == null) {
            gameDirectory = configDirectory;
        }

        Path directory = gameDirectory
                .resolve("logs")
                .resolve("mwc");

        Files.createDirectories(directory);

        Path file = directory.resolve(
                "combat-" + side + ".log"
        );

        long size = Files.exists(file)
                ? Files.size(file)
                : 0L;

        BufferedWriter writer = Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );

        return new FileState(file, writer, size);
    }

    private static void rotate(FileState state)
            throws IOException {

        state.writer.close();
        state.writer = null;

        for (int i = BACKUP_COUNT - 1; i >= 1; i--) {
            Path source = backupPath(state.path, i);
            Path target = backupPath(state.path, i + 1);

            if (Files.exists(source)) {
                Files.move(
                        source,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        }

        if (Files.exists(state.path)) {
            Files.move(
                    state.path,
                    backupPath(state.path, 1),
                    StandardCopyOption.REPLACE_EXISTING
            );
        }

        state.writer = Files.newBufferedWriter(
                state.path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );

        state.sizeBytes = 0L;
    }

    private static Path backupPath(Path file, int index) {
        return file.resolveSibling(
                file.getFileName().toString() + "." + index
        );
    }

    private static void closeQuietly(BufferedWriter writer) {
        if (writer == null) {
            return;
        }

        try {
            writer.close();
        } catch (IOException ignored) {
        }
    }

    public static void close() {
        synchronized (FILE_LOCK) {
            for (FileState state : FILES.values()) {
                closeQuietly(state.writer);
            }

            FILES.clear();
        }
    }

    public static void setInfoEnabled(boolean enabled) {
        infoEnabled = enabled;
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public static void setTraceEnabled(boolean enabled) {
        traceEnabled = enabled;
    }

    public static boolean isInfoEnabled() {
        return infoEnabled;
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static boolean isTraceEnabled() {
        return traceEnabled;
    }

    public static String getLevelName() {
        if (traceEnabled) {
            return "TRACE";
        }

        if (debugEnabled) {
            return "DEBUG";
        }

        return infoEnabled ? "INFO" : "OFF";
    }

    public static String getSessionId() {
        return sessionId;
    }

    public static boolean setSessionId(String value) {
        if (value == null || value.isEmpty() || value.length() > 32) {
            return false;
        }

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

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

        sessionId = value;
        return true;
    }

    private static final class FileState {

        private final Path path;

        private BufferedWriter writer;

        private long sizeBytes;

        private FileState(
                Path path,
                BufferedWriter writer,
                long sizeBytes
        ) {
            this.path = path;
            this.writer = writer;
            this.sizeBytes = sizeBytes;
        }
    }
}
