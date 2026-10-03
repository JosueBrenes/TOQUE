package com.josuebrenes.toquemobspawner;

import com.josuebrenes.toquemobspawner.command.ToqueMobCommands;
import com.josuebrenes.toquemobspawner.config.ConfigManager;
import com.josuebrenes.toquemobspawner.spawn.ExtraMobTracker;
import com.josuebrenes.toquemobspawner.spawn.SpawnManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Entry point. Wires the config, the tracker and the spawner to the server's
 * events; the behaviour lives in the packages below.
 */
public final class ToqueMobSpawner implements ModInitializer {
    public static final String MOD_ID = "toque-mob-spawner";

    @Override
    public void onInitialize() {
        ConfigManager config = new ConfigManager();
        ExtraMobTracker tracker = new ExtraMobTracker();
        SpawnManager spawner = new SpawnManager(config, tracker);

        // Loaded on start rather than here: every mod's entities are registered by
        // then, so a modded mob in the config resolves.
        ServerLifecycleEvents.SERVER_STARTED.register(config::load);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> spawner.onServerStopped());

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> tracker.onLoad(entity));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> tracker.onUnload(entity));

        ServerTickEvents.END_SERVER_TICK.register(spawner::onServerTick);

        ToqueMobCommands commands = new ToqueMobCommands(config, spawner);
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> commands.register(dispatcher));
    }
}
