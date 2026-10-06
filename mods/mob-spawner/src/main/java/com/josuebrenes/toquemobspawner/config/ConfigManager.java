package com.josuebrenes.toquemobspawner.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.josuebrenes.toquemobspawner.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Reads {@code config/toque-mob-spawner.json} and turns it into {@link SpawnSettings}.
 *
 * <p>The file lives in the server's config folder, never in the world, so Hardcore
 * World Reset can delete the world and roll a new seed without touching the zones.
 *
 * <p>Loading happens once the server is starting rather than at mod init: mobs
 * from other mods are only certain to be registered by then. A file that cannot
 * be parsed is never overwritten; the settings already running are kept and the
 * error is reported, so a typo during a {@code reload} does not switch the
 * spawner off mid-session.
 */
public final class ConfigManager {
    public static final String FILE_NAME = "toque-mob-spawner.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path file;
    private volatile SpawnSettings settings = SpawnSettings.inactive();

    public ConfigManager() {
        this.file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public SpawnSettings settings() {
        return settings;
    }

    public Path file() {
        return file;
    }

    /** What a load did, for the console and for {@code /toquemobs reload}. */
    public record LoadResult(boolean success, String message, List<String> warnings) {
    }

    public LoadResult load(MinecraftServer server) {
        ModConfig raw;
        try {
            raw = readOrCreate();
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            String message = "Could not read " + file + ": " + exception.getMessage()
                    + ". Keeping the previous settings.";
            Log.error(message);
            return new LoadResult(false, message, List.of());
        }

        List<String> warnings = new ArrayList<>();
        SpawnSettings loaded = resolve(raw, server, warnings);
        warnings.forEach(Log::warn);
        settings = loaded;

        String message = "Loaded " + loaded.zones().size() + " zones"
                + (loaded.enabled() ? "." : " (spawner disabled).");
        Log.info(message);
        return new LoadResult(true, message, warnings);
    }

    /**
     * Switches the spawner on or off and writes the choice back to the file, so
     * it survives a restart. Only the {@code enabled} field is changed.
     */
    public void setEnabled(boolean enabled) {
        settings = settings.withEnabled(enabled);
        try {
            JsonObject json;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
            json.addProperty("enabled", enabled);
            write(json);
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            Log.warn("Could not save enabled={} to {}: {}. It applies until the next reload.",
                    enabled, file, exception.getMessage());
        }
    }

    /**
     * Appends a zone to the file and reloads. Edits the JSON tree rather than
     * rewriting from {@link ModConfig}, so fields this version does not know about
     * survive.
     */
    public LoadResult addZone(MinecraftServer server, ModConfig.ZoneConfig zone) {
        return editZones(server, zones -> {
            for (JsonElement existing : zones) {
                if (existing.isJsonObject() && existing.getAsJsonObject().has("name")
                        && existing.getAsJsonObject().get("name").getAsString().equalsIgnoreCase(zone.name)) {
                    return "A zone named '" + zone.name + "' already exists.";
                }
            }
            zones.add(GSON.toJsonTree(zone));
            return null;
        });
    }

    public LoadResult removeZone(MinecraftServer server, String name) {
        return editZones(server, zones -> {
            for (int i = 0; i < zones.size(); i++) {
                JsonElement existing = zones.get(i);
                if (existing.isJsonObject() && existing.getAsJsonObject().has("name")
                        && existing.getAsJsonObject().get("name").getAsString().equalsIgnoreCase(name)) {
                    zones.remove(i);
                    return null;
                }
            }
            return "No zone named '" + name + "'.";
        });
    }

    /** Applies {@code change} to the file's zone list; it returns an error message or null. */
    private LoadResult editZones(MinecraftServer server, Function<JsonArray, String> change) {
        try {
            readOrCreate();
            JsonObject json;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (!json.has("zones") || !json.get("zones").isJsonArray()) {
                json.add("zones", new JsonArray());
            }
            String error = change.apply(json.getAsJsonArray("zones"));
            if (error != null) {
                return new LoadResult(false, error, List.of());
            }
            write(json);
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            String message = "Could not edit " + file + ": " + exception.getMessage();
            Log.error(message);
            return new LoadResult(false, message, List.of());
        }
        return load(server);
    }

    private ModConfig readOrCreate() throws IOException {
        if (Files.notExists(file)) {
            ModConfig defaults = ModConfig.defaults();
            Files.createDirectories(file.getParent());
            write(defaults);
            Log.info("Created a default config at {}.", file);
            return defaults;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ModConfig config = GSON.fromJson(reader, ModConfig.class);
            if (config == null) {
                throw new JsonParseException("the file is empty");
            }
            return config;
        }
    }

    private void write(Object content) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(content, writer);
        }
    }

    private static SpawnSettings resolve(ModConfig raw, MinecraftServer server, List<String> warnings) {
        int minDistance = clamp("minSpawnDistance", raw.minSpawnDistance, 8, 96, warnings);
        int maxDistance = clamp("maxSpawnDistance", raw.maxSpawnDistance, minDistance + 8, 128, warnings);
        int baseMobs = clamp("baseMobsPerPlayer", raw.baseMobsPerPlayer, 0, 64, warnings);
        int spawnsPerCheck = clamp("maxSpawnsPerPlayerPerCheck", raw.maxSpawnsPerPlayerPerCheck, 1, 32, warnings);

        List<Zone> zones = new ArrayList<>();
        Set<String> names = new HashSet<>();
        List<ModConfig.ZoneConfig> rawZones = raw.zones == null ? List.of() : raw.zones;
        for (int i = 0; i < rawZones.size(); i++) {
            ModConfig.ZoneConfig zone = rawZones.get(i);
            if (zone == null) {
                continue;
            }
            Zone resolved = resolveZone(zone, i, baseMobs, minDistance, spawnsPerCheck, server, warnings);
            if (!names.add(resolved.name().toLowerCase(Locale.ROOT))) {
                warnings.add("Duplicate zone name '" + resolved.name()
                        + "'; /toquemobs info will only find the first.");
            }
            zones.add(resolved);
        }

        return new SpawnSettings(
                raw.enabled,
                clamp("globalMaxExtraMobs", raw.globalMaxExtraMobs, 0, 1000, warnings),
                clamp("spawnCheckIntervalTicks", raw.spawnCheckIntervalTicks, 1, 1200, warnings),
                baseMobs,
                spawnsPerCheck,
                minDistance,
                maxDistance,
                clamp("maxSpawnAttempts", raw.maxSpawnAttempts, 1, 64, warnings),
                clamp("despawnDistance", raw.despawnDistance, maxDistance, 256, warnings),
                List.copyOf(zones));
    }

    private static Zone resolveZone(ModConfig.ZoneConfig raw, int index, int baseMobs,
                                    int globalMinDistance, int globalSpawnsPerCheck,
                                    MinecraftServer server, List<String> warnings) {
        String name = raw.name == null || raw.name.isBlank() ? "zona_" + (index + 1) : raw.name.trim();
        String where = "Zone '" + name + "': ";

        RegistryKey<World> dimension = World.OVERWORLD;
        if (raw.dimension != null && !raw.dimension.isBlank()) {
            Identifier id = Identifier.tryParse(raw.dimension.trim());
            if (id == null) {
                warnings.add(where + "invalid dimension '" + raw.dimension + "', using minecraft:overworld.");
            } else {
                dimension = RegistryKey.of(RegistryKeys.WORLD, id);
                if (server.getWorld(dimension) == null) {
                    warnings.add(where + "dimension " + id + " does not exist on this server; the zone will never match.");
                }
            }
        }

        if (raw.xMin > raw.xMax || raw.zMin > raw.zMax
                || (raw.yMin != null && raw.yMax != null && raw.yMin > raw.yMax)) {
            warnings.add(where + "a minimum was larger than its maximum; they were swapped.");
        }
        int yMin = Zone.UNBOUNDED_MIN;
        int yMax = Zone.UNBOUNDED_MAX;
        if (raw.yMin != null && raw.yMax != null) {
            yMin = Math.min(raw.yMin, raw.yMax);
            yMax = Math.max(raw.yMin, raw.yMax);
        } else if (raw.yMin != null) {
            yMin = raw.yMin;
        } else if (raw.yMax != null) {
            yMax = raw.yMax;
        }
        if (raw.spawnInside && (yMin == Zone.UNBOUNDED_MIN || yMax == Zone.UNBOUNDED_MAX)) {
            warnings.add(where + "spawnInside needs both yMin and yMax; mobs will appear around players instead.");
        }
        boolean inside = raw.spawnInside && yMin != Zone.UNBOUNDED_MIN && yMax != Zone.UNBOUNDED_MAX;
        // Inside a room the players are usually close to every wall, so the
        // vanilla 24 blocks would leave nowhere to spawn.
        int minPlayerDistance = clamp(where + "minPlayerDistance",
                raw.minPlayerDistance != null ? raw.minPlayerDistance : inside ? 4 : globalMinDistance,
                1, 96, warnings);
        int spawnsPerCheck = clamp(where + "spawnsPerCheck",
                raw.spawnsPerCheck != null ? raw.spawnsPerCheck : globalSpawnsPerCheck, 1, 32, warnings);
        if (!inside && (raw.spawnInAir || raw.ignoreLight || raw.activationDistance != null)) {
            warnings.add(where + "spawnInAir, ignoreLight and activationDistance only apply with spawnInside; ignored.");
        }
        int activationDistance = inside && raw.activationDistance != null
                ? clamp(where + "activationDistance", raw.activationDistance, 0, 128, warnings)
                : 0;

        double multiplier = raw.spawnMultiplier;
        if (!Double.isFinite(multiplier) || multiplier < 1.0) {
            warnings.add(where + "spawnMultiplier " + multiplier + " is below 1; the zone adds nothing.");
            multiplier = 1.0;
        } else if (multiplier > 50.0) {
            warnings.add(where + "spawnMultiplier " + multiplier + " capped at 50.");
            multiplier = 50.0;
        }
        int maxExtra = clamp(where + "maxExtraMobs", raw.maxExtraMobs, 0, 1000, warnings);
        int target = Math.min(maxExtra, (int) Math.round(baseMobs * (multiplier - 1.0)));

        List<EntityType<?>> mobs = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (String entry : raw.mobs == null ? List.<String>of() : raw.mobs) {
            EntityType<?> type = resolveEntity(entry);
            if (type == null) {
                invalid.add(String.valueOf(entry));
                warnings.add("Invalid entity configured: " + entry + " (zone '" + name + "'); skipped.");
            } else if (!mobs.contains(type)) {
                mobs.add(type);
            }
        }
        if (mobs.isEmpty() && raw.enabled) {
            warnings.add(where + "no valid mobs; the zone spawns nothing.");
        }

        return new Zone(name, raw.enabled, dimension,
                Math.min(raw.xMin, raw.xMax), Math.max(raw.xMin, raw.xMax),
                Math.min(raw.zMin, raw.zMax), Math.max(raw.zMin, raw.zMax),
                yMin, yMax, inside, minPlayerDistance, spawnsPerCheck,
                inside && raw.spawnInAir, inside && raw.ignoreLight, activationDistance,
                multiplier, maxExtra, target, List.copyOf(mobs), List.copyOf(invalid));
    }

    /** Any registered entity, vanilla or modded; null when the identifier is unknown. */
    private static EntityType<?> resolveEntity(String entry) {
        if (entry == null) {
            return null;
        }
        Identifier id = Identifier.tryParse(entry.trim());
        if (id == null || !Registries.ENTITY_TYPE.containsId(id)) {
            return null;
        }
        return Registries.ENTITY_TYPE.getOrEmpty(id).orElse(null);
    }

    private static int clamp(String field, int value, int min, int max, List<String> warnings) {
        int clamped = MathHelper.clamp(value, min, max);
        if (clamped != value) {
            warnings.add(field + " = " + value + " is out of range; using " + clamped + ".");
        }
        return clamped;
    }
}
