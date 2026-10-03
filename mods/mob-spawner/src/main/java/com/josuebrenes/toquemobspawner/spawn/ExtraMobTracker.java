package com.josuebrenes.toquemobspawner.spawn;

import com.josuebrenes.toquemobspawner.config.Zone;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Every loaded mob that Toque Mob Spawner created, and nothing else.
 *
 * <p>Ours are marked with a command tag, the same tags {@code /tag} manages.
 * Tags are saved with the entity, so a mob is still recognised after its chunk
 * unloads and loads again, or after a restart. Fabric's entity load and unload
 * events keep this set equal to the tagged mobs currently in memory, so counting
 * ours never means scanning the world: the set is bounded by the global cap.
 */
public final class ExtraMobTracker {
    public static final String TAG = "toque_mob_spawner";

    private final Set<MobEntity> mobs = Collections.newSetFromMap(new HashMap<>());

    public static boolean isExtra(Entity entity) {
        return entity instanceof MobEntity && entity.getCommandTags().contains(TAG);
    }

    public void onLoad(Entity entity) {
        if (entity instanceof MobEntity mob && isExtra(mob)) {
            mobs.add(mob);
        }
    }

    public void onUnload(Entity entity) {
        if (entity instanceof MobEntity mob) {
            mobs.remove(mob);
        }
    }

    public void clear() {
        mobs.clear();
    }

    public int size() {
        return mobs.size();
    }

    /** Ours within {@code radius} blocks of a point, in one world. */
    public int countNear(ServerWorld world, double x, double y, double z, double radius) {
        double radiusSquared = radius * radius;
        int count = 0;
        for (MobEntity mob : mobs) {
            if (mob.getWorld() == world && mob.squaredDistanceTo(x, y, z) <= radiusSquared) {
                count++;
            }
        }
        return count;
    }

    /** How many of ours stand in each zone, counted once per check. */
    public Map<String, Integer> countByZone(List<Zone> zones) {
        Map<String, Integer> counts = new HashMap<>();
        for (MobEntity mob : mobs) {
            RegistryKey<World> world = mob.getWorld().getRegistryKey();
            int x = mob.getBlockX();
            int y = mob.getBlockY();
            int z = mob.getBlockZ();
            for (Zone zone : zones) {
                if (zone.contains(world, x, y, z)) {
                    counts.merge(zone.name(), 1, Integer::sum);
                    break;
                }
            }
        }
        return counts;
    }

    /**
     * Removes ours that match {@code shouldGo}. Collected first: discarding fires
     * the unload event, which edits the set being walked.
     */
    public int removeIf(Predicate<MobEntity> shouldGo) {
        List<MobEntity> doomed = new ArrayList<>();
        for (MobEntity mob : mobs) {
            if (mob.isAlive() && shouldGo.test(mob)) {
                doomed.add(mob);
            }
        }
        doomed.forEach(Entity::discard);
        mobs.removeAll(doomed);
        return doomed.size();
    }
}
