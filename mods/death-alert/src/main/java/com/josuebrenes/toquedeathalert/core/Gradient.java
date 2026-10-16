package com.josuebrenes.toquedeathalert.core;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Fades a line of text from one colour to another, a character at a time.
 *
 * <p>Flat named colours look plain next to a server icon. Every client since 1.16
 * understands the full RGB range in a chat component, so this needs nothing
 * installed and works the same in the server list and in the player list.
 */
public final class Gradient {
    /** TOQUE red: light at the start, deep at the end. */
    public static final int RED_FROM = 0xFF8A7A;
    public static final int RED_TO = 0xA30D0D;

    /** TOQUE gold: used for the numbers that go with the red. */
    public static final int GOLD_FROM = 0xFFD257;
    public static final int GOLD_TO = 0xD1761B;

    private Gradient() {
    }

    public static MutableComponent apply(String text, int from, int to, boolean bold) {
        MutableComponent result = Component.empty();
        int last = Math.max(1, text.length() - 1);

        for (int i = 0; i < text.length(); i++) {
            int colour = blend(from, to, (float) i / last);
            result.append(Component.literal(String.valueOf(text.charAt(i)))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(colour)).withBold(bold)));
        }
        return result;
    }

    private static int blend(int from, int to, float ratio) {
        return (channel(from, to, 16, ratio) << 16)
                | (channel(from, to, 8, ratio) << 8)
                | channel(from, to, 0, ratio);
    }

    private static int channel(int from, int to, int shift, float ratio) {
        int start = (from >> shift) & 0xFF;
        int end = (to >> shift) & 0xFF;
        return Math.round(start + (end - start) * ratio);
    }
}
