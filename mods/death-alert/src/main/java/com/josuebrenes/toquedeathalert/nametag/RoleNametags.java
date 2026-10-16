package com.josuebrenes.toquedeathalert.nametag;

import com.josuebrenes.toquedeathalert.role.PlayerRole;
import com.josuebrenes.toquedeathalert.series.SeriesStatsRepository;
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Puts the rank in front of the name floating over a player's head.
 *
 * <p>The floating name is drawn by the client from the player's scoreboard team
 * prefix, so one team per rank is enough: the teams are sent straight down the
 * connection and never written to the world scoreboard, which keeps the world
 * data and any datapack teams untouched and means Hardcore World Reset has
 * nothing of ours to delete.
 *
 * <p>The player list is unaffected. It prefers the display name the tab renderer
 * supplies, and only falls back to team decoration when there is none.
 */
public final class RoleNametags {
    /** Prefixed and numbered so they cannot collide with a datapack's own teams. */
    private static final String TEAM_PREFIX = "toque_rank_";

    private final SeriesStatsRepository stats;
    private final Map<PlayerRole, PlayerTeam> teams = new EnumMap<>(PlayerRole.class);
    private final Map<UUID, PlayerRole> assigned = new ConcurrentHashMap<>();

    public RoleNametags(SeriesStatsRepository stats) {
        this.stats = stats;
    }

    /**
     * Defines every rank team on this player's client, then places everyone.
     *
     * <p>Also needed after a world change: the client throws its whole scoreboard
     * away, teams included, so the names above heads would lose their rank until
     * the teams are declared again.
     */
    public void install(MinecraftServer server, ServerPlayer player) {
        for (PlayerTeam team : teamsFor(server).values()) {
            player.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(team));
            player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        }
        assigned.clear();
        refresh(server);
    }

    public void forget(UUID uuid) {
        assigned.remove(uuid);
    }

    /** Moves anyone whose rank has changed into the matching team. */
    public void refresh(MinecraftServer server) {
        Map<PlayerRole, PlayerTeam> byRole = teamsFor(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerRole role = PlayerRole.fromDeaths(stats.deathsOf(player.getUUID()));
            if (role == assigned.get(player.getUUID())) {
                continue;
            }
            assigned.put(player.getUUID(), role);

            ClientboundSetPlayerTeamPacket packet = ClientboundSetPlayerTeamPacket.createPlayerPacket(byRole.get(role),
                    player.getGameProfile().name(), ClientboundSetPlayerTeamPacket.Action.ADD);
            for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                viewer.connection.send(packet);
            }
        }
    }

    /**
     * The rank teams, built once against the server scoreboard but never added to
     * it: a Team needs a scoreboard to exist, not to be registered in one.
     */
    private Map<PlayerRole, PlayerTeam> teamsFor(MinecraftServer server) {
        if (!teams.isEmpty()) {
            return teams;
        }
        for (PlayerRole role : PlayerRole.values()) {
            PlayerTeam team = new PlayerTeam(server.getScoreboard(), TEAM_PREFIX + role.ordinal());
            team.setDisplayName(Component.literal(role.label()));
            team.setPlayerPrefix(role.tag().append(Component.literal(" ")));
            team.setColor(Optional.of(TeamColor.WHITE));
            teams.put(role, team);
        }
        return teams;
    }
}
