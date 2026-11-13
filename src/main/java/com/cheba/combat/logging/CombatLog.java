package com.cheba.combat.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CombatLog {

    private static final Logger LOGGER = LogManager.getLogger("MWC-Combat");

    private static volatile boolean debugEnabled;
    private static volatile boolean traceEnabled;

    private CombatLog() {
    }

    public static void info(String component, String method, String message) {
        LOGGER.info("[MWC Combat][{}#{}] {}", component, method, message);
    }

    public static void debug(String component, String method, String message) {
        if (debugEnabled) {
            LOGGER.debug("[MWC Combat][{}#{}] {}", component, method, message);
        }
    }

    public static void trace(String component, String method, String message) {
        if (traceEnabled) {
            LOGGER.trace("[MWC Combat][{}#{}] {}", component, method, message);
        }
    }

    public static void warn(String component, String method, String message) {
        LOGGER.warn("[MWC Combat][{}#{}] {}", component, method, message);
    }

    public static void error(
            String component,
            String method,
            String message,
            Throwable throwable
    ) {
        LOGGER.error(
                "[MWC Combat][{}#{}] {}",
                component,
                method,
                message,
                throwable
        );
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public static void setTraceEnabled(boolean enabled) {
        traceEnabled = enabled;
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static boolean isTraceEnabled() {
        return traceEnabled;
    }
}
