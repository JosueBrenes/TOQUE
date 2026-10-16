package com.josuebrenes.toquedeathalert.role;

import com.josuebrenes.toquedeathalert.core.Gradient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Automatic TOQUE status derived only from the player's current-series death count.
 * No role is stored separately, so it can never become out of sync with the counter.
 *
 * <p>Each rank carries its own two colour fade rather than one flat colour, and
 * no symbol, so a rank reads as a badge and the climb from INTACTO to REY DEL RESPAWN
 * is visible at a glance instead of being a word in a different colour.
 *
 * <p>Players respawn, so deaths pile up: the serious ranks are mixed with joke
 * ones to keep the ladder going. Every label must stay no wider than
 * HIJO DE LA MUERTE, or the player list column after it stops lining up.
 */
public enum PlayerRole {
    INTACTO(0, "INTACTO", ChatFormatting.AQUA, 0x8FF3FF, 0x1E9BC4),
    SOBREVIVIENTE(1, "SOBREVIVIENTE", ChatFormatting.GREEN, 0xA8FF9E, 0x2F9E33),
    TORPE(2, "TORPE", ChatFormatting.GREEN, 0xD4FF8A, 0x6FA81E),
    MARCADO(3, "MARCADO", ChatFormatting.YELLOW, 0xFFF2A0, 0xD9A320),
    PAYASO(4, "PAYASO", ChatFormatting.LIGHT_PURPLE, 0xFF7AD9, 0x3FA9FF),
    CONDENADO(5, "CONDENADO", ChatFormatting.GOLD, 0xFFC663, 0xC96A10),
    MANCO(6, "MANCO", ChatFormatting.GOLD, 0xFFB38A, 0xB0582B),
    DELINCUENTE(7, "DELINCUENTE", ChatFormatting.RED, 0xFF8A7A, 0xA30D0D),
    KAMIKAZE(9, "KAMIKAZE", ChatFormatting.RED, 0xFFD15C, 0xE0301E),
    ESPECTRO(11, "ESPECTRO", ChatFormatting.LIGHT_PURPLE, 0xFFA8E8, 0x9A2BA0),
    IMAN_DE_CREEPERS(13, "IMÁN DE CREEPERS", ChatFormatting.GREEN, 0x9DFF6B, 0x1F7A1F),
    BUFON_DEL_SERVER(16, "BUFÓN DEL SERVER", ChatFormatting.LIGHT_PURPLE, 0xFFE066, 0xD13BD1),
    HIJO_DE_LA_MUERTE(20, "HIJO DE LA MUERTE", ChatFormatting.DARK_PURPLE, 0xC98BFF, 0x4B1170),
    PAYASO_SUPREMO(25, "PAYASO SUPREMO", ChatFormatting.LIGHT_PURPLE, 0xFF4FA3, 0xFFD23F),
    REY_DEL_RESPAWN(30, "REY DEL RESPAWN", ChatFormatting.GOLD, 0xFFF3A0, 0xC9A000);

    private final int minimumDeaths;
    private final String label;
    private final ChatFormatting formatting;
    private final int gradientFrom;
    private final int gradientTo;

    PlayerRole(int minimumDeaths, String label, ChatFormatting formatting,
               int gradientFrom, int gradientTo) {
        this.minimumDeaths = minimumDeaths;
        this.label = label;
        this.formatting = formatting;
        this.gradientFrom = gradientFrom;
        this.gradientTo = gradientTo;
    }

    public String label() {
        return label;
    }

    public ChatFormatting formatting() {
        return formatting;
    }

    /** The rank as plain text, for measuring and for logs. */
    public String plain() {
        return label;
    }

    /** The rank as a badge: the name, faded across the rank's own colours. */
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
