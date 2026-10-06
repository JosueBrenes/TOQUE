package com.josuebrenes.toquemobspawner.spawn;

import com.josuebrenes.toquemobspawner.config.SpawnSettings;
import com.josuebrenes.toquemobspawner.config.Zone;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocation;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
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

    public static Outcome trySpawn(ServerWorld world, ServerPlayerEntity player, Zone zone,
                                   EntityType<?> type, SpawnSettings settings) {
        if (type.getSpawnGroup() == SpawnGroup.MONSTER && world.getDifficulty() == Difficulty.PEACEFUL) {
            return Outcome.NO_POSITION;
        }
        Random random = world.getRandom();
        for (int attempt = 0; attempt < settings.maxSpawnAttempts(); attempt++) {
            BlockPos pos = findPosition(world, player, zone, type, settings, random);
            if (pos == null) {
                continue;
            }
            Entity entity = type.create(world);
            if (entity == null) {
                return Outcome.NO_POSITION;
            }
            if (!(entity instanceof MobEntity mob)) {
                // Never added to the world, so there is nothing to clean up.
                return Outcome.NOT_A_MOB;
            }
            mob.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    random.nextFloat() * 360.0F, 0.0F);
            // canSpawn(world, reason) weighs the ground under the mob, which a
            // mob placed in mid-air does not have.
            if ((!zone.spawnInAir() && !mob.canSpawn(world, SpawnReason.NATURAL)) || !mob.canSpawn(world)) {
                continue;
            }
            mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.NATURAL, null);
            // Persistent mobs are left out of vanilla's mob cap, so ours add to the
            // natural spawns instead of taking their place. They are despawned by
            // SpawnManager instead of by vanilla.
            mob.setPersistent();
            mob.addCommandTag(ExtraMobTracker.TAG);
            world.spawnEntityAndPassengers(mob);
            return Outcome.SPAWNED;
        }
        return Outcome.NO_POSITION;
    }

    @Nullable
    private static BlockPos findPosition(ServerWorld world, ServerPlayerEntity player, Zone zone,
                                         EntityType<?> type, SpawnSettings settings, Random random) {
        int bottom = world.getBottomY() + 1;
        int top = world.getTopY() - 2;
        int x;
        int z;
        int startY;
        if (zone.spawnInside()) {
            bottom = Math.max(bottom, zone.yMin());
            top = Math.min(top, zone.yMax());
            if (bottom > top) {
                return null;
            }
            x = MathHelper.nextInt(random, zone.xMin(), zone.xMax());
            z = MathHelper.nextInt(random, zone.zMin(), zone.zMax());
            startY = MathHelper.nextInt(random, bottom, top);
        } else {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = MathHelper.nextDouble(random, zone.minPlayerDistance(),
                    Math.max(zone.minPlayerDistance() + 1, settings.maxSpawnDistance()));
            x = MathHelper.floor(player.getX() + Math.cos(angle) * distance);
            z = MathHelper.floor(player.getZ() + Math.sin(angle) * distance);
            startY = MathHelper.clamp(player.getBlockY() + MathHelper.nextInt(random, -VERTICAL_SPREAD, VERTICAL_SPREAD),
                    bottom, top);
        }

        BlockPos.Mutable cursor = new BlockPos.Mutable(x, startY, z);
        if (!world.shouldTickEntity(cursor) || !world.getWorldBorder().contains(cursor)) {
            return null;
        }
        if (zone.spawnInAir()) {
            BlockPos pos = cursor.toImmutable();
            return isValidInAir(world, pos, zone, type, random) ? pos : null;
        }

        SpawnLocation location = SpawnRestriction.getLocation(type);
        // A mob nobody registered a placement rule for would otherwise be allowed
        // to appear in mid-air, so it is held to the ground rule.
        SpawnLocation placement = location == SpawnLocationTypes.UNRESTRICTED ? SpawnLocationTypes.ON_GROUND : location;

        for (int step = 0; step < VERTICAL_SCAN && cursor.getY() >= bottom; step++, cursor.move(0, -1, 0)) {
            if (!placement.isSpawnPositionOk(world, cursor, type)) {
                continue;
            }
            BlockPos pos = cursor.toImmutable();
            return isValid(world, pos, zone, type, location, random) ? pos : null;
        }
        return null;
    }

    private static boolean isValidInAir(ServerWorld world, BlockPos pos, Zone zone, EntityType<?> type,
                                        Random random) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        if (world.isPlayerInRange(x, pos.getY(), z, zone.minPlayerDistance())) {
            return false;
        }
        if (!world.getFluidState(pos).isEmpty() || !world.isSpaceEmpty(type.getSpawnBox(x, pos.getY(), z))) {
            return false;
        }
        return zone.ignoreLight()
                || type.getSpawnGroup() != SpawnGroup.MONSTER
                || HostileEntity.isSpawnDark(world, pos, random);
    }

    private static boolean isValid(ServerWorld world, BlockPos pos, Zone zone, EntityType<?> type,
                                   SpawnLocation location, Random random) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        if (world.isPlayerInRange(x, pos.getY(), z, zone.minPlayerDistance())) {
            return false;
        }
        if (!world.isSpaceEmpty(type.getSpawnBox(x, pos.getY(), z))) {
            return false;
        }
        if (zone.ignoreLight() && zone.spawnInside()) {
            return true;
        }
        if (!SpawnRestriction.canSpawn(type, world, SpawnReason.NATURAL, pos, random)) {
            return false;
        }
        // An unregistered monster from another mod has no light rule of its own;
        // give it the one every vanilla hostile uses.
        return location != SpawnLocationTypes.UNRESTRICTED
                || type.getSpawnGroup() != SpawnGroup.MONSTER
                || HostileEntity.isSpawnDark(world, pos, random);
    }
}
