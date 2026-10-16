package com.josuebrenes.toquedeathalert.series;

import com.josuebrenes.toquedeathalert.core.ToqueLog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Keeps the Try counter in step with Hardcore World Reset.
 *
 * <p>A Try is one world. Hardcore World Reset exposes no counter of its own, so
 * the overworld seed stands in for the world's identity: a seed we have not seen
 * before means the next Try has started.
 *
 * <p>The check runs on a timer rather than at server start, because the reset
 * happens in the middle of a session. The world is erased and regenerated with a
 * new seed while the server keeps running, so a start-up check would sit frozen
 * on the same number through a whole evening of resets.
 */
public final class TryWatcher {
    /** Roughly once a second; a reset takes several seconds to complete. */
    private static final int CHECK_TICKS = 20;

    private final SeriesStatsRepository stats;
    private int ticks;

    public TryWatcher(SeriesStatsRepository stats) {
        this.stats = stats;
    }

    public void onServerTick(MinecraftServer server) {
        if (++ticks < CHECK_TICKS) {
            return;
        }
        ticks = 0;
        check(server);
    }

    /** Reads the current seed and advances the Try when it has changed. */
    public void check(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        if (stats.advanceTryIfWorldChanged(overworld.getSeed())) {
            ToqueLog.info("New world detected; this is Try #{}.", stats.tryNumber());
        }
    }
}
