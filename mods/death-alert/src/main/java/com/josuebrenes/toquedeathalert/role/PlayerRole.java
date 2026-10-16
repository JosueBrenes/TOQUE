package com.josuebrenes.toquedeathalert.role;

import com.josuebrenes.toquedeathalert.core.Gradient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Automatic TOQUE status derived only from the player's current-series death count.
 * No role is stored separately, so it can never become out of sync with the counter.
 *
 * <p>Each rank carries a sigil and its own two colour fade rather than one flat
 * colour, so a rank reads as a badge and the climb from INTACTO to HIJO DE LA
 * MUERTE is visible at a glance instead of being a word in a different colour.
 */
public enum PlayerRole {
    INTACTO(0, "INTACTO", "✦", ChatFormatting.AQUA, 0x8FF3FF, 0x1E9BC4),
    SOBREVIVIENTE(1, "SOBREVIVIENTE", "❂", ChatFormatting.GREEN, 0xA8FF9E, 0x2F9E33),
    MARCADO(2, "MARCADO", "❖", ChatFormatting.YELLOW, 0xFFF2A0, 0xD9A320),
    CONDENADO(3, "CONDENADO", "✖", ChatFormatting.GOLD, 0xFFC663, 0xC96A10),
    DELINCUENTE(4, "DELINCUENTE", "⚔", ChatFormatting.RED, 0xFF8A7A, 0xA30D0D),
    ESPECTRO(5, "ESPECTRO", "☠", ChatFormatting.LIGHT_PURPLE, 0xFFA8E8, 0x9A2BA0),
    HIJO_DE_LA_MUERTE(6, "HIJO DE LA MUERTE", "☠", ChatFormatting.DARK_PURPLE, 0xC98BFF, 0x4B1170);

    private final int minimumDeaths;
    private final String label;
    private final String sigil;
    private final ChatFormatting formatting;
    private final int gradientFrom;
    private final int gradientTo;

    PlayerRole(int minimumDeaths, String label, String sigil, ChatFormatting formatting,
               int gradientFrom, int gradientTo) {
        this.minimumDeaths = minimumDeaths;
        this.label = label;
        this.sigil = sigil;
        this.formatting = formatting;
        this.gradientFrom = gradientFrom;
        this.gradientTo = gradientTo;
    }

    public String label() {
        return label;
    }

    public String sigil() {
        return sigil;
    }

    public ChatFormatting formatting() {
        return formatting;
    }

    /** The rank as plain text, sigil included, for measuring and for logs. */
    public String plain() {
        return sigil + " " + label;
    }

    /** The rank as a badge: sigil and name, faded across the rank's own colours. */
    public MutableComponent badge() {
        return Gradient.apply(plain(), gradientFrom, gradientTo, true);
    }

    /** The badge in brackets, for sitting in front of a player name. */
    public MutableComponent tag() {
        return Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(badge())
                .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static PlayerRole fromDeaths(int deaths) {
        int safeDeaths = Math.max(0, deaths);
        PlayerRole selected = INTACTO;
        for (PlayerRole role : values()) {
            if (safeDeaths >= role.minimumDeaths) {
                selected = role;
            } else {
                break;
            }
        }
        return selected;
    }
}
