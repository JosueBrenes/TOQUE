package com.josuebrenes.toquemobspawner.config;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * The whole configuration after validation. Immutable: a reload builds a new
 * one and swaps it in, so the spawner never sees a half-loaded file.
 */
public record SpawnSettings(boolean enabled,
                            int globalMaxExtraMobs,
                            int spawnCheckIntervalTicks,
                            int baseMobsPerPlayer,
                            int maxSpawnsPerPlayerPerCheck,
                            int minSpawnDistance,
                            int maxSpawnDistance,
                            int maxSpawnAttempts,
                            int despawnDistance,
                            List<Zone> zones) {

    /** Used before the first load and when nothing can be read: spawns nothing. */
    static SpawnSettings inactive() {
        return new SpawnSettings(false, 0, 20, 0, 0, 24, 48, 1, 64, List.of());
    }

    SpawnSettings withEnabled(boolean value) {
        return new SpawnSettings(value, globalMaxExtraMobs, spawnCheckIntervalTicks,
                baseMobsPerPlayer, maxSpawnsPerPlayerPerCheck, minSpawnDistance,
                maxSpawnDistance, maxSpawnAttempts, despawnDistance, zones);
    }

    /** The first enabled zone holding this block. Zones earlier in the file win overlaps. */
    @Nullable
    public Zone zoneAt(ResourceKey<Level> world, int x, int y, int z) {
        for (Zone zone : zones) {
            if (zone.enabled() && zone.contains(world, x, y, z)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * The zone acting on a player here: the one they stand in, else a box zone
     * whose activation distance reaches them.
     */
    @Nullable
    public Zone zoneFor(ResourceKey<Level> world, double x, double y, double z) {
        Zone inside = zoneAt(world, Mth.floor(x), Mth.floor(y), Mth.floor(z));
        if (inside != null) {
            return inside;
        }
        for (Zone zone : zones) {
            if (zone.enabled() && zone.isNear(world, x, y, z)) {
                return zone;
            }
        }
        return null;
    }

    @Nullable
    public Zone zoneNamed(String name) {
        for (Zone zone : zones) {
            if (zone.name().equalsIgnoreCase(name)) {
                return zone;
            }
        }
        return null;
    }
}
