package com.josuebrenes.toqueserverbanner.mixin;

import com.josuebrenes.toqueserverbanner.ToqueServerBannerConfig;
import com.josuebrenes.toqueserverbanner.ToqueServerBannerRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the look of the TOQUE row in the multiplayer list.
 *
 * <p>Injected at RETURN of {@code extractContent} rather than cancelled at HEAD on purpose. The vanilla
 * render is not only drawing: it starts the status ping the first time the row
 * appears and it uploads the favicon once it arrives. Cancelling it left the row
 * with no player count, no ping, no MOTD and no icon, which is why the entry
 * looked untouched. Vanilla runs in full and the banner is painted on top of it,
 * covering the row completely.
 *
 * <p>Nothing about the entry's behaviour is touched, so clicking, double
 * clicking, selecting, the play, edit, delete and move buttons all keep working.
 */
@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class MultiplayerServerEntryMixin {
    @Shadow
    @Final
    private ServerData serverData;

    @Inject(method = "extractContent", at = @At("RETURN"))
    private void toque$drawBanner(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered,
                                  float tickDelta, CallbackInfo ci) {
        if (!ToqueServerBannerConfig.matches(serverData)) {
            return;
        }
        ServerSelectionList.Entry entry = (ServerSelectionList.Entry) (Object) this;
        ToqueServerBannerRenderer.render(context, serverData, entry.getContentX(), entry.getContentY(),
                entry.getContentWidth(), entry.getContentHeight(), hovered);
    }
}
