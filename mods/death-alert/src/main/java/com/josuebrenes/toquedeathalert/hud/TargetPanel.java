package com.josuebrenes.toquedeathalert.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.josuebrenes.toquedeathalert.core.ToqueLog;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The box naming a target, built as a single line of text.
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
    private static final Identifier BOX_FONT = Identifier.of("toque", "hud_box");
    private static final Identifier NAME_FONT = Identifier.of("toque", "hud_name");
    private static final Identifier HEART_FONT = Identifier.of("toque", "hud_hearts");
    private static final Identifier MOD_FONT = Identifier.of("toque", "hud_mod");

    private static final char BOX_LEFT = '';
    private static final char BOX_RIGHT = '';
    private static final char BOX_FILL = '';
    private static final int BOX_FILL_MAX_POWER = 7;
    private static final char HEART_FULL = '';
    private static final char HEART_HALF = '';
    private static final char HEART_EMPTY = '';
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
    /** Past this many the row would be wider than the screen allows; a count is shown. */
    private static final int MAX_DRAWN_HEARTS = 15;

    private static final int MOD_NAME_COLOR = 0x5555FF;
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

    static Text render(LivingEntity target) {
        String name = name(target);
        String modName = modName(target);

        int hp = MathHelper.ceil(Math.max(0.0F, target.getHealth()));
        int max = MathHelper.ceil(Math.max(1.0F, target.getMaxHealth()));
        int slots = MathHelper.ceil(max / 2.0F);
        boolean compact = slots > MAX_DRAWN_HEARTS;
        String count = " " + hp + " / " + max;

        int heartsWidth = compact
                ? HEART_ADVANCE + width(count)
                : (slots - 1) * HEART_STEP + HEART_WIDTH;
        int content = Math.max(heartsWidth, Math.max(width(name), width(modName)));
        int boxWidth = content + PADDING * 2;

        MutableText panel = Text.empty();
        box(panel, boxWidth);
        panel.append(space(-(boxWidth - PADDING)));

        panel.append(Text.literal(name).setStyle(Style.EMPTY.withFont(NAME_FONT)
                .withColor(Formatting.WHITE)));
        panel.append(space(-width(name)));

        if (compact) {
            panel.append(glyph(String.valueOf(HEART_FULL), HEART_FONT));
            panel.append(Text.literal(count).setStyle(Style.EMPTY.withFont(HEART_FONT)
                    .withColor(Formatting.WHITE)));
            panel.append(space(-(HEART_ADVANCE + width(count))));
        } else {
            hearts(panel, hp, slots);
            panel.append(space(-slots * HEART_STEP));
        }

        panel.append(Text.literal(modName).setStyle(Style.EMPTY.withFont(MOD_FONT)
                .withColor(MOD_NAME_COLOR).withItalic(true)));
        panel.append(space(boxWidth - PADDING - width(modName)));
        return panel;
    }

    /** The frame, from the left edge to exactly {@code width} pixels on. */
    private static void box(MutableText panel, int width) {
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

    /** One heart per 2 HP, as vanilla draws the player's own. */
    private static void hearts(MutableText panel, int hp, int slots) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < slots; i++) {
            int halves = hp - i * 2;
            row.append(halves >= 2 ? HEART_FULL : halves == 1 ? HEART_HALF : HEART_EMPTY);
            // Each heart advances 10; stepping back 2 lays them 8 apart.
            row.append((char) (SPACE_BACK + 1));
        }
        panel.append(glyph(row.toString(), HEART_FONT));
    }

    private static Text glyph(String glyphs, Identifier font) {
        return Text.literal(glyphs).setStyle(Style.EMPTY.withFont(font).withColor(Formatting.WHITE)
                .withItalic(false));
    }

    /** Moves the pen by {@code pixels}, left when negative. */
    private static Text space(int pixels) {
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
        if (target instanceof PlayerEntity player) {
            return player.getGameProfile().getName();
        }
        if (target.hasCustomName()) {
            return target.getCustomName().getString();
        }
        String spanish = SPANISH_NAMES.get(target.getType().getTranslationKey());
        return spanish != null ? spanish : target.getName().getString();
    }

    /** "Minecraft" for vanilla mobs, otherwise the name of the mod that adds it. */
    private static String modName(LivingEntity target) {
        String namespace = EntityType.getId(target.getType()).getNamespace();
        return FabricLoader.getInstance().getModContainer(namespace)
                .map(mod -> mod.getMetadata().getName())
                .orElse(namespace);
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
