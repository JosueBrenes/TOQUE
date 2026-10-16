# TOQUE Server Banner

Client-side Fabric mod. Replaces the TOQUE entry in the multiplayer screen with a
custom banner: the logo, the rank of the series, the player count, a latency
coloured ping, a message that cycles and a border that breathes under the cursor.

Built from the repository root: `./gradlew :server-banner:build`

## Client only

This mod is **not** installed on the server. It goes in a player's own `mods/`
folder, next to Fabric Loader and Fabric API, and only for players who want the
banner. Everything the server itself draws works without it.

## Which entry it takes over

The address, never the display name: the name is whatever the player typed when
they added the server and they can rename it at any time. A name still works as a
fallback, because a tunnelled address changes whenever the tunnel is recreated.

Both lists live in `config/toque-server-banner.json`, written with a default on
first run:

```json
{ "addresses": ["ivan-fda.tun.ply.gg"], "names": ["toque"] }
```

If the banner does not appear, the log says why — the configured lists at startup,
then each entry it tested with the address it actually had.

## How it draws

The banner is painted **after** the vanilla entry has rendered, covering it, rather
than cancelling the vanilla render. That render is not only drawing: it starts the
status ping the first time the row appears and uploads the favicon once it arrives.
Cancelling it leaves the row with no player count, no ping, no MOTD and no icon.

Nothing about the entry's behaviour is touched, so clicking, double clicking,
selecting and the play, edit, delete and move buttons all keep working.

## Limitation

The day comes from the server MOTD. A multiplayer list entry receives
only what the status reply carries — MOTD, player counts, version, favicon — and
there is no other channel, so it is parsed out of the MOTD when it mentions it
and left out when it does not.
