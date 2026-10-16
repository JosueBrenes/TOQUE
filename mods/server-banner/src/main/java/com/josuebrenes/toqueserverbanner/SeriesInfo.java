package com.josuebrenes.toqueserverbanner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.multiplayer.ServerData;

/**
 * The Try and day numbers shown on the banner.
 *
 * <p>The multiplayer list only ever receives what the server status reply carries:
 * the MOTD, the player counts, the version and the favicon. There is no channel
 * for anything else, and this mod is client side only, so the numbers are read out
 * of the MOTD. Put something like {@code TRY #7 - DIA 24} in the server MOTD and
 * the banner picks it up; leave it out and the banner simply omits those fields.
 *
 * @param tryNumber the Try, or -1 when the MOTD does not mention one
 * @param day       the day, or -1 when the MOTD does not mention one
 */
public record SeriesInfo(int tryNumber, int day) {
    private static final SeriesInfo UNKNOWN = new SeriesInfo(-1, -1);

    private static final Pattern TRY_PATTERN =
            Pattern.compile("TRY\\s*#?\\s*(\\d{1,4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAY_PATTERN =
            Pattern.compile("(?:D[IÍ]A|DAY)\\s*#?\\s*(\\d{1,5})", Pattern.CASE_INSENSITIVE);

    public static SeriesInfo from(ServerData server) {
        if (server == null || server.motd == null) {
            return UNKNOWN;
        }
        String motd = server.motd.getString();
        return new SeriesInfo(match(TRY_PATTERN, motd), match(DAY_PATTERN, motd));
    }

    public boolean hasTry() {
        return tryNumber > 0;
    }

    public boolean hasDay() {
        return day > 0;
    }

    private static int match(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException impossible) {
            return -1;
        }
    }
}
