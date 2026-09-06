# Loot Ender - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Key Files

| File | Responsibility |
|------|---------------|
| `Main.java` | Entry point; join sync and block-break cleanup |
| `LootOpening.java` | Decides whether a container is ours, and opens the right copy |
| `LootVault.java` | Everyone's copies, the per-player roll, and persistence |
| `PlayerLootContainer.java` | One copy; lid delegation, reach, and the spent mark |
| `LootMarks.java` | The darkened clasp, via Pandorical |
| `TakeOnly.java` | Marks a container as take-only; both copy shapes carry it |
| `LootDoubleContainer.java` | The two halves of a double chest, joined and recognisable |
| `LootContainerOpenMixin.java` | Opens the player's copy, on the block's own use method |
| `LootVehicleOpenMixin.java` | The same, for a chest minecart, which is an entity and unpacks differently |
| `integration/ChestUtilsScreen.java` | Showing a copy through Chest Utils, when it is installed |
| `LootSlotMixin.java` | Refuses to let anything be put into a copy |
| `LootEnderConfig.java` | The settings file, read once at startup |
| `LootEnderItems.java` | The lockpick, registered whatever the config says |
| `lock/LockTier.java` | How good a chest's lock is, and whether it has one |
| `lock/Lockpicking.java` | The lock between a player and their copy: probe, turn, snap |
| `lock/LockScreen.java` | The lock, drawn, through Pandorical's screens |
| `lock/LockAttempt.java` | One lock, part way picked |
| `lock/LockVault.java` | Which chests each player has already picked |
| `lock/LockpickLoot.java` | Where picks come from: chests, the dead, a nugget |
| `lock/PlayerLocks.java` | Whether somebody else's claim on a chest may be picked |
| `lock/LastSeen.java` | How long ago a player was last on this server |
| `integration/ChestUtilsLocks.java` | Reading chest-utils' player locks, when it is installed |

## Building

Loot Ender builds against Pandorical's live source, not a published artifact: `settings.gradle` includes `../pandorical`. Check both out side by side or the build fails before it starts.

```bash
./gradlew build
```

The built jar will be in `build/libs/`.
