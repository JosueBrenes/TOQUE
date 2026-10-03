# TOQUE

[![Build](https://github.com/JosueBrenes/TOQUE/actions/workflows/build.yml/badge.svg)](https://github.com/JosueBrenes/TOQUE/actions/workflows/build.yml)
[![Latest build](https://img.shields.io/github/actions/workflow/status/JosueBrenes/TOQUE/build.yml?branch=main&label=jars&logo=github)](https://github.com/JosueBrenes/TOQUE/actions/workflows/build.yml)
[![Last commit](https://img.shields.io/github/last-commit/JosueBrenes/TOQUE?logo=git&logoColor=white)](https://github.com/JosueBrenes/TOQUE/commits/main)

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?logo=minecraft&logoColor=white)
![Fabric Loader 0.19.5](https://img.shields.io/badge/Fabric%20Loader-0.19.5-DBD0B4)
![Fabric API 0.116.17](https://img.shields.io/badge/Fabric%20API-0.116.17-DBD0B4)
![Java 21](https://img.shields.io/badge/Java-21-E76F00?logo=openjdk&logoColor=white)
![Gradle 8.10.2](https://img.shields.io/badge/Gradle-8.10.2-02303A?logo=gradle&logoColor=white)

> One death wipes the world. The counters do not.

Three Fabric mods for the **TOQUE Hardcore** series: a Minecraft run where a single
death erases the world and starts the next Try, while every player's death count
carries on across the whole series.

| | |
|---|---|
| **[`mods/death-alert`](mods/death-alert)** | Server side. Death alerts, the series death counters, the player list, the ranks and the server list MOTD. Players install nothing. |
| **[`mods/mob-spawner`](mods/mob-spawner)** | Server side. Extra mob spawns inside coordinate zones, or inside a room. Players install nothing. |
| **[`mods/server-banner`](mods/server-banner)** | Client side. Replaces the TOQUE entry in the multiplayer screen with a custom banner. Optional, for players who want it. |

---

## Repository layout

```
.
├── .github/workflows/build.yml   CI: builds every mod, uploads the jars
├── gradle/wrapper/               Pinned Gradle, so no one installs one
├── build.gradle                  Shared build logic for every module
├── settings.gradle               Which modules exist and where they live
├── gradle.properties             Platform and version numbers, in one place
├── mods/
│   ├── death-alert/              Server mod
│   ├── mob-spawner/              Server mod: zone spawns
│   └── server-banner/            Client mod
└── resourcepack/                 Pushed to players by the server (see below)
```

A module's own `build.gradle` declares only its archive name. Everything else —
Minecraft and Fabric versions, Java release, mappings, resource processing — comes
from the root, so the two mods cannot drift apart.

---

## Building

Nothing to install but a JDK 21: the Gradle wrapper handles the rest.

```bash
./gradlew build            # every mod
./gradlew :death-alert:build
./gradlew :mob-spawner:build
./gradlew :server-banner:build
```

The jars land in:

```
mods/death-alert/build/libs/toque-death-alert-1.0.0.jar
mods/mob-spawner/build/libs/toque-mob-spawner-1.0.0.jar
mods/server-banner/build/libs/toque-server-banner-1.0.0.jar
```

Every push to `main` builds them all and attaches them to the run, under the **Actions**
tab.

---

## Installing

**Server** — drop `toque-death-alert-1.0.0.jar` and `toque-mob-spawner-1.0.0.jar` into the server's `mods/`, alongside
Fabric API and Hardcore World Reset. Restart.

**Client** — optional. `toque-server-banner-1.0.0.jar` goes in a player's own `mods/`
folder and needs Fabric Loader plus Fabric API. Everything the server mod draws — the
player list, the ranks, the side panel, the MOTD — works on a plain vanilla client
with nothing installed.

---

## Commands

Server side, from `death-alert`.

| Command | Permission | What it does |
|---|---|---|
| `/toque deaths` | anyone | Series standings |
| `/toque status [player]` | OP | Stored count, rank, vanilla statistic and where the file lives |
| `/toque try <n>` | OP | Corrects the Try counter |
| `/toque objective <text>` | OP | Sets the objective line |
| `/toque set <player> <n>` | OP | Overrides one player's deaths |
| `/toque import <player>` | OP | Re-imports from the vanilla statistic |
| `/toque resetDeaths` | OP | Starts a new series: every counter to zero |

Server side, from `mob-spawner`; all OP. See [its README](mods/mob-spawner) for the
config file.

| Command | What it does |
|---|---|
| `/toquemobs create <name> <from> <to> [multiplier]` | Makes a room a spawn zone; mobs appear inside it |
| `/toquemobs delete <zone>` | Removes a zone |
| `/toquemobs reload` | Re-reads `config/toque-mob-spawner.json` |
| `/toquemobs status` · `zones` · `info <zone>` | What is running, and where |
| `/toquemobs enable` · `disable` | Master switch, saved to the file |

---

## Where the data lives

`config/toque-death-alert/deaths.json`, in the server root — **outside** the world
folder. Hardcore World Reset deletes and regenerates the world on every Try, so a
counter kept in world data would reset with it. Players are keyed by UUID, since a
name can change.

`/toque resetDeaths` is the only thing that zeroes it.

---

## Resource pack

[`resourcepack/`](resourcepack) holds the pack carrying the `toque:death` sound and the
fonts the target panel is drawn with. The
server pushes it to players automatically, serving it from this repository by raw URL
and pinning it by hash in `server.properties`:

```properties
resource-pack=https://raw.githubusercontent.com/JosueBrenes/TOQUE/main/resourcepack/TOQUE_Death_ResourcePack.zip
resource-pack-sha1=de544d9548bc42e17fa849a5c7efa3aae5ba5225
```

Both lines are coupled to the file: its path decides the URL and its contents decide
the hash, so moving it or rebuilding it means editing `server.properties` to match.
See [`resourcepack/README.md`](resourcepack/README.md).
