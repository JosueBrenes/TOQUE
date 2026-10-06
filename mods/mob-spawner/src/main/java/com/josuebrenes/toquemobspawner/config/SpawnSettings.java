package com.josuebrenes.toquemobspawner.config;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    public Zone zoneAt(RegistryKey<World> world, int x, int y, int z) {
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
    public Zone zoneFor(RegistryKey<World> world, double x, double y, double z) {
        Zone inside = zoneAt(world, MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
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
