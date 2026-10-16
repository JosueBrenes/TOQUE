package com.josuebrenes.toquedeathalert.tab;

import com.josuebrenes.toquedeathalert.core.FontWidth;
import com.josuebrenes.toquedeathalert.core.Gradient;
import com.josuebrenes.toquedeathalert.role.PlayerRole;
import com.josuebrenes.toquedeathalert.series.SeriesStatsRepository;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.Locale;

/**
 * Draws the TOQUE player list: a two line banner, the player rows, and the
 * objective underneath.
 *
 * <p>The role is derived automatically from the current TOQUE death counter.
 * Nothing is stored separately, so the role always follows the death count.
 */
public final class TabRowRenderer {
    private static final String SKULL = "☠";
    private static final String SEPARATOR = "   ·   ";

    /** Column positions inside a player row, in pixels. */
    private static final int NAME_COLUMN_PX = 84;
    private static final int ROLE_COLUMN_PX = 176;
    private static final int HEALTH_COLUMN_PX = 108;

    private static final long TICKS_PER_DAY = 24000L;

    private final SeriesStatsRepository stats;

    public TabRowRenderer(SeriesStatsRepository stats) {
        this.stats = stats;
    }

    public Component row(ServerPlayer player) {
        int deaths = stats.deathsOf(player.getUUID());

        String name = FontWidth.padTo(player.getGameProfile().name(), NAME_COLUMN_PX);

        PlayerRole role = PlayerRole.fromDeaths(deaths);
        Component roleText = role.badge();

        String roleGap = FontWidth.spaces(
                Math.max(4, ROLE_COLUMN_PX - FontWidth.of(roleText.getString()))
        );

        Component health = HeartBar.render(player.getHealth(), player.getMaxHealth());
        String healthGap = FontWidth.spaces(
                Math.max(4, HEALTH_COLUMN_PX - FontWidth.of(health.getString()))
        );

        return Component.literal(name).withStyle(ChatFormatting.WHITE)
                .append(roleText)
                .append(Component.literal(roleGap))
                .append(health)
                .append(Component.literal(healthGap))
                .append(deaths(stats.deathsOf(player.getUUID())));
    }

    /** The name, then where the series stands, in the same shape as the server list. */
    public Component header(MinecraftServer server) {
        MutableComponent progress = Component.empty();

        long day = currentDay(server);
        if (day > 0L) {
            progress.append(Component.literal("DÍA " + day).withStyle(ChatFormatting.GOLD))
                    .append(separator());
        }
        progress.append(deaths(stats.totalDeaths()));

        return Component.literal("\n")
                .append(Gradient.apply(SKULL + " TOQUE HARDCORE " + SKULL,
                        Gradient.RED_FROM, Gradient.RED_TO, true))
                .append(Component.literal("\n"))
                .append(progress)
                .append(Component.literal("\n"));
    }

    public Component footer(MinecraftServer server) {
        return Component.literal("\n")
                .append(Gradient.apply(stats.objective().toUpperCase(Locale.ROOT),
                        Gradient.GOLD_FROM, Gradient.GOLD_TO, true))
                .append(Component.literal("\n"));
    }

    /** Current day of the world. */
    private static long currentDay(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld == null ? -1L : (overworld.getDefaultClockTime() / TICKS_PER_DAY) + 1L;
    }

    private static Component separator() {
        return Component.literal(SEPARATOR).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static Component deaths(int count) {
        MutableComponent text = Component.literal(SKULL + " ").withStyle(ChatFormatting.DARK_RED);
        return text.append(Component.literal(Integer.toString(count))
                .withStyle(count == 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}
