package com.josuebrenes.toquemobspawner.config;

import java.util.ArrayList;
import java.util.List;

/**
 * The JSON file, field for field, exactly as an operator writes it.
 *
 * <p>Nothing here is trusted: {@link ConfigManager} clamps every number and
 * resolves every identifier into a {@link SpawnSettings} before the spawner sees
 * it. A field missing from the file keeps the default written here.
 */
public final class ModConfig {
    public boolean enabled = true;

    /** Extra mobs alive at once across the whole server, every zone together. */
    public int globalMaxExtraMobs = 150;

    /** How often players are checked and mobs are spawned. 20 ticks is one second. */
    public int spawnCheckIntervalTicks = 20;

    /**
     * Roughly how many hostile mobs vanilla keeps around one player. A zone with
     * multiplier M aims for (M - 1) times this many extra mobs near each player.
     */
    public int baseMobsPerPlayer = 8;

    /** Spawned per player per check at most, so a zone fills over seconds, not at once. */
    public int maxSpawnsPerPlayerPerCheck = 4;

    /** No extra mob appears closer than this to any player. Vanilla uses 24. */
    public int minSpawnDistance = 24;

    /** Extra mobs appear at most this far from the player they are spawned for. */
    public int maxSpawnDistance = 48;

    /** Positions tried per mob before giving up until the next check. */
    public int maxSpawnAttempts = 12;

    /** An extra mob with no player within this many blocks is removed. */
    public int despawnDistance = 64;

    public List<ZoneConfig> zones = new ArrayList<>();

    public static final class ZoneConfig {
        public String name;
        public boolean enabled = true;
        public String dimension = "minecraft:overworld";
        public int xMin;
        public int xMax;
        public int zMin;
        public int zMax;
        /** Optional. Without them the zone is a column from bedrock to the sky. */
        public Integer yMin;
        public Integer yMax;
        /**
         * False: mobs appear around each player in the zone, 24 to 48 blocks away.
         * True: mobs appear anywhere inside the zone's own box, for a room or an
         * arena; the box is filled once, however many players are in it.
         */
        public boolean spawnInside;
        /** Optional. Closest an extra mob may appear to a player. */
        public Integer minPlayerDistance;
        /** Optional. Mobs spawned per check, per player or per filled box. */
        public Integer spawnsPerCheck;
        /**
         * Box zones only. Mobs appear in mid-air anywhere in the box and fall,
         * for a drop shaft. No ground is needed under them.
         */
        public boolean spawnInAir;
        /** Box zones only. Hostiles appear even where it is lit. */
        public boolean ignoreLight;
        /**
         * Box zones only. Also active while a player is within this many blocks of
         * the box, not only inside it: for a shaft the player waits beneath.
         */
        public Integer activationDistance;
        public double spawnMultiplier = 1.0;
        public int maxExtraMobs = 40;
        public List<String> mobs = new ArrayList<>();

        public static ZoneConfig of(String name, int xMin, int xMax, int zMin, int zMax,
                                    double multiplier, int maxExtraMobs) {
            ZoneConfig zone = new ZoneConfig();
            zone.name = name;
            zone.xMin = xMin;
            zone.xMax = xMax;
            zone.zMin = zMin;
            zone.zMax = zMax;
            zone.spawnMultiplier = multiplier;
            zone.maxExtraMobs = maxExtraMobs;
            zone.mobs = new ArrayList<>(List.of(
                    "minecraft:zombie",
                    "minecraft:skeleton",
                    "minecraft:creeper",
                    "minecraft:spider"));
            return zone;
        }
    }

    /** What is written the first time the server starts without a config file. */
    static ModConfig defaults() {
        ModConfig config = new ModConfig();
        config.zones.add(ZoneConfig.of("zona_1", -500, 500, -500, 500, 5.0, 60));
        config.zones.add(ZoneConfig.of("zona_2", 501, 1500, -500, 500, 10.0, 80));
        return config;
    }
}
