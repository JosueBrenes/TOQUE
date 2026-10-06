package com.josuebrenes.toquemobspawner.command;

import com.josuebrenes.toquemobspawner.config.ConfigManager;
import com.josuebrenes.toquemobspawner.config.ModConfig;
import com.josuebrenes.toquemobspawner.config.SpawnSettings;
import com.josuebrenes.toquemobspawner.config.Zone;
import com.josuebrenes.toquemobspawner.spawn.ExtraMobTracker;
import com.josuebrenes.toquemobspawner.spawn.SpawnManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code /toquemobs}: reload, status, zones, info, create, delete, enable and
 * disable. Operators only.
 *
 * <p>{@code create} builds a box zone from two corners, so a room can be made a
 * spawn zone from inside the game: look at one corner, press Tab, look at the
 * opposite corner, press Tab.
 */
public final class ToqueMobCommands {
    private static final int OPERATOR = 2;

    /** What /toquemobs create gives a box unless told otherwise. */
    private static final double BOX_MULTIPLIER = 10.0;
    private static final int BOX_MAX_MOBS = 100;
    private static final int BOX_SPAWNS_PER_CHECK = 8;

    private final ConfigManager config;
    private final SpawnManager spawner;

    public ToqueMobCommands(ConfigManager config, SpawnManager spawner) {
        this.config = config;
        this.spawner = spawner;
    }

    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("toquemobs")
                .requires(source -> source.hasPermissionLevel(OPERATOR))
                .then(CommandManager.literal("reload").executes(this::reload))
                .then(CommandManager.literal("status").executes(this::status))
                .then(CommandManager.literal("zones").executes(this::zones))
                .then(CommandManager.literal("info")
                        .then(CommandManager.argument("zona", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        config.settings().zones().stream().map(Zone::name), builder))
                                .executes(this::info)))
                .then(CommandManager.literal("create")
                        .then(CommandManager.argument("nombre", StringArgumentType.word())
                                .then(CommandManager.argument("desde", BlockPosArgumentType.blockPos())
                                        .then(CommandManager.argument("hasta", BlockPosArgumentType.blockPos())
                                                .executes(context -> create(context, BOX_MULTIPLIER))
                                                .then(CommandManager.argument("multiplicador",
                                                                DoubleArgumentType.doubleArg(1.0, 50.0))
                                                        .executes(context -> create(context,
                                                                DoubleArgumentType.getDouble(context, "multiplicador"))))))))
                .then(CommandManager.literal("delete")
                        .then(CommandManager.argument("zona", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        config.settings().zones().stream().map(Zone::name), builder))
                                .executes(this::delete)))
                .then(CommandManager.literal("enable").executes(context -> setEnabled(context, true)))
                .then(CommandManager.literal("disable").executes(context -> setEnabled(context, false))));
    }

    private int reload(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ConfigManager.LoadResult result = config.load(source.getServer());
        spawner.onReload();
        source.sendFeedback(() -> Text.literal(result.message())
                .formatted(result.success() ? Formatting.GREEN : Formatting.RED), true);
        for (String warning : result.warnings()) {
            source.sendFeedback(() -> Text.literal("⚠ " + warning).formatted(Formatting.YELLOW), false);
        }
        return result.success() ? 1 : 0;
    }

    private int status(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        SpawnSettings settings = config.settings();
        ExtraMobTracker tracker = spawner.tracker();

        send(source, header("Toque Mob Spawner"));
        send(source, line("Estado", settings.enabled()
                ? Text.literal("activado").formatted(Formatting.GREEN)
                : Text.literal("desactivado").formatted(Formatting.RED)));
        send(source, line("Mobs extra cargados", value(tracker.size() + " / " + settings.globalMaxExtraMobs())));
        send(source, line("Revisión cada", value(settings.spawnCheckIntervalTicks() + " ticks")));

        Map<String, Integer> counts = tracker.countByZone(settings.zones());
        for (Zone zone : settings.zones()) {
            send(source, line("Zona " + zone.name(), value(counts.getOrDefault(zone.name(), 0)
                    + " / " + zone.maxExtraMobs() + (zone.enabled() ? "" : " (desactivada)"))));
        }

        int inZones = 0;
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            String zone = spawner.zoneOf(player.getUuid());
            if (zone == null) {
                continue;
            }
            inZones++;
            int near = tracker.countNear(player.getServerWorld(), player.getX(), player.getY(), player.getZ(),
                    settings.maxSpawnDistance());
            send(source, line(player.getGameProfile().getName(),
                    value(zone + " · " + near + " extra cerca")));
        }
        if (inZones == 0) {
            send(source, Text.literal("Ningún jugador está dentro de una zona.").formatted(Formatting.GRAY));
        }
        return 1;
    }

    private int zones(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        SpawnSettings settings = config.settings();
        send(source, header("Zonas (" + settings.zones().size() + ")"));
        if (settings.zones().isEmpty()) {
            send(source, Text.literal("No hay zonas en " + config.file()).formatted(Formatting.GRAY));
        }
        for (Zone zone : settings.zones()) {
            send(source, Text.literal("▪ ").formatted(Formatting.GRAY)
                    .append(Text.literal(zone.name()).formatted(zone.enabled() ? Formatting.WHITE : Formatting.DARK_GRAY))
                    .append(Text.literal(" " + bounds(zone) + " · " + zone.spawnMultiplier() + "x · "
                            + zone.mobs().size() + " mobs").formatted(Formatting.GRAY)));
        }
        return settings.zones().size();
    }

    private int info(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String name = StringArgumentType.getString(context, "zona");
        Zone zone = config.settings().zoneNamed(name);
        if (zone == null) {
            source.sendError(Text.literal("No existe la zona '" + name + "'. Usa /toquemobs zones."));
            return 0;
        }

        int count = spawner.tracker().countByZone(config.settings().zones()).getOrDefault(zone.name(), 0);
        send(source, header("Zona " + zone.name()));
        send(source, line("Activa", value(zone.enabled() ? "sí" : "no")));
        send(source, line("Dimensión", value(zone.dimension().getValue().toString())));
        send(source, line("Coordenadas", value(bounds(zone))));
        send(source, line("Aparecen", value(zone.spawnInAir()
                ? "en el aire dentro de la zona, y caen"
                : zone.spawnInside()
                ? "dentro de la zona"
                : "alrededor del jugador")));
        send(source, line("Distancia mínima al jugador", value(zone.minPlayerDistance() + " bloques")));
        send(source, line("Por revisión", value(zone.spawnsPerCheck() + " mobs")));
        if (zone.activationDistance() > 0) {
            send(source, line("Se activa", value("con un jugador a " + zone.activationDistance() + " bloques o menos")));
        }
        if (zone.ignoreLight()) {
            send(source, line("Luz", value("ignorada")));
        }
        send(source, line("Multiplicador", value(zone.spawnMultiplier() + "x")));
        send(source, line(zone.spawnInside() ? "Objetivo en la zona" : "Extra por jugador",
                value(Integer.toString(zone.targetPerPlayer()))));
        send(source, line("Mobs extra", value(count + " / " + zone.maxExtraMobs())));
        send(source, line("Mobs", value(zone.mobs().isEmpty() ? "ninguno"
                : zone.mobs().stream().map(ToqueMobCommands::id).collect(Collectors.joining(", ")))));
        if (!zone.invalidMobs().isEmpty()) {
            send(source, line("Inválidos", Text.literal(String.join(", ", zone.invalidMobs()))
                    .formatted(Formatting.RED)));
        }
        return 1;
    }

    private int create(CommandContext<ServerCommandSource> context, double multiplier) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        String name = StringArgumentType.getString(context, "nombre");
        BlockPos from = BlockPosArgumentType.getLoadedBlockPos(context, "desde");
        BlockPos to = BlockPosArgumentType.getLoadedBlockPos(context, "hasta");

        ModConfig.ZoneConfig zone = ModConfig.ZoneConfig.of(name,
                Math.min(from.getX(), to.getX()), Math.max(from.getX(), to.getX()),
                Math.min(from.getZ(), to.getZ()), Math.max(from.getZ(), to.getZ()),
                multiplier, BOX_MAX_MOBS);
        zone.yMin = Math.min(from.getY(), to.getY());
        zone.yMax = Math.max(from.getY(), to.getY());
        zone.spawnInside = true;
        zone.spawnsPerCheck = BOX_SPAWNS_PER_CHECK;
        zone.dimension = source.getWorld().getRegistryKey().getValue().toString();

        ConfigManager.LoadResult result = config.addZone(source.getServer(), zone);
        if (!result.success()) {
            source.sendError(Text.literal(result.message()));
            return 0;
        }
        spawner.onReload();
        Zone created = config.settings().zoneNamed(name);
        source.sendFeedback(() -> Text.literal("Zona '" + name + "' creada: " + bounds(created)
                + ". Hasta " + created.targetPerPlayer() + " mobs dentro mientras haya alguien adentro.")
                .formatted(Formatting.GREEN), true);
        source.sendFeedback(() -> Text.literal("Mobs, multiplicador y límites se cambian en "
                + config.file().getFileName() + " y se aplican con /toquemobs reload.")
                .formatted(Formatting.GRAY), false);
        return 1;
    }

    private int delete(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String name = StringArgumentType.getString(context, "zona");
        ConfigManager.LoadResult result = config.removeZone(source.getServer(), name);
        if (!result.success()) {
            source.sendError(Text.literal(result.message()));
            return 0;
        }
        spawner.onReload();
        source.sendFeedback(() -> Text.literal("Zona '" + name + "' borrada. Sus mobs extra se van cuando nadie esté cerca.")
                .formatted(Formatting.YELLOW), true);
        return 1;
    }

    private int setEnabled(CommandContext<ServerCommandSource> context, boolean enabled) {
        config.setEnabled(enabled);
        context.getSource().sendFeedback(() -> Text.literal("Toque Mob Spawner "
                + (enabled ? "activado." : "desactivado. Los mobs extra que ya existen se quedan hasta que se alejen."))
                .formatted(enabled ? Formatting.GREEN : Formatting.YELLOW), true);
        return 1;
    }

    private static String bounds(Zone zone) {
        String bounds = "X " + zone.xMin() + ".." + zone.xMax();
        if (zone.hasHeightLimits()) {
            bounds += ", Y " + (zone.yMin() == Zone.UNBOUNDED_MIN ? "-∞" : zone.yMin())
                    + ".." + (zone.yMax() == Zone.UNBOUNDED_MAX ? "∞" : zone.yMax());
        }
        return bounds + ", Z " + zone.zMin() + ".." + zone.zMax();
    }

    private static String id(EntityType<?> type) {
        return Registries.ENTITY_TYPE.getId(type).toString();
    }

    private static void send(ServerCommandSource source, Text text) {
        source.sendFeedback(() -> text, false);
    }

    private static Text header(String title) {
        return Text.literal("☠ " + title + " ☠").formatted(Formatting.RED, Formatting.BOLD);
    }

    private static Text line(String label, Text value) {
        MutableText text = Text.literal("▪ ").formatted(Formatting.GRAY)
                .append(Text.literal(label + ": ").formatted(Formatting.WHITE));
        return text.append(value);
    }

    private static Text value(String value) {
        return Text.literal(value).formatted(Formatting.GOLD);
    }
}
