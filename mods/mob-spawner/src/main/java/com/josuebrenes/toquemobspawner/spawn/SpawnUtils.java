package com.josuebrenes.toquemobspawner.spawn;

import com.josuebrenes.toquemobspawner.config.SpawnSettings;
import com.josuebrenes.toquemobspawner.config.Zone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import org.jetbrains.annotations.Nullable;

/**
 * Finding a spot for one extra mob and putting it there.
 *
 * <p>A zone either spawns around the player, in a ring between the minimum and
 * maximum spawn distance, or, with {@code spawnInside}, anywhere inside its own
 * box: a room, an arena, a pit. A box with {@code spawnInAir} puts mobs in
 * mid-air to fall, as in a drop shaft: the mob's ground rule and its registered
 * spawn predicate (which also wants ground) are skipped, and darkness is checked
 * directly unless {@code ignoreLight} is set.
 *
 * <p>The work is bounded: at most {@code maxSpawnAttempts} random columns, and in
 * each only {@link #VERTICAL_SCAN} blocks are looked at. Nothing ever sweeps an
 * area.
 *
 * <p>A position has to pass the same rules vanilla's natural spawner applies, in
 * the same order: loaded and ticking, inside the world border, far enough from
 * every player, the mob's own placement rule (on the ground, in water, in lava),
 * room for its hitbox, the mob's registered spawn predicate (which is where light
 * level and difficulty are checked for hostiles), and finally the mob's own
 * {@code canSpawn} checks. Biome spawn lists are deliberately not consulted: the
 * zone's list decides which mobs appear.
 */
public final class SpawnUtils {
    /** How far up or down from the player a column starts looking. */
    private static final int VERTICAL_SPREAD = 12;

    /** Blocks looked at downward in one column for something to stand on. */
    private static final int VERTICAL_SCAN = 10;

    private SpawnUtils() {
    }

    public enum Outcome { SPAWNED, NO_POSITION, NOT_A_MOB }

    public static Outcome trySpawn(ServerLevel world, ServerPlayer player, Zone zone,
                                   EntityType<?> type, SpawnSettings settings) {
        if (type.getCategory() == MobCategory.MONSTER && world.getDifficulty() == Difficulty.PEACEFUL) {
            return Outcome.NO_POSITION;
        }
        RandomSource random = world.getRandom();
        for (int attempt = 0; attempt < settings.maxSpawnAttempts(); attempt++) {
            BlockPos pos = findPosition(world, player, zone, type, settings, random);
            if (pos == null) {
                continue;
            }
            Entity entity = type.create(world, EntitySpawnReason.NATURAL);
            if (entity == null) {
                return Outcome.NO_POSITION;
            }
            if (!(entity instanceof Mob mob)) {
                // Never added to the world, so there is nothing to clean up.
                return Outcome.NOT_A_MOB;
            }
            mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    random.nextFloat() * 360.0F, 0.0F);
            // canSpawn(world, reason) weighs the ground under the mob, which a
            // mob placed in mid-air does not have.
            if ((!zone.spawnInAir() && !mob.checkSpawnRules(world, EntitySpawnReason.NATURAL)) || !mob.checkSpawnObstruction(world)) {
                continue;
            }
            mob.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
            // Persistent mobs are left out of vanilla's mob cap, so ours add to the
            // natural spawns instead of taking their place. They are despawned by
            // SpawnManager instead of by vanilla.
            mob.setPersistenceRequired();
            mob.addTag(ExtraMobTracker.TAG);
            world.addFreshEntityWithPassengers(mob);
            return Outcome.SPAWNED;
        }
        return Outcome.NO_POSITION;
    }

    @Nullable
    private static BlockPos findPosition(ServerLevel world, ServerPlayer player, Zone zone,
                                         EntityType<?> type, SpawnSettings settings, RandomSource random) {
        int bottom = world.getMinY() + 1;
        int top = world.getMaxY() - 1;
        int x;
        int z;
        int startY;
        if (zone.spawnInside()) {
            bottom = Math.max(bottom, zone.yMin());
            top = Math.min(top, zone.yMax());
            if (bottom > top) {
                return null;
            }
            x = Mth.nextInt(random, zone.xMin(), zone.xMax());
            z = Mth.nextInt(random, zone.zMin(), zone.zMax());
            startY = Mth.nextInt(random, bottom, top);
        } else {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Mth.nextDouble(random, zone.minPlayerDistance(),
                    Math.max(zone.minPlayerDistance() + 1, settings.maxSpawnDistance()));
            x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            startY = Mth.clamp(player.getBlockY() + Mth.nextInt(random, -VERTICAL_SPREAD, VERTICAL_SPREAD),
                    bottom, top);
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, startY, z);
        if (!world.isPositionEntityTicking(cursor) || !world.getWorldBorder().isWithinBounds(cursor)) {
            return null;
        }
        if (zone.spawnInAir()) {
            BlockPos pos = cursor.immutable();
            return isValidInAir(world, pos, zone, type, random) ? pos : null;
        }

        SpawnPlacementType location = SpawnPlacements.getPlacementType(type);
        // A mob nobody registered a placement rule for would otherwise be allowed
        // to appear in mid-air, so it is held to the ground rule.
        SpawnPlacementType placement = location == SpawnPlacementTypes.NO_RESTRICTIONS ? SpawnPlacementTypes.ON_GROUND : location;

        for (int step = 0; step < VERTICAL_SCAN && cursor.getY() >= bottom; step++, cursor.move(0, -1, 0)) {
            if (!placement.isSpawnPositionOk(world, cursor, type)) {
                continue;
            }
            BlockPos pos = cursor.immutable();
            return isValid(world, pos, zone, type, location, random) ? pos : null;
        }
        return null;
    }

    private static boolean isValidInAir(ServerLevel world, BlockPos pos, Zone zone, EntityType<?> type,
                                        RandomSource random) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        if (world.hasNearbyAlivePlayer(x, pos.getY(), z, zone.minPlayerDistance())) {
            return false;
        }
        if (!world.getFluidState(pos).isEmpty() || !world.noCollision(type.getSpawnAABB(x, pos.getY(), z))) {
            return false;
        }
        return zone.ignoreLight()
                || type.getCategory() != MobCategory.MONSTER
                || Monster.isDarkEnoughToSpawn(world, pos, random);
    }

    private static boolean isValid(ServerLevel world, BlockPos pos, Zone zone, EntityType<?> type,
                                   SpawnPlacementType location, RandomSource random) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        if (world.hasNearbyAlivePlayer(x, pos.getY(), z, zone.minPlayerDistance())) {
            return false;
        }
        if (!world.noCollision(type.getSpawnAABB(x, pos.getY(), z))) {
            return false;
        }
        if (zone.ignoreLight() && zone.spawnInside()) {
            return true;
        }
        if (!SpawnPlacements.checkSpawnRules(type, world, EntitySpawnReason.NATURAL, pos, random)) {
            return false;
        }
        // An unregistered monster from another mod has no light rule of its own;
        // give it the one every vanilla hostile uses.
        return location != SpawnPlacementTypes.NO_RESTRICTIONS
                || type.getCategory() != MobCategory.MONSTER
                || Monster.isDarkEnoughToSpawn(world, pos, random);
    }
}
