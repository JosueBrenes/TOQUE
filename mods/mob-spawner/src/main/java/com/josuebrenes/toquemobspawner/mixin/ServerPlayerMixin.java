package com.josuebrenes.toquemobspawner.mixin;

import com.josuebrenes.toquemobspawner.difficulty.MobDifficulty;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Scales the damage a player takes from hostile mobs. Done on the hit rather than
 * on the mob's attack attribute, so arrows and creeper blasts are covered too.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float toque$scaleMobDamage(float damage, ServerLevel level, DamageSource source) {
        return source.getEntity() == null ? damage : MobDifficulty.scaleDamage(source.getEntity(), damage);
    }
}
