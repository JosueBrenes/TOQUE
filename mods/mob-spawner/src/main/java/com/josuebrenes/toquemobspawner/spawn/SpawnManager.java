package com.josuebrenes.toquemobspawner.spawn;

import com.josuebrenes.toquemobspawner.Log;
import com.josuebrenes.toquemobspawner.config.ConfigManager;
import com.josuebrenes.toquemobspawner.config.SpawnSettings;
import com.josuebrenes.toquemobspawner.config.Zone;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides, once every {@code spawnCheckIntervalTicks}, how many extra mobs each
 * player in a zone should get, and spawns them.
 *
 * <p>The number aimed for near a player is the zone's {@code targetPerPlayer},
 * {@code baseMobsPerPlayer × (spawnMultiplier − 1)}: on top of what vanilla keeps
 * around them, so the total is roughly the multiplier times normal. Our mobs
 * already near the player count against it, which is what stops players standing
 * together from multiplying the spawns: two players share the mobs between them.
 * On top of that, three hard limits are checked before every single spawn: how
 * many a player gets per check, the zone's {@code maxExtraMobs}, and
 * {@code globalMaxExtraMobs}.
 *
 * <p>Ours are persistent so they stay out of vanilla's mob cap, which means vanilla
 * will not despawn them; this class does instead, removing any with no player
 * within {@code despawnDistance}.
 */
public final class SpawnManager {
    private final ConfigManager config;
    private final ExtraMobTracker tracker;

    private int ticksUntilCheck;
    /** The zone each online player was last seen in, for the log and /toquemobs status. */
    private final Map<UUID, String> playerZones = new HashMap<>();
    /** Configured types that turned out not to be mobs; skipped until the next reload. */
    private final Set<EntityType<?>> rejected = new HashSet<>();

    public SpawnManager(ConfigManager config, ExtraMobTracker tracker) {
        this.config = config;
        this.tracker = tracker;
    }

    public ExtraMobTracker tracker() {
        return tracker;
    }

    /** The zone a player was in at the last check, or null. */
    public String zoneOf(UUID player) {
        return playerZones.get(player);
    }

    public void onReload() {
        rejected.clear();
        ticksUntilCheck = 0;
    }

    public void onServerStopped() {
        tracker.clear();
        playerZones.clear();
        rejected.clear();
    }

    public void onServerTick(MinecraftServer server) {
        if (--ticksUntilCheck > 0) {
            return;
        }
        SpawnSettings settings = config.settings();
        ticksUntilCheck = settings.spawnCheckIntervalTicks();

        updatePlayerZones(server, settings);
        tracker.removeIf(mob -> mob.getWorld() instanceof ServerWorld world
                && !world.isPlayerInRange(mob.getX(), mob.getY(), mob.getZ(), settings.despawnDistance()));

        if (!settings.enabled() || settings.zones().isEmpty()) {
            return;
        }
        Map<String, Integer> zoneCounts = tracker.countByZone(settings.zones());
        for (ServerWorld world : server.getWorlds()) {
            if (world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) {
                spawnIn(world, settings, zoneCounts);
            }
        }
    }

    private void spawnIn(ServerWorld world, SpawnSettings settings, Map<String, Integer> zoneCounts) {
        List<ServerPlayerEntity> players = new ArrayList<>(world.getPlayers());
        // Shuffled so that, when a cap is nearly full, the same player is not
        // always the one who gets the last mobs.
        Collections.shuffle(players, new java.util.Random(world.getRandom().nextLong()));

        Set<String> filledBoxes = new HashSet<>();
        for (ServerPlayerEntity player : players) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            Zone zone = settings.zoneFor(world.getRegistryKey(), player.getX(), player.getY(), player.getZ());
            if (zone == null || !zone.isActive()) {
                continue;
            }

            int wanted;
            if (zone.spawnInside()) {
                // A box is filled once per check whoever is in it, and its target
                // is for the box, so a crowd inside does not multiply it.
                if (!filledBoxes.add(zone.name())) {
                    continue;
                }
                wanted = zone.targetPerPlayer() - zoneCounts.getOrDefault(zone.name(), 0);
            } else {
                wanted = zone.targetPerPlayer() - tracker.countNear(world,
                        player.getX(), player.getY(), player.getZ(), settings.maxSpawnDistance());
            }
            wanted = Math.min(wanted, zone.spawnsPerCheck());
            for (int i = 0; i < wanted; i++) {
                int inZone = zoneCounts.getOrDefault(zone.name(), 0);
                if (inZone >= zone.maxExtraMobs() || tracker.size() >= settings.globalMaxExtraMobs()) {
                    break;
                }
                EntityType<?> type = pick(zone, world);
                if (type == null) {
                    break;
                }
                SpawnUtils.Outcome outcome = SpawnUtils.trySpawn(world, player, zone, type, settings);
                if (outcome == SpawnUtils.Outcome.SPAWNED) {
                    zoneCounts.put(zone.name(), inZone + 1);
                } else if (outcome == SpawnUtils.Outcome.NOT_A_MOB) {
                    rejected.add(type);
                    Log.warn("Entity {} in zone '{}' is not a mob and cannot be spawned; skipping it until the next reload.",
                            Registries.ENTITY_TYPE.getId(type), zone.name());
                }
            }
        }
    }

    private EntityType<?> pick(Zone zone, ServerWorld world) {
        List<EntityType<?>> usable = zone.mobs();
        if (!rejected.isEmpty()) {
            usable = usable.stream().filter(type -> !rejected.contains(type)).toList();
        }
        return usable.isEmpty() ? null : usable.get(world.getRandom().nextInt(usable.size()));
    }

    /** Logs players entering and leaving zones, once per change rather than per check. */
    private void updatePlayerZones(MinecraftServer server, SpawnSettings settings) {
        Set<UUID> online = new HashSet<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID id = player.getUuid();
            online.add(id);
            Zone zone = settings.zoneFor(player.getWorld().getRegistryKey(),
                    player.getX(), player.getY(), player.getZ());
            String now = zone == null ? null : zone.name();
            String before = playerZones.get(id);
            if (now != null && !now.equals(before)) {
                Log.info("Player {} entered zone: {}", player.getGameProfile().getName(), now);
                playerZones.put(id, now);
            } else if (now == null && before != null) {
                Log.info("Player {} left zone: {}", player.getGameProfile().getName(), before);
                playerZones.remove(id);
            }
        }
        playerZones.keySet().retainAll(online);
    }
}
