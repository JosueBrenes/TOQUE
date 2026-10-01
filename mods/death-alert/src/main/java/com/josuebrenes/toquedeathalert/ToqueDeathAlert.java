package com.josuebrenes.toquedeathalert;

import com.josuebrenes.toquedeathalert.command.ToqueCommands;
import com.josuebrenes.toquedeathalert.core.ToqueLog;
import com.josuebrenes.toquedeathalert.core.ToqueRuntime;
import com.josuebrenes.toquedeathalert.death.DeathAnnouncer;
import com.josuebrenes.toquedeathalert.death.DeathListener;
import com.josuebrenes.toquedeathalert.hud.SidebarHud;
import com.josuebrenes.toquedeathalert.hud.TargetHealthHud;
import com.josuebrenes.toquedeathalert.migration.VanillaDeathsImporter;
import com.josuebrenes.toquedeathalert.nametag.RoleNametags;
import com.josuebrenes.toquedeathalert.migration.VanillaDeathsLookup;
import com.josuebrenes.toquedeathalert.series.SeriesStatsRepository;
import com.josuebrenes.toquedeathalert.series.TryWatcher;
import com.josuebrenes.toquedeathalert.tab.TabListService;
import com.josuebrenes.toquedeathalert.tab.TabRowRenderer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Entry point. Wires the services together and registers the server events;
 * all behaviour lives in the packages below.
 */
public final class ToqueDeathAlert implements ModInitializer {
    public static final String MOD_ID = "toque-death-alert";

    private static final ToqueRuntime RUNTIME = new ToqueRuntime();

    public static ToqueRuntime runtime() {
        return RUNTIME;
    }

    @Override
    public void onInitialize() {
        ToqueLog.info("Death Alert loaded.");

        registerLifecycle();
        registerConnection();
        registerTick();
        registerCommands();
        new DeathListener(RUNTIME, new DeathAnnouncer()).register();
    }

    private void registerLifecycle() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> RUNTIME.bind(buildServices(server)));

        // The world only exists once the server has started. From then on the Try
        // is watched on a timer, because Hardcore World Reset rebuilds the world in
        // the middle of a session without ever restarting the server.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ToqueRuntime.Services services = RUNTIME.services();
            if (services == null) {
                return;
            }
            services.tryWatcher().check(server);
            ToqueLog.info("Running Try #{}.", services.stats().tryNumber());
            services.tabList().sendHeaderAndFooter(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SeriesStatsRepository stats = RUNTIME.stats();
            if (stats != null) {
                stats.save();
            }
            RUNTIME.unbind();
        });
    }

    /**
     * The stats live in {@code config/toque-death-alert/deaths.json}, outside the
     * world folder that Hardcore World Reset deletes on every new Try.
     */
    private ToqueRuntime.Services buildServices(MinecraftServer server) {
        SeriesStatsRepository stats = new SeriesStatsRepository(server.getRunDirectory());
        stats.load();

        TabListService tabList = new TabListService(new TabRowRenderer(stats));
        VanillaDeathsImporter importer = new VanillaDeathsImporter(stats, new VanillaDeathsLookup());
        TryWatcher tryWatcher = new TryWatcher(stats);
        SidebarHud hud = new SidebarHud(stats);
        RoleNametags nametags = new RoleNametags(stats);
        TargetHealthHud targetHud = new TargetHealthHud();

        ToqueLog.info("Stats loaded from {} (series #{}, vanilla import {}).",
                stats.file(), stats.seriesNumber(),
                stats.isVanillaImportOpen() ? "open" : "closed");
        return new ToqueRuntime.Services(stats, tabList, importer, tryWatcher, hud, nametags, targetHud);
    }

    private void registerConnection() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ToqueRuntime.Services services = RUNTIME.services();
            if (services == null) {
                return;
            }
            ServerPlayerEntity player = handler.getPlayer();

            services.importer().migrateOnJoin(server, player);
            services.tabList().invalidate(player.getUuid());
            // Everyone gets it: the footer carries the online count.
            services.tabList().sendHeaderAndFooter(server);
            reinstallOverlays(server, player);
        });

        // A client throws its whole scoreboard away when it changes world, taking
        // the panel and the rank teams with it. Hardcore World Reset moves everyone
        // to a freshly generated world on every Try, and dying moves them too, so
        // both have to be put back rather than left to a timer.
        ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> reinstallOverlays(newPlayer.server, newPlayer));

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> reinstallOverlays(player.server, player));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ToqueRuntime.Services services = RUNTIME.services();
            if (services == null) {
                return;
            }
            java.util.UUID uuid = handler.getPlayer().getUuid();
            services.tabList().invalidate(uuid);
            services.tabList().sendHeaderAndFooter(server);
            services.hud().forget(uuid);
            services.nametags().forget(uuid);
            services.targetHud().forget(uuid);
        });
    }

    /** Puts the sidebar, the rank teams and the target bar back on one player's client. */
    private static void reinstallOverlays(MinecraftServer server, ServerPlayerEntity player) {
        ToqueRuntime.Services services = RUNTIME.services();
        if (services == null) {
            return;
        }
        services.hud().install(server, player);
        services.nametags().install(server, player);
        // The bar is dropped and added again on the next check, rather than trusted
        // to have survived the client's move to another world.
        services.targetHud().forget(player.getUuid());
    }

    private void registerCommands() {
        ToqueCommands commands = new ToqueCommands(RUNTIME);
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> commands.register(dispatcher));
    }

    private void registerTick() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ToqueRuntime.Services services = RUNTIME.services();
            if (services == null) {
                return;
            }
            services.tryWatcher().onServerTick(server);
            services.tabList().onServerTick(server);
            services.hud().onServerTick(server);
            services.nametags().refresh(server);
            services.targetHud().onServerTick(server);
        });
    }
}
