"""Builds the TOQUE resource pack and the font data the server mod lays it out with.

The target panel (the box naming the mob a player looks at) is drawn by a vanilla
client from nothing but a boss bar title. Everything it needs comes from here:

- fonts that draw the panel's box, the hearts, and copies of the default font
  shifted down so the panel can hold three lines in a one-line title; the look
  follows the side panel, which is the vanilla scoreboard;
- see-through green boss bar sprites, so the bar under the title is not drawn;
- the advance of every default-font glyph, which the server needs to step back
  and forth inside the title with negative spaces;
- the Spanish entity names, because the server only knows English and has to
  measure the exact text it sends.

The default-font glyphs and the Spanish names are read from a local Minecraft
1.21.1 install; nothing of Mojang's is copied into the pack itself, which only
references the client's own textures.

    python resourcepack/build.py

Rebuilding changes the zip's hash, so `resource-pack-sha1` in server.properties
has to be updated to the value printed at the end.
"""

import glob
import hashlib
import io
import json
import os
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.join(HERE, "pack")
ZIP = os.path.join(HERE, "TOQUE_Death_ResourcePack.zip")
MOD_DATA = os.path.join(HERE, "..", "mods", "death-alert", "src", "main", "resources",
                        "toque-death-alert", "hud")

# --- Layout. Mirrored in TargetPanel.java; change both together. ------------------
# Offsets are pixels below the top of the boss bar title line.
BOX_TOP = 2
BOX_HEIGHT = 37
HEADER_HEIGHT = 11          # the darker band the name sits in
NAME_TOP = 4
HEART_TOP = 16
INFO_TOP = 27
BULLET_TOP = 29

BOX_LEFT, BOX_RIGHT = 0xE000, 0xE001
BOX_FILL = 0xE010            # + k draws a 2^k wide strip, k = 0..7
HEART_FULL, HEART_HALF, HEART_EMPTY = 0xE100, 0xE101, 0xE102
BULLET = 0xE200
SPACE_BACK = 0xF000          # + k moves back 2^k pixels, k = 0..9
SPACE_FORWARD = 0xF100       # + k moves forward 2^k pixels

# The side panel is the vanilla scoreboard: black at 40% behind its title and 30%
# behind its lines. A boss bar title is drawn twice, its shadow first, and the box
# is part of the title, so each layer here is lighter to land on the same shade.
HEADER = (0, 0, 0, 58)
BODY = (0, 0, 0, 42)
ACCENT = (163, 13, 13, 255)  # the dark end of the TOQUE red gradient


def find_client_jar():
    candidates = glob.glob(os.path.expanduser(
        "~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar"))
    if not candidates:
        raise SystemExit("Minecraft 1.21.1 client jar not found; run ./gradlew build first.")
    return candidates[0]


def find_asset(name):
    """An object from a launcher's asset store, located through its 1.21.1 index."""
    roots = [os.path.expandvars(r"$APPDATA/.minecraft/assets"),
             os.path.expandvars(r"$APPDATA/ModrinthApp/meta/assets"),
             os.path.expanduser("~/.minecraft/assets")]
    for root in roots:
        index = os.path.join(root, "indexes", "17.json")
        if not os.path.exists(index):
            continue
        objects = json.load(open(index, encoding="utf-8"))["objects"]
        if name in objects:
            digest = objects[name]["hash"]
            path = os.path.join(root, "objects", digest[:2], digest)
            if os.path.exists(path):
                return path
    raise SystemExit(f"{name} not found in any launcher's assets; start Minecraft 1.21.1 once.")


