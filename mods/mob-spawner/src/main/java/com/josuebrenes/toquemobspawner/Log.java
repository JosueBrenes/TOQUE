package com.josuebrenes.toquemobspawner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's logger. The server's log pattern does not print logger names, so
 * every line carries the prefix itself.
 */
public final class Log {
    private static final Logger LOGGER = LoggerFactory.getLogger("ToqueMobSpawner");
    private static final String PREFIX = "[ToqueMobSpawner] ";

    private Log() {
    }

    public static void info(String message, Object... args) {
        LOGGER.info(PREFIX + message, args);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(PREFIX + message, args);
    }

    public static void error(String message, Object... args) {
        LOGGER.error(PREFIX + message, args);
    }
}
