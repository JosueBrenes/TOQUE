package com.josuebrenes.toquedeathalert.tab;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Renders a health bar for the player list.
 *
 * <p>The ten slots are scaled to the player's actual maximum health, so attribute
 * or effect changes still read correctly instead of pretending everyone has 20 HP.
 * The default font has no half-heart glyph, so a half is shown as a gold heart.
 * The exact numbers are appended only when the maximum is not the usual 20, where
 * ten hearts would otherwise be misleading.
 */
public final class HeartBar {
    private static final int SLOTS = 10;
    private static final float VANILLA_MAX_HEALTH = 20.0F;
    private static final String FULL = "♥";
    private static final String EMPTY = "♡";

    private HeartBar() {
    }

    public static Component render(float health, float maxHealth) {
        float safeMax = maxHealth <= 0.0F ? 20.0F : maxHealth;
        float current = Math.max(0.0F, Math.min(health, safeMax));

        int halves = Math.round((current / safeMax) * (SLOTS * 2));
        if (halves == 0 && current > 0.0F) {
            halves = 1; // never show a living player as completely empty
        }
        int full = halves / 2;
        boolean half = (halves % 2) == 1;
        int empty = SLOTS - full - (half ? 1 : 0);

        MutableComponent bar = Component.empty();
        if (full > 0) {
            bar.append(Component.literal(FULL.repeat(full)).withStyle(ChatFormatting.RED));
        }
        if (half) {
            bar.append(Component.literal(FULL).withStyle(ChatFormatting.GOLD));
        }
        if (empty > 0) {
            bar.append(Component.literal(EMPTY.repeat(empty)).withStyle(ChatFormatting.DARK_GRAY));
        }
        // On a normal 20 HP bar the hearts already say it, and the numbers were
        // just noise on every row. They earn their place only when a potion or an
        // attribute has moved the maximum, where ten hearts no longer mean 20 HP.
        if (safeMax != VANILLA_MAX_HEALTH) {
            bar.append(Component.literal(" " + number(current) + "/" + number(safeMax))
                    .withStyle(ChatFormatting.GRAY));
        }
        return bar;
    }

    private static String number(float value) {
        float rounded = Math.round(value * 10.0F) / 10.0F;
        return rounded == Math.floor(rounded)
                ? Integer.toString((int) rounded)
                : String.format(Locale.ROOT, "%.1f", rounded);
    }
}
