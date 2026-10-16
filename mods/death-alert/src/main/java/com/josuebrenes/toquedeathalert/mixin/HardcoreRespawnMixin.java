package com.josuebrenes.toquedeathalert.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hardcore without the spectator sentence: a player who dies comes back in
 * survival, at their bed or the world spawn, and has to go back for their things.
 *
 * <p>Vanilla respawns a hardcore player normally and only then switches them to
 * spectator. That one switch is skipped; everything else about hardcore stays,
 * the hearts, the locked Hard difficulty and the death screen.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class HardcoreRespawnMixin {
    @Redirect(method = "handleClientCommand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;setGameMode(Lnet/minecraft/world/level/GameType;)Z"))
    private boolean toque$keepSurvival(ServerPlayer player, GameType mode) {
        return mode == GameType.SPECTATOR ? false : player.setGameMode(mode);
    }
}
