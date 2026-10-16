package com.josuebrenes.toquedeathalert.hud;

import com.josuebrenes.toquedeathalert.core.Gradient;
import com.josuebrenes.toquedeathalert.role.PlayerRole;
import com.josuebrenes.toquedeathalert.series.SeriesStatsRepository;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The panel down the right hand side of the screen.
 *
 * <p>Sent per player rather than through the world scoreboard. The world
 * scoreboard has one sidebar shared by everybody, and half of what belongs on
 * this panel is personal: coordinates, rank and death count differ for each
 * player. So the objective, the display slot and every line are written straight
 * to one connection, and the world scoreboard is never touched. That also keeps
 * it clear of the datapack objectives already on this server.
 *
 * <p>Scores carry their own display text, so the lines are free text rather than
 * the fake player names this used to need, and the objective asks for a blank
 * number format so the red score column on the right is not drawn at all.
 */
public final class SidebarHud {
    private static final String OBJECTIVE_NAME = "toque_hud";

    private static final int ADD_MODE = 0;
    private static final int REMOVE_MODE = 1;

    /** Lines refresh five times a second; coordinates change as the player walks. */
    private static final int REFRESH_TICKS = 4;

    private final SeriesStatsRepository stats;
    private final Map<UUID, List<String>> lastLines = new ConcurrentHashMap<>();
    private Objective objective;
    private int refreshCounter;

    public SidebarHud(SeriesStatsRepository stats) {
        this.stats = stats;
    }

    public void onServerTick(MinecraftServer server) {
        if (++refreshCounter < REFRESH_TICKS) {
            return;
        }
        refreshCounter = 0;
        server.getPlayerList().getPlayers().forEach(player -> refresh(server, player));
    }

    /**
     * Declares the objective on this client and puts it in the sidebar slot.
     *
     * <p>Removed before it is added. The client throws if it is asked to create an
     * objective it already has, so a plain create is only safe on a client whose
     * scoreboard is empty; a remove for one it does not have is ignored. Getting
     * this wrong crashed the client every time the panel was reinstalled.
     */
    public void install(MinecraftServer server, ServerPlayer player) {
        Objective target = objective(server);
        player.connection.send(
                new ClientboundSetObjectivePacket(target, REMOVE_MODE));
        player.connection.send(
                new ClientboundSetObjectivePacket(target, ADD_MODE));
        player.connection.send(
                new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, target));
        lastLines.remove(player.getUUID());
    }

    public void forget(UUID uuid) {
        lastLines.remove(uuid);
    }

    /** Re-sends only the lines that changed since the last refresh. */
    private void refresh(MinecraftServer server, ServerPlayer player) {
        List<Component> lines = lines(server, player);
        List<String> rendered = lines.stream().map(Component::getString).toList();
        List<String> previous = lastLines.get(player.getUUID());

        for (int i = 0; i < lines.size(); i++) {
            if (previous != null && i < previous.size() && previous.get(i).equals(rendered.get(i))) {
                continue;
            }
            // The sidebar sorts by score descending, so the first line needs the
            // highest one for the panel to read top to bottom.
            player.connection.send(new ClientboundSetScorePacket(
                    holder(i), OBJECTIVE_NAME, lines.size() - i,
                    Optional.of(lines.get(i)), Optional.of(BlankFormat.INSTANCE)));
        }
        lastLines.put(player.getUUID(), rendered);
    }

    private List<Component> lines(MinecraftServer server, ServerPlayer player) {
        PlayerRole role = PlayerRole.fromDeaths(stats.deathsOf(player.getUUID()));
        BlockPos pos = player.blockPosition();

        List<Component> lines = new ArrayList<>();
        lines.add(entry("Jugador", Component.literal(player.getGameProfile().name())
                .withStyle(ChatFormatting.WHITE)));
        lines.add(entry("Rango", role.badge()));
        lines.add(entry("Muertes", Component.literal("☠ " + stats.deathsOf(player.getUUID()))
                .withStyle(ChatFormatting.RED)));
        lines.add(blank());
        lines.add(entry("XYZ", Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ())
                .withStyle(ChatFormatting.AQUA)));
        lines.add(entry("Mundo", Component.literal(dimension(player)).withStyle(ChatFormatting.GREEN)));
        lines.add(blank());
        lines.add(entry("Día", Component.literal(Long.toString(currentDay(server)))
                .withStyle(ChatFormatting.YELLOW)));
        lines.add(entry("Objetivo", Component.literal(stats.objective()).withStyle(ChatFormatting.GOLD)));
        return lines;
    }

    private static Component entry(String label, Component value) {
        // Grey on grey was almost unreadable over the world behind the panel, so
        // the labels are white and only the bullet is dimmed.
        return Component.literal("▪ ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(label + ": ").withStyle(ChatFormatting.WHITE))
                .append(value);
    }

    private static Component blank() {
        return Component.literal("");
    }

    private static String dimension(ServerPlayer player) {
        String path = player.level().dimension().identifier().getPath();
        return switch (path) {
            case "overworld" -> "Superficie";
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            default -> path;
        };
    }

    private static long currentDay(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld == null ? 1L : (overworld.getDefaultClockTime() / 24000L) + 1L;
    }

    /** One stable holder per row, so a row is updated rather than duplicated. */
    private static String holder(int index) {
        return "toque_line_" + index;
    }

    private Objective objective(MinecraftServer server) {
        if (objective == null) {
            objective = new Objective(server.getScoreboard(), OBJECTIVE_NAME,
                    ObjectiveCriteria.DUMMY,
                    Gradient.apply("☠ TOQUE HARDCORE ☠", Gradient.RED_FROM, Gradient.RED_TO, true),
                    ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
        }
        return objective;
    }
}
