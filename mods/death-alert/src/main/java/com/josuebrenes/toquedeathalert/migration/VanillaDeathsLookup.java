package com.josuebrenes.toquedeathalert.migration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.josuebrenes.toquedeathalert.core.ToqueLog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Finds how many times a player has died according to vanilla Minecraft.
 *
 * <p>The live statistic is the primary source. It is kept in {@code <world>/stats/<uuid>.json},
 * which means Hardcore World Reset wipes it together with the world on every Try — so when the
 * live value is zero we also look for a stats file left behind in any other world folder still
 * on disk. That is a best-effort rescue, not a second source of truth: it only ever runs once,
 * during the migration.
 */
public final class VanillaDeathsLookup {
    /** How many directory levels below the server root a world folder may sit. */
    private static final int MAX_WORLD_DEPTH = 2;

    /**
     * @param deaths how many deaths were found
     * @param origin where the number came from, for the admin-facing diagnostics
     */
    public record Result(int deaths, String origin) {
    }

    public Result lookup(MinecraftServer server, UUID uuid, ServerPlayer player) {
        int live = player == null ? 0 : player.getStats().getValue(Stats.CUSTOM, Stats.DEATHS);
        if (live > 0) {
            return new Result(live, "live stat handler");
        }

        DiskHit onDisk = searchStatsFiles(server.getServerDirectory(), uuid);
        if (onDisk != null && onDisk.deaths() > live) {
            return new Result(onDisk.deaths(), "stats file " + onDisk.path());
        }
        return new Result(live, live > 0 ? "live stat handler" : "no vanilla deaths found");
    }

    private record DiskHit(int deaths, Path path) {
    }

    /**
     * Looks for {@code <dir>/stats/<uuid>.json} in the server root and in the directories
     * below it, so a world kept as a backup or renamed by a reset is still found.
     */
    private DiskHit searchStatsFiles(Path serverRoot, UUID uuid) {
        DiskHit best = null;
        for (Path candidate : worldCandidates(serverRoot)) {
            Path statsFile = candidate.resolve("stats").resolve(uuid + ".json");
            if (!Files.isRegularFile(statsFile)) {
                continue;
            }
            int deaths = readDeaths(statsFile);
            if (best == null || deaths > best.deaths()) {
                best = new DiskHit(deaths, statsFile);
            }
        }
        return best;
    }

    private Iterable<Path> worldCandidates(Path serverRoot) {
        java.util.List<Path> candidates = new java.util.ArrayList<>();
        collectDirectories(serverRoot, candidates, MAX_WORLD_DEPTH);
        return candidates;
    }

    private void collectDirectories(Path directory, java.util.List<Path> out, int depthLeft) {
        out.add(directory);
        if (depthLeft <= 0) {
            return;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory, Files::isDirectory)) {
            for (Path child : children) {
                String name = child.getFileName().toString();
                // Nothing of interest inside these, and they can be very large.
                if (name.equals("region") || name.equals("entities") || name.equals("poi")
                        || name.equals("libraries") || name.equals("mods") || name.equals("cache")
                        || name.startsWith(".")) {
                    continue;
                }
                collectDirectories(child, out, depthLeft - 1);
            }
        } catch (IOException unreadable) {
            // A directory we cannot list simply holds no candidate for us.
        }
    }

    /** Reads {@code stats -> minecraft:custom -> minecraft:deaths} out of a vanilla stats file. */
    private int readDeaths(Path statsFile) {
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(statsFile, StandardCharsets.UTF_8));
            JsonObject custom = parsed.getAsJsonObject()
                    .getAsJsonObject("stats")
                    .getAsJsonObject("minecraft:custom");
            return custom == null || !custom.has("minecraft:deaths")
                    ? 0
                    : Math.max(0, custom.get("minecraft:deaths").getAsInt());
        } catch (IOException | RuntimeException e) {
            ToqueLog.warn("Could not read vanilla stats file {} ({}).", statsFile, e.toString());
            return 0;
        }
    }
}
