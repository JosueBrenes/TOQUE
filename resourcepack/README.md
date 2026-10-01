# TOQUE Resource Pack

`TOQUE_Death_ResourcePack.zip` carries what the server mod needs drawn or played on
a vanilla client. The server pushes it to players automatically; nobody installs it
by hand.

- The `toque:death` sound, played when a run ends.
- The target panel: the box at the top of the screen naming the mob a player looks
  at, with its hearts. The server sends it as a boss bar title; the pack supplies
  the fonts that turn that title into a box (`assets/toque/font/hud_*.json`), and
  makes the green boss bar see-through so only the box shows.

## Building it

`pack/` is the source; the zip is built from it, never edited by hand.

```bash
python resourcepack/build.py
```

The script redraws the box and the hearts, writes the fonts, zips `pack/`, and prints
the new `resource-pack-sha1`. It also writes two tables into the server mod's
resources, `glyph_widths.json` and `entity_names_es.json`: the server walks the pen
back and forth inside the title with negative spaces, so it has to know the exact
width of everything it sends. Both are read from a local Minecraft 1.21.1 (the Loom
cache and a launcher's assets), so run `./gradlew build` and start the game once
first. Needs Python with Pillow.

The panel's layout lives in two places, the top of `build.py` and `TargetPanel.java`
in the server mod. Change one, change the other, then rebuild both.

## It is served straight from this path

`server.properties` points at the raw URL and pins the file by hash:

```properties
resource-pack=https://raw.githubusercontent.com/JosueBrenes/TOQUE/main/resourcepack/TOQUE_Death_ResourcePack.zip
resource-pack-sha1=de544d9548bc42e17fa849a5c7efa3aae5ba5225
```

So the two are coupled, and each breaks differently:

- **Move or rename the file** and the URL 404s. The client reports
  `Failed to download ... FileNotFoundException` and the death sound goes silent
  with `Unable to play unknown soundEvent: toque:death`. The hash still matches,
  because a hash is of the contents, not of the path.
- **Change the contents** and the hash no longer matches, so the client refuses the
  download even though the URL resolves.

Either change means editing `server.properties` to match. Rebuilding the zip is not
byte for byte reproducible, so a rebuild always needs a fresh
`resource-pack-sha1`; `build.py` prints it.
