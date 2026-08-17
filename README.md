# Loot Ender

A Minecraft Fabric mod. Structure loot is rolled per player, so nobody arrives second.

## What This Mod Does

The first player to reach a dungeon chest empties it and everyone behind them finds a box of air. That turns exploring together into a race, and a race nobody agreed to enter.

Here, opening a loot chest opens **your** copy of it. The block itself is never unpacked: its loot table stays on it, unrolled, forever. What you take comes out of a copy made the first time you opened it, so the next player still gets a first look, and so does the one after that.

Everything else is vanilla. The chest is the same block, in the same place, with the same lid animation and the same sound. You walk up and open it.

## The One New Thing To Look At

An unopened loot chest looks exactly like any other chest, because as far as you need to care it is one.

What vanilla can no longer tell you, once loot is per player, is that **you** have nothing left in this one. So a chest you have emptied wears a darkened clasp. Nothing hovers, nothing glows; the clasp just reads as spent from across a room.

That mark is drawn by Pandorical. A client without it sees ordinary chests everywhere and loses nothing but the reminder.

## What Counts As A Loot Chest

A chest or barrel that still carries an unrolled loot table, which is how the game ships every naturally generated one.

A chest you have been keeping wheat in is not a loot chest and never becomes one. Neither is a double chest with a table on only one half, which is a shape this mod does not claim to understand: both fall through to vanilla, untouched. Refusing the cases it is unsure of is the whole safety story, because the worst thing it could do is stand between you and an ordinary chest.

## Details Worth Knowing

- **Your roll is stable.** It is seeded from the chest's own seed, your UUID, and the position. Reopening a chest you have not taken from shows the same contents.
- **Two players get different loot.** Same chest, different rolls, because identical chests read as fake.
- **Half-emptied chests persist.** Leave three things behind, come back next week, they are still there. Yours.
- **Double chests behave.** Two copies joined into one 54-slot menu, in the same order vanilla uses, so it does not matter which half you clicked.
- **Breaking the chest ends it.** Every player's copy of that position is dropped, so nothing outlives the block or attaches itself to whatever is built there next.
- **You cannot walk away and keep looting.** The copy closes at the same range a real chest would.

### A known edge

A comparator or hopper reading an unopened loot chest makes vanilla unpack it on the spot, which is vanilla's behaviour and predates this mod. If that happens the chest becomes an ordinary one holding a single rolled batch, shared, exactly as it would without this installed. It needs someone to have deliberately placed the comparator, so it has not come up.

## Pandorical

Loot Ender runs server-side, and Pandorical is a hard dependency (`fabric.mod.json`): the server will not load this mod without it. It is used for one thing, the darkened clasp on a chest you have emptied, drawn through Pandorical's chest overlay API.

No Loot Ender jar is needed on a client.

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
| `LootContainerOpenMixin.java` | The one interception, on the block's own use method |

## Building

Loot Ender builds against Pandorical's live source, not a published artifact: `settings.gradle` includes `../pandorical`. Check both out side by side or the build fails before it starts.

```bash
./gradlew build
```

The built jar will be in `build/libs/`.

## License

MIT, see [LICENSE](LICENSE).
