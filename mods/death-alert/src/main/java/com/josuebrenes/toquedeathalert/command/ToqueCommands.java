package com.josuebrenes.toquedeathalert.command;

import com.josuebrenes.toquedeathalert.core.ToqueLog;
import com.josuebrenes.toquedeathalert.core.ToqueRuntime;
import com.josuebrenes.toquedeathalert.migration.VanillaDeathsLookup;
import com.josuebrenes.toquedeathalert.role.PlayerRole;
import com.josuebrenes.toquedeathalert.series.PlayerDeathRecord;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;

/**
 * The {@code /toque} command tree.
 *
 * <p>Registered while the data packs load, which happens before the server object
 * exists, so every action resolves its services from {@link ToqueRuntime} at
 * execution time rather than capturing them here.
 */
public final class ToqueCommands {
    private static final PermissionCheck ADMIN_LEVEL = Commands.LEVEL_GAMEMASTERS;
    private static final String SKULL = "☠";

    private final ToqueRuntime runtime;

    public ToqueCommands(ToqueRuntime runtime) {
        this.runtime = runtime;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("toque")
                .then(Commands.literal("deaths")
                        .executes(this::listDeaths))
                .then(Commands.literal("status")
                        .requires(Commands.hasPermission(ADMIN_LEVEL))
                        .executes(context -> status(context, context.getSource().getPlayer()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> status(context,
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("set")
                        .requires(Commands.hasPermission(ADMIN_LEVEL))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("deaths", IntegerArgumentType.integer(0))
                                        .executes(this::setDeaths))))
                .then(Commands.literal("import")
                        .requires(Commands.hasPermission(ADMIN_LEVEL))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(this::importDeaths)))
                .then(Commands.literal("objective")
                        .requires(Commands.hasPermission(ADMIN_LEVEL))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(this::setObjective)))
                .then(Commands.literal("try")
                        .requires(Commands.hasPermission(ADMIN_LEVEL))
                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                .executes(this::setTry)))
                .then(Commands.literal("resetDeaths")
                        .requires(Commands.hasPermission(ADMIN_LEVEL))
                        .executes(this::resetSeries)));
    }

    private int listDeaths(CommandContext<CommandSourceStack> context) {
        ToqueRuntime.Services services = require(context);
        if (services == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.literal(SKULL + " TOQUE — Serie #"
                + services.stats().seriesNumber()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);

        List<PlayerDeathRecord> records = services.stats().all().stream()
                .sorted(Comparator.comparingInt(PlayerDeathRecord::deaths).reversed())
                .toList();

        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Sin muertes registradas.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (PlayerDeathRecord record : records) {
            PlayerRole role = PlayerRole.fromDeaths(record.deaths());
            source.sendSuccess(() -> Component.literal(record.displayName() + " ").withStyle(ChatFormatting.WHITE)
                    .append(role.badge())
                    .append(Component.literal("  " + SKULL + " " + record.deaths()).withStyle(ChatFormatting.RED)), false);
        }
        return records.size();
    }

    /** Diagnostics: what TOQUE stored, what vanilla says, and where the file lives. */
    private int status(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        ToqueRuntime.Services services = require(context);
        if (services == null || player == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        PlayerDeathRecord record = services.stats().find(player.getUUID())
                .orElseGet(() -> PlayerDeathRecord.fresh(player.getUUID(), player.getGameProfile().name()));
        VanillaDeathsLookup.Result vanilla = services.importer().lookup()
                .lookup(source.getServer(), player.getUUID(), player);

        line(source, "Archivo", services.stats().file().toString());
        line(source, "Serie", "#" + services.stats().seriesNumber());
        line(source, "Try", "#" + services.stats().tryNumber());
        line(source, "Objetivo", services.stats().objective());
        line(source, "Import vanilla abierto", Boolean.toString(services.stats().isVanillaImportOpen()));
        line(source, "Jugador", record.displayName() + " (" + player.getUUID() + ")");
        line(source, "Muertes TOQUE", Integer.toString(record.deaths()));
        line(source, "Rol", PlayerRole.fromDeaths(record.deaths()).plain());
        line(source, "Ya migrado", Boolean.toString(record.migrated()));
        line(source, "Muertes vanilla", vanilla.deaths() + " [" + vanilla.origin() + "]");
        return 1;
    }

    private int setDeaths(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ToqueRuntime.Services services = require(context);
        if (services == null) {
            return 0;
        }
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int deaths = IntegerArgumentType.getInteger(context, "deaths");

        services.stats().setDeaths(player.getUUID(), player.getGameProfile().name(), deaths);
        services.tabList().invalidate(player.getUUID());

        context.getSource().sendSuccess(() -> Component.literal(SKULL + " "
                + player.getGameProfile().name() + " queda en " + deaths + " muertes.")
                .withStyle(ChatFormatting.RED), true);
        ToqueLog.info("Admin set deaths for {} to {}.", player.getGameProfile().name(), deaths);
        return deaths;
    }

    private int importDeaths(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ToqueRuntime.Services services = require(context);
        if (services == null) {
            return 0;
        }
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int starting = services.importer().forceImport(context.getSource().getServer(), player);
        services.tabList().invalidate(player.getUUID());

        context.getSource().sendSuccess(() -> Component.literal(SKULL + " "
                + player.getGameProfile().name() + " importado desde vanilla: "
                + starting + " muertes.").withStyle(ChatFormatting.RED), true);
        return starting;
    }

    /** The objective line shown at the bottom of the player list. */
    private int setObjective(CommandContext<CommandSourceStack> context) {
        ToqueRuntime.Services services = require(context);
        if (services == null) {
            return 0;
        }
        String objective = StringArgumentType.getString(context, "text");
        services.stats().setObjective(objective);
        services.tabList().invalidateAll();
        services.tabList().sendHeaderAndFooter(context.getSource().getServer());

        context.getSource().sendSuccess(() -> Component.literal(SKULL + " Objetivo: " + objective)
                .withStyle(ChatFormatting.GOLD), true);
        ToqueLog.info("Objective set to: {}", objective);
        return 1;
    }

    /** Manual correction, for when the Try counter and the real series disagree. */
    private int setTry(CommandContext<CommandSourceStack> context) {
        ToqueRuntime.Services services = require(context);
        if (services == null) {
            return 0;
        }
        int number = IntegerArgumentType.getInteger(context, "number");
        services.stats().setTryNumber(number);
        services.tabList().invalidateAll();
        services.tabList().sendHeaderAndFooter(context.getSource().getServer());

        context.getSource().sendSuccess(() -> Component.literal(SKULL + " Try #" + number + ".")
                .withStyle(ChatFormatting.GOLD), true);
        ToqueLog.info("Try counter set to #{}", number);
        return number;
    }

    private int resetSeries(CommandContext<CommandSourceStack> context) {
        ToqueRuntime.Services services = require(context);
        if (services == null) {
            return 0;
        }
        // Worlds, seeds, Hardcore World Reset and vanilla statistics stay untouched:
        // only the TOQUE counters go back to zero, and the vanilla import closes so a
        // restart can never turn a fresh 0 back into an old number.
        services.stats().startNewSeries();
        services.tabList().invalidateAll();
        services.tabList().sendHeaderAndFooter(context.getSource().getServer());

        int series = services.stats().seriesNumber();
        context.getSource().sendSuccess(() -> Component.literal(SKULL + " Nueva serie iniciada (#"
                + series + "). Muertes reiniciadas a 0.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        ToqueLog.info("Death counters reset. New series: #{}", series);
        return 1;
    }

    private ToqueRuntime.Services require(CommandContext<CommandSourceStack> context) {
        ToqueRuntime.Services services = runtime.services();
        if (services == null) {
            context.getSource().sendFailure(Component.literal("TOQUE aun no esta listo."));
        }
        return services;
    }

    private static void line(CommandSourceStack source, String label, String value) {
        source.sendSuccess(() -> Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE)), false);
    }
}
