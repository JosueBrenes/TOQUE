# Toque Mob Spawner

Server-side Fabric mod for the TOQUE Hardcore series. Adds extra mob spawns inside
zones defined by coordinates. Outside every zone, spawning is untouched vanilla.
**Players install nothing.**

Built from the repository root: `./gradlew :mob-spawner:build`, which produces
`mods/mob-spawner/build/libs/toque-mob-spawner-1.0.0.jar`.

## Two kinds of zone

- **Around the player** (default). While a player stands in the zone's X/Z area,
  extra mobs appear in a ring around them, between `minSpawnDistance` and
  `maxSpawnDistance` blocks away, much like vanilla's own spawner.
- **Inside the box** (`"spawnInside": true`, needs `yMin` and `yMax`). While a
  player is inside the box, extra mobs appear anywhere inside it: a room, an arena,
  a pit. `/toquemobs create` makes this kind from two corners.

## How many

A zone aims for `baseMobsPerPlayer × (spawnMultiplier − 1)` extra mobs, on top of
vanilla's own:

- **Around the player:** that many near each player in the zone. Our mobs already
  near a player count against it, so players standing together share the mobs
  instead of multiplying them.
- **Inside the box:** that many in the whole box, however many players are in it.

Before every single spawn three caps are checked: `spawnsPerCheck` (per player, or
per box), the zone's `maxExtraMobs`, and `globalMaxExtraMobs` across the server.

## Where they may appear

A position must pass the rules vanilla's natural spawner uses:

- the chunk is loaded and ticking, and the spot is inside the world border;
- the spot is at least `minPlayerDistance` from every player;
- the mob's own placement rule holds (on the ground, in water, or in lava);
- its hitbox fits, so nothing appears inside blocks;
- its registered spawn predicate passes. For hostiles that includes the light
  level, so lit areas stay safe. A modded monster with no predicate of its own gets
  the vanilla darkness rule.

Biome spawn lists are not consulted; the zone's `mobs` list decides.

The work is bounded. A mob gets `maxSpawnAttempts` random columns with 10 blocks
scanned in each, and checks run every `spawnCheckIntervalTicks`. Nothing sweeps an
area.

## Keeping count, and despawning

Our mobs carry the command tag `toque_mob_spawner`. The tag is saved with the
entity, so they are recognised after a chunk reload or a restart. A set of the
loaded tagged mobs is kept up to date from Fabric's entity load and unload events;
counting never scans the world.

They are made **persistent**, because vanilla leaves persistent mobs out of its mob
cap. Without that, our mobs would fill the cap and vanilla would stop spawning,
which would replace natural spawns instead of adding to them. Vanilla therefore
does not despawn them, so this mod does: any with no player within
`despawnDistance` is removed on the next check.

## Config: `config/toque-mob-spawner.json`

It is written with two example zones on the first start. It lives in the server's
config folder, never in the world, so Hardcore World Reset's new seeds keep the
same zones. After editing it, run `/toquemobs reload`. A file that fails to parse is
never overwritten, and the settings already running are kept.

| Field | Default | |
|---|---|---|
| `enabled` | `true` | Master switch, also set by `/toquemobs enable|disable` |
| `globalMaxExtraMobs` | 150 | Extra mobs alive at once, all zones together |
| `spawnCheckIntervalTicks` | 20 | 20 ticks = 1 second |
| `baseMobsPerPlayer` | 8 | Roughly vanilla's hostile count near one player |
| `maxSpawnsPerPlayerPerCheck` | 4 | Default for zones without `spawnsPerCheck` |
| `minSpawnDistance` | 24 | Default for ring zones without `minPlayerDistance` |
| `maxSpawnDistance` | 48 | Outer edge of the ring |
| `maxSpawnAttempts` | 12 | Positions tried per mob |
| `despawnDistance` | 64 | Removed with no player this close |

Per zone:

| Field | | |
|---|---|---|
| `name` | required | Used by the commands |
| `enabled` | `true` | |
| `dimension` | `minecraft:overworld` | Also `minecraft:the_nether`, `minecraft:the_end`, or a modded one |
| `xMin` `xMax` `zMin` `zMax` | required | Inclusive |
| `yMin` `yMax` | optional | Without them the zone is a full column |
| `spawnInside` | `false` | Spawn inside the box rather than around the player |
| `minPlayerDistance` | 24, or 4 inside a box | |
| `spawnsPerCheck` | global default | |
| `spawnMultiplier` | 1.0 | 1 to 50 |
| `maxExtraMobs` | 40 | Cap for this zone |
| `mobs` | | Entity identifiers, vanilla or modded: `"minecraft:zombie"`, `"othermod:monster"` |

An identifier that does not exist is logged as
`Invalid entity configured: example:mob` and skipped. An entity that turns out not
to be a mob (an arrow, say) is skipped with a warning until the next reload.
Neither stops the server.

When zones overlap, the one earlier in the file wins.

## Commands (operators)

| Command | |
|---|---|
| `/toquemobs reload` | Re-reads the JSON and lists any warnings |
| `/toquemobs status` | On or off, extra mobs per zone and in total, who is in which zone |
| `/toquemobs zones` | Every zone in one line |
| `/toquemobs info <zona>` | One zone in full, including mobs that did not resolve |
| `/toquemobs create <nombre> <desde> <hasta> [multiplicador]` | A box zone from two corners; mobs appear inside it. Defaults: 10x, 100 max, 8 per check |
| `/toquemobs delete <zona>` | Removes a zone from the JSON |
| `/toquemobs enable` / `disable` | Saved to the JSON. Disabling keeps existing extra mobs until nobody is near |

Corners accept `~ ~ ~` and Tab-complete to the block under the crosshair.
