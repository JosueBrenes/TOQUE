# TOQUE Death Alert

Server-side Fabric mod for the TOQUE Hardcore series. **Players install nothing** —
everything below is drawn with packets a vanilla client already understands.

Built from the repository root: `./gradlew :death-alert:build`

## What it does

- **Death alert.** A title, a subtitle carrying the player's rank, and the
  `toque:death` sound, for everyone.
- **Series death counters.** Cumulative across every Try, surviving world resets,
  new seeds and server restarts.
- **Ranks.** Derived from the death count, never stored, so they cannot fall out of
  step with it. Shown in the player list, above players' heads and in the panel.
- **Player list.** Name, health bar and deaths per player, under a banner with the
  Try, the day and the toll.
- **Side panel.** Per player: name, rank, deaths, coordinates, dimension, Try, day
  and objective.
- **Server list MOTD.** Rebuilt on every ping with the live Try, day and toll.
- **Target panel.** Look at any mob, player or animal up to 24 blocks away and a
  box at the top of the screen shows its name, its hearts and the mod it comes from.

## Package layout

| Package | Responsibility |
|---|---|
| `core` | Logging, the runtime service holder, font measurement, gradients |
| `series` | The death counters, their JSON storage and the Try watcher |
| `death` | Fatal-damage detection, the totem guard and the announcement |
| `role` | Ranks derived from the death count |
| `tab` | The player list: rows, health bars, banner |
| `hud` | The per-player side panel and the target panel |
| `nametag` | Ranks above players' heads |
| `status` | The multiplayer-list MOTD |
| `command` | The `/toque` command tree |
| `mixin` | The two hooks into vanilla |

## The two mixins

Both are small and read-only with respect to game state.

- `ServerPlayerEntityMixin` fills in `getPlayerListName`, which vanilla leaves null.
  That is what lets the server send a custom player list row.
- `MinecraftServerMixin` replaces the description in the status reply, so the MOTD
  carries the live series. Hooked on the metadata rather than `setMotd`, because
  `setMotd` only stores the string while a query handler is handed a fresh copy of
  the metadata for every ping.

## The target panel

A vanilla client draws nothing of its own at the top of the screen for the server,
except boss bars. So each player gets a boss bar nobody else sees, and its title is
the whole panel: the resource pack's fonts draw the box, the hearts and two extra
lines below the title, and the server stitches them together with negative spaces
(see `TargetPanel`). The bar is green, which the pack makes see-through; nothing in
vanilla uses a green bar, but a plugin or datapack that does would lose its bar.

The target is found on the server with a ray from the player's eyes, stopped by the
first block, so nothing shows through walls. Invisible entities, spectators and
armor stands are skipped. Names are sent in Spanish whatever the player's language,
because the server has to measure the exact text.

Without the resource pack the panel turns into a line of placeholder boxes, so the
pack should be required in `server.properties`.

## Working with Hardcore World Reset

HWR intercepts the normal death flow, so `AFTER_DEATH` may never fire; the death is
caught on `ALLOW_DEATH` instead, which runs before that interception. The death is
never cancelled.

`ALLOW_DEATH` also fires before vanilla checks for a Totem of Undying, so the totem
check here mirrors vanilla's own: a held totem saves the player unless the damage
bypasses invulnerability. A player saved that way is neither announced nor counted.

HWR rebuilds the world mid-session without restarting the server, so the Try is
derived from the overworld seed on a one second timer rather than at start-up.
