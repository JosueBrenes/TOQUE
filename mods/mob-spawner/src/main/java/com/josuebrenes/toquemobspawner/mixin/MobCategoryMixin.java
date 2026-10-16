package com.josuebrenes.toquemobspawner.mixin;

import com.josuebrenes.toquemobspawner.difficulty.MobDifficulty;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Raises the hostile cap vanilla's natural spawner works to, globally and per chunk. */
@Mixin(MobCategory.class)
public abstract class MobCategoryMixin {
    @Inject(method = "getMaxInstancesPerChunk", at = @At("RETURN"), cancellable = true)
    private void toque$scaleCap(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(MobDifficulty.scaleCap((MobCategory) (Object) this, cir.getReturnValueI()));
    }
}
