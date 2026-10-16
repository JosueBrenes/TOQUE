package com.josuebrenes.toquedeathalert.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Name and hearts of whatever living entity a player is looking at, at the top
 * of the screen.
 *
 * <p>Drawn as a boss bar because that is the only panel at the top of the screen
 * a vanilla client will take from the server with nothing installed. Each player
 * has a bar of their own that nobody else is subscribed to. The title is the
 * whole panel, drawn by {@link TargetPanel} with the resource pack's fonts; the
 * bar itself is green, which the pack makes see-through, because the panel
 * already shows the health and nothing vanilla uses a green bar.
 *
 * <p>The target is found on the server with the same ray the client uses for its
 * crosshair, but reaching further, and cut short by the first block in the way so
 * nothing is revealed through walls.
 */
public final class TargetHealthHud {
    /** How far a player can read a target, in blocks. */
    private static final double RANGE = 24.0D;

    /** Ten checks a second is enough to feel instant without raycasting every tick. */
    private static final int CHECK_TICKS = 2;

    private final Map<UUID, Shown> shown = new ConcurrentHashMap<>();
    private int checkCounter;

    /** One player's bar and the key of what it last showed, so it is only resent on change. */
    private record Shown(ServerBossEvent bar, String key) {
    }

    public void onServerTick(MinecraftServer server) {
        if (++checkCounter < CHECK_TICKS) {
            return;
        }
        checkCounter = 0;
        server.getPlayerList().getPlayers().forEach(this::refresh);
    }

    /**
     * Takes the bar off this player's screen. Called on disconnect, and when the
     * client changes world, so the next refresh adds it back cleanly.
     */
    public void forget(UUID uuid) {
        Shown previous = shown.remove(uuid);
        if (previous != null) {
            previous.bar().removeAllPlayers();
        }
    }

    private void refresh(ServerPlayer player) {
        LivingEntity target = target(player);
        Shown previous = shown.get(player.getUUID());

        if (target == null) {
            forget(player.getUUID());
            return;
        }

        Component name = target.getDisplayName();
        float health = Math.max(0.0F, target.getHealth());
        float maxHealth = Math.max(1.0F, target.getMaxHealth());
        String key = target.getId() + ":" + Mth.ceil(health) + ":" + maxHealth
                + ":" + name.getString();
        if (previous != null && previous.key().equals(key)) {
            return;
        }

        Component title = TargetPanel.render(target);
        float percent = Mth.clamp(health / maxHealth, 0.0F, 1.0F);

        ServerBossEvent bar;
        if (previous == null) {
            bar = new ServerBossEvent(UUID.randomUUID(), title, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
            bar.setProgress(percent);
            bar.addPlayer(player);
        } else {
            bar = previous.bar();
            bar.setName(title);
            bar.setProgress(percent);
        }
        shown.put(player.getUUID(), new Shown(bar, key));
    }

    @Nullable
    private static LivingEntity target(ServerPlayer player) {
        // A spectator riding someone else's camera is not looking from their own eyes.
        if (player.getCamera() != player || !player.isAlive()) {
            return null;
        }

        Vec3 eyes = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);

        double reach = RANGE;
        HitResult block = player.pick(RANGE, 1.0F, false);
        if (block.getType() != HitResult.Type.MISS) {
            reach = block.getLocation().distanceTo(eyes);
        }

        Vec3 end = eyes.add(look.scale(reach));
        AABB sweep = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eyes, end, sweep,
                entity -> isReadable(player, entity), reach * reach);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static boolean isReadable(ServerPlayer viewer, Entity entity) {
        return entity instanceof LivingEntity
                && !(entity instanceof ArmorStand)
                && entity.isPickable()
                && !entity.isSpectator()
                && !entity.isInvisibleTo(viewer);
    }
}
