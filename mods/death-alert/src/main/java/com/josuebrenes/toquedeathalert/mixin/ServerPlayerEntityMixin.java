package com.josuebrenes.toquedeathalert.mixin;

import com.josuebrenes.toquedeathalert.ToqueDeathAlert;
import com.josuebrenes.toquedeathalert.tab.TabListService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla returns null here, which makes the client draw the plain player name in
 * the player list. Returning our own text makes the server send that text inside
 * the standard player-list packet, so vanilla clients render it with no mod.
 *
 * <p>Read-only: this never touches the death counters.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin {
    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void toque$playerListName(CallbackInfoReturnable<Component> cir) {
        TabListService tabList = ToqueDeathAlert.runtime().tabList();
        if (tabList == null) {
            return;
        }
        cir.setReturnValue(tabList.rowFor((ServerPlayer) (Object) this));
    }
}
