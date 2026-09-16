# Loot Ender - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need no Loot Ender jar, and Pandorical only for the clasps and the lock screen. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Key Files

| File | Responsibility |
|------|---------------|
| `Main.java` | Entry point; registration, join sync, chunk-load marks and block-break cleanup |
| `LootOpening.java` | Decides whether a container is ours, and opens the right copy |
| `LootVault.java` | Everyone's copies, the per-player roll, and persistence |
| `PlayerLootContainer.java` | One copy; lid delegation, reach, and the spent mark |
| `LootMarks.java` | The gold and darkened clasps, via Pandorical |
| `LootIndex.java` | Which loot chests exist, noticed as their chunks load, so they can be marked before anyone opens them |
| `LootLid.java` | Opens and shuts the real block's lid for players holding their own copy |
| `LootGuard.java` | Loot chests and loot minecarts stand until an op breaks them |
| `LootTips.java` | The Block Tip lines, when it is installed |
| `TakeOnly.java` | Marks a container as take-only; both copy shapes carry it |
| `LootDoubleContainer.java` | The two halves of a double chest, joined and recognisable |
| `LootContainerOpenMixin.java` | Opens the player's copy, on the block's own use method |
| `LootVehicleOpenMixin.java` | The same, for a chest minecart, which is an entity and unpacks differently |
| `integration/ChestUtilsScreen.java` | Showing a copy through Chest Utils, when it is installed |
| `LootSlotMixin.java` | Refuses to let anything be put into a copy |
| `LootExplosionMixin.java` | Takes loot chests out of a blast |
| `LootVehicleGuardMixin.java` | Refuses damage to a chest minecart with loot aboard, unless an op's |
| `LootEnderConfig.java` | The settings file, read at startup, and its page in the mods menu |
| `LootEnderItems.java` | The lockpick, registered whatever the config says |
| `lock/LockTier.java` | How good a chest's lock is, and whether it has one |
| `lock/Lockpicking.java` | The lock between a player and their copy: probe, turn, snap |
| `lock/LockScreen.java` | The lock, drawn, through Pandorical's screens |
| `lock/LockAttempt.java` | One lock, part way picked |
| `lock/LockDifficulty.java` | Easiest to hard: the server's, or an op's choice for one player |
| `lock/LockCommands.java` | `/lockpicking difficulty` |
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
