package com.josuebrenes.toquemobspawner.config;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.List;

/**
 * One zone after validation: bounds ordered, multiplier sane, every mob
 * identifier resolved against the live entity registry.
 *
 * @param targetPerPlayer   extra mobs the zone tries to keep near each player in it,
 *                          or in the whole box when {@code spawnInside}
 * @param minPlayerDistance closest an extra mob may appear to any player
 * @param spawnsPerCheck    mobs spawned per check, per player or per box
 * @param invalidMobs       configured identifiers that did not resolve, kept for /toquemobs info
 */
public record Zone(String name,
                   boolean enabled,
                   RegistryKey<World> dimension,
                   int xMin, int xMax,
                   int zMin, int zMax,
                   int yMin, int yMax,
                   boolean spawnInside,
                   int minPlayerDistance,
                   int spawnsPerCheck,
                   double spawnMultiplier,
                   int maxExtraMobs,
                   int targetPerPlayer,
                   List<EntityType<?>> mobs,
                   List<String> invalidMobs) {

    /** Stands for "no limit" in {@code yMin} and {@code yMax}. */
    public static final int UNBOUNDED_MIN = Integer.MIN_VALUE;
    public static final int UNBOUNDED_MAX = Integer.MAX_VALUE;

    /** Inclusive on every edge, so 500 belongs to a zone running -500 to 500. */
    public boolean contains(RegistryKey<World> world, int x, int y, int z) {
        return dimension.equals(world)
                && x >= xMin && x <= xMax
                && y >= yMin && y <= yMax
                && z >= zMin && z <= zMax;
    }

    public boolean hasHeightLimits() {
        return yMin != UNBOUNDED_MIN || yMax != UNBOUNDED_MAX;
    }

    /** Whether this zone can spawn anything at all. */
    public boolean isActive() {
        return enabled && targetPerPlayer > 0 && maxExtraMobs > 0 && !mobs.isEmpty();
    }
}
