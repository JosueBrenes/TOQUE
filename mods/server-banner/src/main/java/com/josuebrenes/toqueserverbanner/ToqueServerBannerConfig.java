package com.josuebrenes.toqueserverbanner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ServerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides which entry in the multiplayer list is the TOQUE server.
 *
 * <p>The address is the primary test, because the display name is whatever the
 * player typed when they added the server and they can rename it at any time.
 * Names are kept as a fallback, since a tunnelled address changes whenever the
 * tunnel is recreated and the banner should not quietly disappear when it does.
 *
 * <p>Both lists live in {@code config/toque-server-banner.json} so they can be
 * changed without rebuilding the mod.
 */
public final class ToqueServerBannerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("TOQUE");
    private static final String FILE_NAME = "toque-server-banner.json";

    private static final List<String> DEFAULT_ADDRESSES = List.of("ivan-fda.tun.ply.gg");
    private static final List<String> DEFAULT_NAMES = List.of("toque");

    private static volatile Set<String> addresses = Set.copyOf(DEFAULT_ADDRESSES);
    private static volatile Set<String> names = Set.copyOf(DEFAULT_NAMES);

    /** Entries already reported, so the diagnostics do not repeat every frame. */
    private static final Set<String> reported = ConcurrentHashMap.newKeySet();

    private ToqueServerBannerConfig() {
    }

    public static Set<String> addresses() {
        return addresses;
    }

    public static Set<String> names() {
        return names;
    }

    /** Reads the config, writing a default one the first time the mod runs. */
    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            writeDefault(file);
        } else {
            read(file);
        }
        LOGGER.info("[TOQUE] Matching addresses {} and names {}.", addresses, names);
    }

    private static void read(Path file) {
        try {
            JsonObject root = JsonParser.parseString(
                    Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            addresses = readList(root, "addresses", DEFAULT_ADDRESSES, true);
            names = readList(root, "names", DEFAULT_NAMES, false);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[TOQUE] Could not read {} ({}). Using the defaults.", file, e.toString());
        }
    }

    private static Set<String> readList(JsonObject root, String key, List<String> fallback,
                                        boolean stripPort) {
        if (!root.has(key) || !root.get(key).isJsonArray()) {
            return Set.copyOf(fallback);
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonElement element : root.getAsJsonArray(key)) {
            String value = stripPort ? normalise(element.getAsString())
                    : element.getAsString().trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values.isEmpty() ? Set.copyOf(fallback) : Set.copyOf(values);
    }

    /**
     * True when this list entry points at a TOQUE server.
     *
     * <p>The first time each distinct entry is tested the result is logged, so a
     * banner that does not show up can be traced to the address it actually has
     * rather than guessed at.
     */
    public static boolean matches(ServerData server) {
        if (server == null) {
            return false;
        }
        String address = normalise(server.ip);
        String name = server.name == null ? "" : server.name.trim().toLowerCase(Locale.ROOT);
        boolean matched = matchesAddress(address) || matchesName(name);

        String key = name + "\u0000" + address;
        if (reported.add(key)) {
            LOGGER.info("[TOQUE] Server list entry name='{}' address='{}' -> banner {}.",
                    server.name, server.ip, matched ? "ON" : "off");
        }
        return matched;
    }

    private static boolean matchesAddress(String address) {
        if (address.isEmpty()) {
            return false;
        }
        for (String known : addresses) {
            if (address.equals(known) || address.endsWith("." + known)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (String known : names) {
            if (name.contains(known)) {
                return true;
            }
        }
        return false;
    }

    /** Lower cases, trims, and drops the port so "HOST:25565" matches "host". */
    private static String normalise(String address) {
        if (address == null) {
            return "";
        }
        String trimmed = address.trim().toLowerCase(Locale.ROOT);
        int port = trimmed.lastIndexOf(':');
        return port > 0 ? trimmed.substring(0, port) : trimmed;
    }

    private static void writeDefault(Path file) {
        JsonObject root = new JsonObject();
        root.add("addresses", toArray(DEFAULT_ADDRESSES));
        root.add("names", toArray(DEFAULT_NAMES));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
            LOGGER.info("[TOQUE] Wrote a default config to {}.", file);
        } catch (IOException e) {
            LOGGER.warn("[TOQUE] Could not write {} ({}).", file, e.toString());
        }
    }

    private static JsonArray toArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
