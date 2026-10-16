package com.josuebrenes.toquedeathalert.death;

import com.josuebrenes.toquedeathalert.ToqueDeathAlert;
import com.josuebrenes.toquedeathalert.role.PlayerRole;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Shows the TOQUE death title to everyone and plays the series sound; the player respawns afterwards. */
public final class DeathAnnouncer {
    private static final SoundEvent DEATH_SOUND = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("toque", "death"));
    private static final String SKULL = "☠";

    public void announce(MinecraftServer server, ServerPlayer deadPlayer) {
        int deaths = ToqueDeathAlert.runtime().stats() == null
                ? 0
                : ToqueDeathAlert.runtime().stats().deathsOf(deadPlayer.getUUID());
        PlayerRole role = PlayerRole.fromDeaths(deaths);

        Component title = Component.literal(SKULL + " ")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(deadPlayer.getName().copy().withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal(" HA MUERTO " + SKULL).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));

        Component subtitle = role.badge()
                .append(Component.literal("  •  VE POR TUS COSAS")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            viewer.connection.send(new ClientboundSetTitlesAnimationPacket(5, 80, 10));
            viewer.connection.send(new ClientboundSetTitleTextPacket(title));
            viewer.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            viewer.connection.send(new ClientboundSoundPacket(Holder.direct(DEATH_SOUND), SoundSource.MASTER,
                    viewer.getX(), viewer.getY(), viewer.getZ(), 10.0F, 1.0F, viewer.getRandom().nextLong()));
        }
    }
}
