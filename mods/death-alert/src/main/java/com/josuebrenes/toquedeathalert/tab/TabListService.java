package com.josuebrenes.toquedeathalert.tab;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the player list in sync over plain vanilla protocol.
 *
 * <p>Only two packets are involved, both of which every vanilla client already
 * understands, so nobody has to install anything: the player-list header/footer,
 * and a display-name update for the rows that actually changed.
 */
public final class TabListService {
    /** Refresh interval in server ticks; 4 ticks is five updates per second. */
    private static final int REFRESH_TICKS = 4;

    /** How often the banner is re-checked, in ticks; the day only changes slowly. */
    private static final int BANNER_TICKS = 100;

    private final TabRowRenderer renderer;
    private final Map<UUID, String> lastSent = new ConcurrentHashMap<>();
    private int tickCounter;
    private int bannerCounter;
    private String lastBanner = "";

    public TabListService(TabRowRenderer renderer) {
        this.renderer = renderer;
    }

    /** The text the mixin hands to the client for this player's row. */
    public Component rowFor(ServerPlayer player) {
        return renderer.row(player);
    }

    public void onServerTick(MinecraftServer server) {
        if (++bannerCounter >= BANNER_TICKS) {
            bannerCounter = 0;
            resendBannerIfChanged(server);
        }
        if (++tickCounter < REFRESH_TICKS) {
            return;
        }
        tickCounter = 0;
        broadcastChangedRows(server);
    }

    /** Keeps DAY and the online count current without spamming the packet. */
    private void resendBannerIfChanged(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        String banner = renderer.header(server).getString() + renderer.footer(server).getString();
        if (!banner.equals(lastBanner)) {
            lastBanner = banner;
            sendHeaderAndFooter(server);
        }
    }

    private void broadcastChangedRows(MinecraftServer server) {
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        if (online.isEmpty()) {
            return;
        }

        List<ServerPlayer> changed = new ArrayList<>();
        for (ServerPlayer player : online) {
            String rendered = renderer.row(player).getString();
            if (!rendered.equals(lastSent.get(player.getUUID()))) {
                lastSent.put(player.getUUID(), rendered);
                changed.add(player);
            }
        }
        if (changed.isEmpty()) {
            return;
        }

        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), changed);
        for (ServerPlayer viewer : online) {
            viewer.connection.send(packet);
        }
    }

    /** Forces every row to be resent on the next tick. */
    public void invalidateAll() {
        lastSent.clear();
        lastBanner = "";
    }

    public void invalidate(UUID uuid) {
        lastSent.remove(uuid);
    }

    public void sendHeaderAndFooter(MinecraftServer server) {
        ClientboundTabListPacket packet =
                new ClientboundTabListPacket(renderer.header(server), renderer.footer(server));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }
}
