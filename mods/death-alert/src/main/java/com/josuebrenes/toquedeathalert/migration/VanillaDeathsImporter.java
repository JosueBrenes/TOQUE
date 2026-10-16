package com.josuebrenes.toquedeathalert.migration;

import com.josuebrenes.toquedeathalert.core.ToqueLog;
import com.josuebrenes.toquedeathalert.series.PlayerDeathRecord;
import com.josuebrenes.toquedeathalert.series.SeriesStatsRepository;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * One-off seeding of a player's TOQUE counter from their vanilla deaths statistic.
 *
 * <p>This is a migration, never a synchronisation. It runs the first time TOQUE
 * sees a player and the result is written to disk with a {@code migrated} flag, so
 * a server restart, a world change, a new seed or a new Try never import again —
 * and a death already counted by the death listener can never be added twice.
 *
 * <p>After {@code /toque resetDeaths} the import is closed for the whole series,
 * including players who join for the first time afterwards. They start at zero,
 * which is what a brand new series means.
 */
public final class VanillaDeathsImporter {
    private final SeriesStatsRepository stats;
    private final VanillaDeathsLookup lookup;

    public VanillaDeathsImporter(SeriesStatsRepository stats, VanillaDeathsLookup lookup) {
        this.stats = stats;
        this.lookup = lookup;
    }

    public VanillaDeathsLookup lookup() {
        return lookup;
    }

    /** Called once per join; does nothing unless this player has never been migrated. */
    public void migrateOnJoin(MinecraftServer server, ServerPlayer player) {
        String name = player.getGameProfile().name();
        PlayerDeathRecord record = stats.touch(player.getUUID(), name);

        if (record.migrated()) {
            return;
        }
        if (!stats.isVanillaImportOpen()) {
            stats.markMigrated(player.getUUID(), name, record.deaths());
            ToqueLog.info("{} joins a reset series; starting at {} deaths.", name, record.deaths());
            return;
        }

        apply(server, player, record, "join");
    }

    /** Admin-triggered re-import, used to repair a counter that migrated as zero. */
    public int forceImport(MinecraftServer server, ServerPlayer player) {
        PlayerDeathRecord record = stats.touch(player.getUUID(), player.getGameProfile().name());
        return apply(server, player, record, "command");
    }

    private int apply(MinecraftServer server, ServerPlayer player,
                      PlayerDeathRecord record, String trigger) {
        String name = player.getGameProfile().name();
        VanillaDeathsLookup.Result vanilla = lookup.lookup(server, player.getUUID(), player);

        // A death counted by TOQUE also bumped the vanilla statistic, so taking the
        // maximum imports the pre-TOQUE history without ever counting a death twice,
        // and without losing a TOQUE count when the vanilla stats file is already gone.
        int starting = Math.max(record.deaths(), vanilla.deaths());
        stats.markMigrated(player.getUUID(), name, starting);

        ToqueLog.info("Migration ({}) for {}: vanilla={} [{}], toque={} -> starting at {}.",
                trigger, name, vanilla.deaths(), vanilla.origin(), record.deaths(), starting);
        return starting;
    }
}
