package com.josuebrenes.toquedeathalert.hud;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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
    private record Shown(ServerBossBar bar, String key) {
    }

    public void onServerTick(MinecraftServer server) {
        if (++checkCounter < CHECK_TICKS) {
            return;
        }
        checkCounter = 0;
        server.getPlayerManager().getPlayerList().forEach(this::refresh);
    }

    /**
     * Takes the bar off this player's screen. Called on disconnect, and when the
     * client changes world, so the next refresh adds it back cleanly.
     */
    public void forget(UUID uuid) {
        Shown previous = shown.remove(uuid);
        if (previous != null) {
            previous.bar().clearPlayers();
        }
    }

    private void refresh(ServerPlayerEntity player) {
        LivingEntity target = target(player);
        Shown previous = shown.get(player.getUuid());

        if (target == null) {
            forget(player.getUuid());
            return;
        }

        Text name = target.getDisplayName();
        float health = Math.max(0.0F, target.getHealth());
        float maxHealth = Math.max(1.0F, target.getMaxHealth());
        String key = target.getId() + ":" + MathHelper.ceil(health) + ":" + maxHealth
                + ":" + name.getString();
        if (previous != null && previous.key().equals(key)) {
            return;
        }

        Text title = TargetPanel.render(target);
        float percent = MathHelper.clamp(health / maxHealth, 0.0F, 1.0F);

        ServerBossBar bar;
        if (previous == null) {
            bar = new ServerBossBar(title, BossBar.Color.GREEN, BossBar.Style.PROGRESS);
            bar.setPercent(percent);
            bar.addPlayer(player);
        } else {
            bar = previous.bar();
            bar.setName(title);
            bar.setPercent(percent);
        }
        shown.put(player.getUuid(), new Shown(bar, key));
    }

    @Nullable
    private static LivingEntity target(ServerPlayerEntity player) {
        // A spectator riding someone else's camera is not looking from their own eyes.
        if (player.getCameraEntity() != player || !player.isAlive()) {
            return null;
        }

        Vec3d eyes = player.getCameraPosVec(1.0F);
        Vec3d look = player.getRotationVec(1.0F);

        double reach = RANGE;
        HitResult block = player.raycast(RANGE, 1.0F, false);
        if (block.getType() != HitResult.Type.MISS) {
            reach = block.getPos().distanceTo(eyes);
        }

        Vec3d end = eyes.add(look.multiply(reach));
        Box sweep = player.getBoundingBox().stretch(look.multiply(reach)).expand(1.0D);
        EntityHitResult hit = ProjectileUtil.raycast(player, eyes, end, sweep,
                entity -> isReadable(player, entity), reach * reach);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static boolean isReadable(ServerPlayerEntity viewer, Entity entity) {
        return entity instanceof LivingEntity
                && !(entity instanceof ArmorStandEntity)
                && entity.canHit()
                && !entity.isSpectator()
                && !entity.isInvisibleTo(viewer);
    }
}