def save(image, *path):
    full = os.path.join(PACK, *path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    image.save(full)


def write_json(data, *path):
    full = os.path.join(*path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as out:
        json.dump(data, out, ensure_ascii=False, indent=2)
        out.write("\n")


# --- The box ---------------------------------------------------------------------

def box_column():
    """One column of the box: the header band, a red rule under it, the body."""
    column = []
    for y in range(BOX_HEIGHT):
        if y < HEADER_HEIGHT:
            column.append(HEADER)
        elif y == HEADER_HEIGHT:
            column.append(ACCENT)
        else:
            column.append(BODY)
    return column


def strip(width):
    image = Image.new("RGBA", (width, BOX_HEIGHT))
    for x in range(width):
        for y, pixel in enumerate(box_column()):
            image.putpixel((x, y), pixel)
    return image


def build_box_font():
    # The edges are kept as their own glyphs so the shape can change without the
    # server's arithmetic changing; today they are plain strips like the fill.
    save(strip(2), "assets", "toque", "textures", "font", "box_left.png")
    save(strip(2), "assets", "toque", "textures", "font", "box_right.png")
    providers = [
        {"type": "bitmap", "file": "toque:font/box_left.png", "ascent": 7 - BOX_TOP,
         "height": BOX_HEIGHT, "chars": [chr(BOX_LEFT)]},
        {"type": "bitmap", "file": "toque:font/box_right.png", "ascent": 7 - BOX_TOP,
         "height": BOX_HEIGHT, "chars": [chr(BOX_RIGHT)]},
    ]
    for k in range(8):
        width = 1 << k
        save(strip(width), "assets", "toque", "textures", "font", f"box_fill_{width}.png")
        providers.append({"type": "bitmap", "file": f"toque:font/box_fill_{width}.png",
                          "ascent": 7 - BOX_TOP, "height": BOX_HEIGHT, "chars": [chr(BOX_FILL + k)]})
    providers.append(pen_moves())
    write_json({"providers": providers}, PACK, "assets", "toque", "font", "hud_box.json")


def pen_moves():
    """Spaces that move the pen 2^k pixels back or forward without drawing."""
    advances = {}
    for k in range(10):
        advances[chr(SPACE_BACK + k)] = -(1 << k)
        advances[chr(SPACE_FORWARD + k)] = 1 << k
    return {"type": "space", "advances": advances}


# --- Hearts and lines ----------------------------------------------------------------------

HEART_SHAPE = [
    ".KKK.KKK.",
    "KRRRKRRRK",
    "KRWRRRRRK",
    "KRRRRRRRK",
    ".KRRRRRK.",
    "..KRRRK..",
    "...KRK...",
    "....K....",
]
OUTLINE = (24, 0, 0, 255)
RED = (220, 24, 24, 255)
SHINE = (255, 200, 200, 255)
EMPTY = (60, 16, 16, 255)


def heart(fill_columns):
    image = Image.new("RGBA", (9, 9))
    for y, row in enumerate(HEART_SHAPE):
        for x, cell in enumerate(row):
            if cell == "K":
                image.putpixel((x, y), OUTLINE)
            elif cell in "RW":
                if x < fill_columns:
                    image.putpixel((x, y), SHINE if cell == "W" else RED)
                else:
                    image.putpixel((x, y), EMPTY)
    return image


def shifted_default_providers(default_providers, top):
    """The default font's bitmap pages, moved down so their top sits at `top`."""
    shifted = [{"type": "reference", "id": "minecraft:include/space"}]
    for provider in default_providers:
        moved = dict(provider)
        moved["ascent"] = provider["ascent"] - top
        shifted.append(moved)
    return shifted


def build_line_fonts(default_providers):
    save(heart(9), "assets", "toque", "textures", "font", "heart_full.png")
    save(heart(5), "assets", "toque", "textures", "font", "heart_half.png")
    save(heart(0), "assets", "toque", "textures", "font", "heart_empty.png")

    # The pen moves are here too: the row of hearts steps back between hearts
    # without leaving this font.
    hearts = [pen_moves()]
    for char, name in ((HEART_FULL, "full"), (HEART_HALF, "half"), (HEART_EMPTY, "empty")):
        hearts.append({"type": "bitmap", "file": f"toque:font/heart_{name}.png",
                       "ascent": 7 - HEART_TOP, "height": 9, "chars": [chr(char)]})
    write_json({"providers": hearts}, PACK, "assets", "toque", "font", "hud_hearts.json")
    write_json({"providers": shifted_default_providers(default_providers, NAME_TOP)},
               PACK, "assets", "toque", "font", "hud_name.json")
    # The side panel's "▪" comes from unifont, which cannot be moved down a line,
    # so the info line draws a square of its own; the text colour tints it.
    save(Image.new("RGBA", (3, 3), (255, 255, 255, 255)), "assets", "toque", "textures", "font", "bullet.png")
    info = shifted_default_providers(default_providers, INFO_TOP)
    info.append({"type": "bitmap", "file": "toque:font/bullet.png",
                 "ascent": 7 - BULLET_TOP, "height": 3, "chars": [chr(BULLET)]})
    write_json({"providers": info}, PACK, "assets", "toque", "font", "hud_info.json")


# --- Data for the server ---------------------------------------------------------

def glyph_widths(jar, default_providers):
    """Advance of every default-font character, with the client's own formula."""
    widths = {" ": 4, chr(BULLET): 4}
    for provider in default_providers:
        namespace, path = provider["file"].split(":")
        with jar.open(f"assets/{namespace}/textures/{path}") as stream:
            image = Image.open(io.BytesIO(stream.read())).convert("RGBA")
        rows = provider["chars"]
        cell_w = image.width // len(rows[0])
        cell_h = image.height // len(rows)
        scale = provider.get("height", 8) / cell_h
        alpha = image.getchannel("A").load()
        for row_index, row in enumerate(rows):
            for col_index, char in enumerate(row):
                if char in ("\u0000", " ") or char in widths:
                    continue
                x0, y0 = col_index * cell_w, row_index * cell_h
                used = 0
                for x in range(cell_w - 1, -1, -1):
                    if any(alpha[x0 + x, y0 + y] for y in range(cell_h)):
                        used = x + 1
                        break
                widths[char] = int(0.5 + used * scale) + 1
    return widths


def entity_names():
    lang = json.load(open(find_asset("minecraft/lang/es_es.json"), encoding="utf-8"))
    return {key: value for key, value in sorted(lang.items())
            if key.startswith("entity.") and key.count(".") == 2}


# --- Zip -------------------------------------------------------------------------

def build_zip():
    with zipfile.ZipFile(ZIP, "w", zipfile.ZIP_DEFLATED) as out:
        for root, _, files in os.walk(PACK):
            for name in sorted(files):
                full = os.path.join(root, name)
                out.write(full, os.path.relpath(full, PACK).replace(os.sep, "/"))
    return hashlib.sha1(open(ZIP, "rb").read()).hexdigest()


def main():
    with zipfile.ZipFile(find_client_jar()) as jar:
        default = json.loads(jar.read("assets/minecraft/font/include/default.json"))
        default_providers = [p for p in default["providers"] if p["type"] == "bitmap"]
        build_box_font()
        build_line_fonts(default_providers)
        widths = glyph_widths(jar, default_providers)

    clear = Image.new("RGBA", (182, 5))
    save(clear, "assets", "minecraft", "textures", "gui", "sprites", "boss_bar", "green_background.png")
    save(clear, "assets", "minecraft", "textures", "gui", "sprites", "boss_bar", "green_progress.png")

    write_json(widths, MOD_DATA, "glyph_widths.json")
    write_json(entity_names(), MOD_DATA, "entity_names_es.json")

    print("resource-pack-sha1=" + build_zip())


if __name__ == "__main__":
    main()
