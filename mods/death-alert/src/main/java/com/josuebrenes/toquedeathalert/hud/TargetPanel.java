package com.josuebrenes.toquedeathalert.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.josuebrenes.toquedeathalert.core.Gradient;
import com.josuebrenes.toquedeathalert.core.ToqueLog;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The box naming a target, built as a single line of text.
 *
 * <p>It is dressed like the side panel, which is the vanilla scoreboard: a dark
 * band with the name in the TOQUE red gradient, then the lines below it.
 *
 * <p>A boss bar title is one line, but the resource pack gives it fonts that draw
 * lower down: the box itself, the hearts, and copies of the default font shifted
 * to a second and third line. Each piece is drawn, and the pen is then walked
 * back to the left edge with negative spaces before the next one, so they stack
 * instead of running on. Walking back needs the exact width of everything drawn,
 * which is why the glyph advances and the Spanish entity names are tabulated
 * from the client by {@code resourcepack/build.py}.
 *
 * <p>The title ends exactly at the box's right edge, so the client centres the box.
 *
 * <p>The layout constants live in {@code build.py} too; the two change together.
 */
final class TargetPanel {
    private static final FontDescription BOX_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("toque", "hud_box"));
    private static final FontDescription NAME_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("toque", "hud_name"));
    private static final FontDescription HEART_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("toque", "hud_hearts"));
    private static final FontDescription INFO_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("toque", "hud_info"));

    private static final char BOX_LEFT = '';
    private static final char BOX_RIGHT = '';
    private static final char BOX_FILL = '';
    private static final int BOX_FILL_MAX_POWER = 7;
    private static final char HEART_FULL = '';
    private static final char HEART_HALF = '';
    private static final char HEART_EMPTY = '';
    private static final char BULLET = '';
    private static final char SPACE_BACK = '';
    private static final char SPACE_FORWARD = '';
    private static final int SPACE_MAX_POWER = 9;

    /** Box edges are 2 pixels wide; a bitmap glyph always advances one more. */
    private static final int EDGE = 2;
    private static final int PADDING = 5;
    private static final int HEART_ADVANCE = 10;
    /** Hearts overlap by a pixel, as in the vanilla health bar. */
    private static final int HEART_STEP = 8;
    private static final int HEART_WIDTH = 9;
    /** Past this many the row would run too wide; ten hearts then stand for the whole bar. */
    private static final int MAX_DRAWN_HEARTS = 15;
    private static final int SCALED_HEARTS = 10;
    private static final int FALLBACK_ADVANCE = 6;

    private static final Map<Character, Integer> WIDTHS = new HashMap<>();
    private static final Map<String, String> SPANISH_NAMES = new HashMap<>();

    static {
        JsonObject widths = load("glyph_widths.json");
        if (widths != null) {
            widths.entrySet().forEach(entry ->
                    WIDTHS.put(entry.getKey().charAt(0), entry.getValue().getAsInt()));
        }
        JsonObject names = load("entity_names_es.json");
        if (names != null) {
            names.entrySet().forEach(entry ->
                    SPANISH_NAMES.put(entry.getKey(), entry.getValue().getAsString()));
        }
    }

    private TargetPanel() {
    }

    static Component render(LivingEntity target) {
        String name = name(target);

        int hp = Mth.ceil(Math.max(0.0F, target.getHealth()));
        int max = Mth.ceil(Math.max(1.0F, target.getMaxHealth()));
        int slots = Mth.ceil(max / 2.0F);
        int halves = hp;
        if (slots > MAX_DRAWN_HEARTS) {
            // A Wither in hearts would cross the screen; ten scaled hearts read the
            // same at a glance, and the line below carries the exact numbers.
            slots = SCALED_HEARTS;
            halves = Math.round((float) hp / max * SCALED_HEARTS * 2);
            if (halves == 0 && hp > 0) {
                halves = 1;
            }
        }

        String label = "Vida: ";
        String value = hp + " / " + max;
        // Bold draws every glyph a pixel wider.
        int nameWidth = width(name) + name.length();
        int heartsWidth = (slots - 1) * HEART_STEP + HEART_WIDTH;
        int infoWidth = width(BULLET + " " + label + value);
        int content = Math.max(heartsWidth, Math.max(nameWidth, infoWidth));
        int boxWidth = content + PADDING * 2;

        MutableComponent panel = Component.empty();
        box(panel, boxWidth);

        int nameLeft = PADDING + (content - nameWidth) / 2;
        panel.append(space(-(boxWidth - nameLeft)));
        panel.append(Component.empty().setStyle(Style.EMPTY.withFont(NAME_FONT))
                .append(Gradient.apply(name, Gradient.RED_FROM, Gradient.RED_TO, true)));
        panel.append(space(-(nameLeft + nameWidth - PADDING)));

        hearts(panel, halves, slots);
        panel.append(space(-slots * HEART_STEP));

        panel.append(Component.literal(BULLET + " ").setStyle(Style.EMPTY.withFont(INFO_FONT)
                .withColor(ChatFormatting.GRAY)));
        panel.append(Component.literal(label).setStyle(Style.EMPTY.withFont(INFO_FONT)
                .withColor(ChatFormatting.WHITE)));
        panel.append(Component.literal(value).setStyle(Style.EMPTY.withFont(INFO_FONT)
                .withColor(ChatFormatting.RED)));
        panel.append(space(boxWidth - PADDING - infoWidth));
        return panel;
    }

    /** The frame, from the left edge to exactly {@code width} pixels on. */
    private static void box(MutableComponent panel, int width) {
        StringBuilder glyphs = new StringBuilder();
        glyphs.append(BOX_LEFT).append(back(1));
        int inside = width - EDGE * 2;
        int top = 1 << BOX_FILL_MAX_POWER;
        while (inside >= top) {
            glyphs.append((char) (BOX_FILL + BOX_FILL_MAX_POWER)).append(back(1));
            inside -= top;
        }
        for (int power = BOX_FILL_MAX_POWER - 1; power >= 0; power--) {
            if ((inside & (1 << power)) != 0) {
                glyphs.append((char) (BOX_FILL + power)).append(back(1));
            }
        }
        glyphs.append(BOX_RIGHT).append(back(1));
        panel.append(glyph(glyphs.toString(), BOX_FONT));
    }

    /** One heart per two halves, as vanilla draws the player's own. */
    private static void hearts(MutableComponent panel, int hp, int slots) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < slots; i++) {
            int halves = hp - i * 2;
            row.append(halves >= 2 ? HEART_FULL : halves == 1 ? HEART_HALF : HEART_EMPTY);
            // Each heart advances 10; stepping back 2 lays them 8 apart.
            row.append((char) (SPACE_BACK + 1));
        }
        panel.append(glyph(row.toString(), HEART_FONT));
    }

    private static Component glyph(String glyphs, FontDescription font) {
        return Component.literal(glyphs).setStyle(Style.EMPTY.withFont(font).withColor(ChatFormatting.WHITE)
                .withItalic(false));
    }

    /** Moves the pen by {@code pixels}, left when negative. */
    private static Component space(int pixels) {
        return glyph(pixels < 0 ? back(-pixels) : forward(pixels), BOX_FONT);
    }

    private static String back(int pixels) {
        return spaces(pixels, SPACE_BACK);
    }

    private static String forward(int pixels) {
        return spaces(pixels, SPACE_FORWARD);
    }

    private static String spaces(int pixels, char base) {
        StringBuilder out = new StringBuilder();
        int top = 1 << SPACE_MAX_POWER;
        while (pixels >= top) {
            out.append((char) (base + SPACE_MAX_POWER));
            pixels -= top;
        }
        for (int power = SPACE_MAX_POWER - 1; power >= 0; power--) {
            if ((pixels & (1 << power)) != 0) {
                out.append((char) (base + power));
            }
        }
        return out.toString();
    }

    static int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += WIDTHS.getOrDefault(text.charAt(i), FALLBACK_ADVANCE);
        }
        return width;
    }

    /**
     * The name the client would show. The server only has English, so a mob
     * without a custom name is looked up in the Spanish table; the text has to be
     * known here, exactly, to be measured.
     */
    private static String name(LivingEntity target) {
        if (target instanceof Player player) {
            return player.getGameProfile().name();
        }
        if (target.hasCustomName()) {
            return target.getCustomName().getString();
        }
        String spanish = SPANISH_NAMES.get(target.getType().getDescriptionId());
        return spanish != null ? spanish : target.getName().getString();
    }

    private static JsonObject load(String file) {
        String path = "/toque-death-alert/hud/" + file;
        try (InputStream stream = TargetPanel.class.getResourceAsStream(path)) {
            if (stream == null) {
                ToqueLog.warn("Missing {}; the target panel will be misaligned.", path);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception exception) {
            ToqueLog.warn("Could not read {}: {}", path, exception.toString());
            return null;
        }
    }
}
