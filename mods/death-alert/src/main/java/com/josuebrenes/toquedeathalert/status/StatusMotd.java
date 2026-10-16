package com.josuebrenes.toquedeathalert.status;

import com.josuebrenes.toquedeathalert.ToqueDeathAlert;
import com.josuebrenes.toquedeathalert.core.FontWidth;
import com.josuebrenes.toquedeathalert.core.Gradient;
import com.josuebrenes.toquedeathalert.series.SeriesStatsRepository;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The two MOTD lines shown under the server name in the multiplayer list.
 *
 * <p>This is the whole of the design surface a server has on that screen. The
 * list is drawn by the client before it ever connects and the status reply
 * carries only the MOTD, the player counts, the version and the 64x64 favicon:
 * no frame, no background, no layout. Everything here works on a plain vanilla
 * client with nothing installed.
 *
 * <p>The client draws the MOTD left aligned and wraps it at {@code rowWidth - 34},
 * which is 305 - 34 pixels, so centring has to be done here by padding. Both
 * lines are measured in pixels rather than characters, because the font is
 * proportional and counting characters would leave them visibly off centre.
 *
 * <p>Rebuilt on every ping, so the day and the death count are current
 * each time a player refreshes their list.
 */
public final class StatusMotd {
    /** Width the client wraps the MOTD at: the row is 305 wide, less icon and gap. */
    private static final int MOTD_WIDTH_PX = 271;

    private static final String SKULL = "☠";

    private static final long TICKS_PER_DAY = 24000L;
    private static final long TAGLINE_MILLIS = 4000L;

    private static final String[] TAGLINES = {
            "SOBREVIVE, EXPLORA, CONSTRUYE",
            "CADA NOCHE CUENTA",
            "¿CUÁNTO AGUANTARÁS?",
            "MUERE Y VE POR TUS COSAS"
    };

    private StatusMotd() {
    }

    /** The replacement description, or null to leave the vanilla MOTD alone. */
    @Nullable
    public static Component build(MinecraftServer server) {
        SeriesStatsRepository stats = ToqueDeathAlert.runtime().stats();
        if (stats == null) {
            return null;
        }

        return centred(titleLine(server, stats), Gradient.RED_FROM, Gradient.RED_TO)
                .append(Component.literal("\n"))
                .append(centred(deathLine(stats), Gradient.GOLD_FROM, Gradient.GOLD_TO));
    }

    /**
     * The name, with where the series stands in brackets after it.
     *
     * <p>Written tight rather than l e t t e r  s p a c e d: the spaced form read
     * as a row of loose glyphs instead of a word, and it cost so much width that
     * nothing else fitted beside it.
     */
    private static String titleLine(MinecraftServer server, SeriesStatsRepository stats) {
        StringBuilder line = new StringBuilder(SKULL + " TOQUE HARDCORE");

        long day = currentDay(server);
        if (day > 0L) {
            line.append(" [DÍA ").append(day).append(']');
        }
        return line.append(' ').append(SKULL).toString();
    }

    /**
     * The toll so far and a tagline that changes every few seconds, with the
     * tagline dropped rather than clipped if the numbers ever fill the row.
     */
    private static String deathLine(SeriesStatsRepository stats) {
        int deaths = stats.totalDeaths();
        String toll = SKULL + " " + deaths + (deaths == 1 ? " MUERTE" : " MUERTES");

        String withTagline = toll + "  ·  " + tagline();
        return FontWidth.of(withTagline, true) <= MOTD_WIDTH_PX ? withTagline : toll;
    }

    /** Centres the line in the MOTD area and fades it from one colour to the other. */
    private static MutableComponent centred(String text, int from, int to) {
        return Component.literal(FontWidth.centre(text, MOTD_WIDTH_PX, true))
                .append(Gradient.apply(text, from, to, true));
    }

    private static long currentDay(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld == null ? -1L : (overworld.getDefaultClockTime() / TICKS_PER_DAY) + 1L;
    }

    private static String tagline() {
        int index = (int) ((System.currentTimeMillis() / TAGLINE_MILLIS) % TAGLINES.length);
        return TAGLINES[index];
    }
}
