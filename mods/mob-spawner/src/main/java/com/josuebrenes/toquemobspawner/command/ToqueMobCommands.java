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
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.world.entity.EntityType;

/**
 * {@code /toquemobs}: reload, status, zones, info, create, delete, enable and
 * disable. Operators only.
 *
 * <p>{@code create} builds a box zone from two corners, so a room can be made a
 * spawn zone from inside the game: look at one corner, press Tab, look at the
 * opposite corner, press Tab.
 */
public final class ToqueMobCommands {
    private static final PermissionCheck OPERATOR = Commands.LEVEL_GAMEMASTERS;

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

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("toquemobs")
                .requires(Commands.hasPermission(OPERATOR))
                .then(Commands.literal("reload").executes(this::reload))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("zones").executes(this::zones))
                .then(Commands.literal("info")
                        .then(Commands.argument("zona", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        config.settings().zones().stream().map(Zone::name), builder))
                                .executes(this::info)))
                .then(Commands.literal("create")
                        .then(Commands.argument("nombre", StringArgumentType.word())
                                .then(Commands.argument("desde", BlockPosArgument.blockPos())
                                        .then(Commands.argument("hasta", BlockPosArgument.blockPos())
                                                .executes(context -> create(context, BOX_MULTIPLIER))
                                                .then(Commands.argument("multiplicador",
                                                                DoubleArgumentType.doubleArg(1.0, 50.0))
                                                        .executes(context -> create(context,
                                                                DoubleArgumentType.getDouble(context, "multiplicador"))))))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("zona", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        config.settings().zones().stream().map(Zone::name), builder))
                                .executes(this::delete)))
                .then(Commands.literal("enable").executes(context -> setEnabled(context, true)))
                .then(Commands.literal("disable").executes(context -> setEnabled(context, false))));
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ConfigManager.LoadResult result = config.load(source.getServer());
        spawner.onReload();
        source.sendSuccess(() -> Component.literal(result.message())
                .withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        for (String warning : result.warnings()) {
            source.sendSuccess(() -> Component.literal("⚠ " + warning).withStyle(ChatFormatting.YELLOW), false);
        }
        return result.success() ? 1 : 0;
    }

    private int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        SpawnSettings settings = config.settings();
        ExtraMobTracker tracker = spawner.tracker();

        send(source, header("Toque Mob Spawner"));
        send(source, line("Estado", settings.enabled()
                ? Component.literal("activado").withStyle(ChatFormatting.GREEN)
                : Component.literal("desactivado").withStyle(ChatFormatting.RED)));
        send(source, line("Mobs extra cargados", value(tracker.size() + " / " + settings.globalMaxExtraMobs())));
        send(source, line("Revisión cada", value(settings.spawnCheckIntervalTicks() + " ticks")));

        Map<String, Integer> counts = tracker.countByZone(settings.zones());
        for (Zone zone : settings.zones()) {
            send(source, line("Zona " + zone.name(), value(counts.getOrDefault(zone.name(), 0)
                    + " / " + zone.maxExtraMobs() + (zone.enabled() ? "" : " (desactivada)"))));
        }

        int inZones = 0;
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            String zone = spawner.zoneOf(player.getUUID());
            if (zone == null) {
                continue;
            }
            inZones++;
            int near = tracker.countNear(player.level(), player.getX(), player.getY(), player.getZ(),
                    settings.maxSpawnDistance());
            send(source, line(player.getGameProfile().name(),
                    value(zone + " · " + near + " extra cerca")));
        }
        if (inZones == 0) {
            send(source, Component.literal("Ningún jugador está dentro de una zona.").withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private int zones(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        SpawnSettings settings = config.settings();
        send(source, header("Zonas (" + settings.zones().size() + ")"));
        if (settings.zones().isEmpty()) {
            send(source, Component.literal("No hay zonas en " + config.file()).withStyle(ChatFormatting.GRAY));
        }
        for (Zone zone : settings.zones()) {
            send(source, Component.literal("▪ ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(zone.name()).withStyle(zone.enabled() ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY))
                    .append(Component.literal(" " + bounds(zone) + " · " + zone.spawnMultiplier() + "x · "
                            + zone.mobs().size() + " mobs").withStyle(ChatFormatting.GRAY)));
        }
        return settings.zones().size();
    }

    private int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "zona");
        Zone zone = config.settings().zoneNamed(name);
        if (zone == null) {
            source.sendFailure(Component.literal("No existe la zona '" + name + "'. Usa /toquemobs zones."));
            return 0;
        }

        int count = spawner.tracker().countByZone(config.settings().zones()).getOrDefault(zone.name(), 0);
        send(source, header("Zona " + zone.name()));
        send(source, line("Activa", value(zone.enabled() ? "sí" : "no")));
        send(source, line("Dimensión", value(zone.dimension().identifier().toString())));
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
            send(source, line("Inválidos", Component.literal(String.join(", ", zone.invalidMobs()))
                    .withStyle(ChatFormatting.RED)));
        }
        return 1;
    }

    private int create(CommandContext<CommandSourceStack> context, double multiplier) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "nombre");
        BlockPos from = BlockPosArgument.getLoadedBlockPos(context, "desde");
        BlockPos to = BlockPosArgument.getLoadedBlockPos(context, "hasta");

        ModConfig.ZoneConfig zone = ModConfig.ZoneConfig.of(name,
                Math.min(from.getX(), to.getX()), Math.max(from.getX(), to.getX()),
                Math.min(from.getZ(), to.getZ()), Math.max(from.getZ(), to.getZ()),
                multiplier, BOX_MAX_MOBS);
        zone.yMin = Math.min(from.getY(), to.getY());
        zone.yMax = Math.max(from.getY(), to.getY());
        zone.spawnInside = true;
        zone.spawnsPerCheck = BOX_SPAWNS_PER_CHECK;
        zone.dimension = source.getLevel().dimension().identifier().toString();

        ConfigManager.LoadResult result = config.addZone(source.getServer(), zone);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        spawner.onReload();
        Zone created = config.settings().zoneNamed(name);
        source.sendSuccess(() -> Component.literal("Zona '" + name + "' creada: " + bounds(created)
                + ". Hasta " + created.targetPerPlayer() + " mobs dentro mientras haya alguien adentro.")
                .withStyle(ChatFormatting.GREEN), true);
        source.sendSuccess(() -> Component.literal("Mobs, multiplicador y límites se cambian en "
                + config.file().getFileName() + " y se aplican con /toquemobs reload.")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private int delete(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "zona");
        ConfigManager.LoadResult result = config.removeZone(source.getServer(), name);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        spawner.onReload();
        source.sendSuccess(() -> Component.literal("Zona '" + name + "' borrada. Sus mobs extra se van cuando nadie esté cerca.")
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private int setEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        config.setEnabled(enabled);
        context.getSource().sendSuccess(() -> Component.literal("Toque Mob Spawner "
                + (enabled ? "activado." : "desactivado. Los mobs extra que ya existen se quedan hasta que se alejen."))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
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
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    private static void send(CommandSourceStack source, Component text) {
        source.sendSuccess(() -> text, false);
    }

    private static Component header(String title) {
        return Component.literal("☠ " + title + " ☠").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
    }

    private static Component line(String label, Component value) {
        MutableComponent text = Component.literal("▪ ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(label + ": ").withStyle(ChatFormatting.WHITE));
        return text.append(value);
    }

    private static Component value(String value) {
        return Component.literal(value).withStyle(ChatFormatting.GOLD);
    }
}
