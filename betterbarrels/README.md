# BetterBarrels

**Store up to 2,048 items of one exact type in a vanilla barrel—with no inventory window.**

Built for **Paper 26.3 / Java 25**. No client mod, resource pack, or runtime plugin dependency is required.

![BetterBarrels features](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/betterbarrels/docs/images/features.png)

> **In game:** use `/help betterbarrels` for a full guide. Operators also see
> the migration and uninstall commands; the guide provides clickable links to
> the source and issue tracker.

## Contents

- [Quick start](#quick-start)
- [Using a storage barrel](#using-a-storage-barrel)
- [Recipes](#recipes)
- [Existing barrels and migration](#existing-barrels-and-migration)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration and saved data](#configuration-and-saved-data)
- [Limits and safety](#limits-and-safety)
- [Install](#install)
- [Links and support](#links-and-support)

## Quick start

![Right-click and sneak controls](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/betterbarrels/docs/images/controls.png)

1. Back up every world before installing or upgrading.
2. Put the plugin JAR in `plugins/` and restart Paper.
3. Right-click a barrel while holding an item to store one. Sneak-right-click
   to store as much of the held stack as fits.
4. Right-click with an empty hand to retrieve one item. Sneak-right-click
   empty-handed to retrieve up to one normal stack.
5. Check `/help betterbarrels` in game for the concise guide and links.

The action bar shows the current item and count. Empty the barrel completely
before choosing another item type.

## Using a storage barrel

![How barrel interactions work](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/betterbarrels/docs/images/interaction-flow.png)

| Main-hand action | Result |
| --- | --- |
| Right-click holding an item | Store one item |
| Sneak-right-click holding items | Store the held stack, limited by remaining capacity |
| Right-click with an empty hand | Retrieve one item |
| Sneak-right-click with an empty hand | Retrieve up to one item stack |

A barrel stores up to **2,048 of one item type**. Matching uses the complete
item similarity (including names, lore, enchantments, container contents, and
other item data), not merely its material. A different variant cannot be added
until the barrel is empty. Retrieval respects the item's own maximum stack size:
64 for most blocks, 16 for ender pearls, and 1 for unstackable tools.

There is no inventory GUI. Transfers are made by the main hand only; off-hand
interaction does not duplicate a transfer. An inventory that has no room does
not lose items. If the player is in spectator mode, no storage operation occurs.

## Recipes

BetterBarrels adds no crafting recipes. The storage feature applies to an
ordinary vanilla barrel.

| Recipe | Ingredients / result |
| --- | --- |
| BetterBarrels recipe | None; use a vanilla barrel |

## Existing barrels and migration

**Back up all worlds before first installation.** Existing barrels with ordinary
vanilla inventory contents are converted into single chests containing those
contents; mixed-item barrels are preserved instead of being discarded or
forced into single-item storage. Empty legacy barrels stay as barrels and are
initialized for storage. The conversion preserves the barrel's custom name and
lock. If a legacy barrel is encountered before the background scan reaches it,
clicking converts it and reports that the new chest can be clicked again to open.
Newly placed barrels are also initialized or converted as appropriate.

![Migration and scan lifecycle](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/betterbarrels/docs/images/migration-flow.png)

### Scan scope and pacing

The background scanner reads Anvil region headers in the `region`, `DIM-1/region`,
and `DIM1/region` directories. It visits only allocated/generated chunks and
never requests terrain generation. Initially loaded chunks are queued first; a
background tick processes at most one queued chunk every **5 server ticks**
(about four per second at 20 TPS). Temporary plugin chunk tickets are released,
and chunks loaded by the scan are requested to unload afterward. Chunk loading
and saving can still cause tick-time spikes, and large worlds may take hours.

The scan covers all worlds currently loaded in Bukkit, including generated
chunks that are not currently loaded. It does not scan arbitrary backups or
worlds that remain unloaded under a multiworld plugin. Load all relevant worlds
before relying on scan completion. A later world-load event enrolls that world
and revokes completion until its scan finishes. Use `/betterbarrels rescan`
after adding or restoring world data.

The scan saves worlds before advancing region cursors, checkpointing every 128
visited chunks in a region and at region completion. Completion saves worlds
again. After a crash, some chunks can be revisited; the conversion is designed
to be safe to repeat. Avoid world editors, region-file restores, or unloading
worlds during migration. The `status` output reports the current run's visited
chunk and converted-barrel counts; those counters restart with each scan.

## Commands

| Command | Who can use it | What it does |
| --- | --- | --- |
| `/betterbarrels status` | `betterbarrels.admin` | Shows mode, state/failure, progress counters, remaining region files, initially loaded queued chunks, and scan scope. If uninstall conversion is complete, it also warns that overflow must be collected and all worlds must be scanned before removal. |
| `/betterbarrels uninstall confirm` | `betterbarrels.admin` | Persists uninstall mode and starts a fresh scan. Every barrel, including empty barrels, is converted to a chest. This is irreversible through plugin commands. |
| `/betterbarrels rescan` | `betterbarrels.admin` | Restarts the full scan. Existing uninstall mode remains enabled. |
| `/help betterbarrels` | Everyone | Shows the in-game help topic, with clickable GitHub and issue links for players. |

Uninstall mode survives restarts and remains active after scan completion, so
newly placed barrels are also converted to chests. Keep the plugin installed
until the conversion is complete and the server has been stopped normally.

### Safe uninstall procedure

1. Make fresh backups and load every world that may contain barrels.
2. Run `/betterbarrels uninstall confirm`.
3. Keep the JAR installed and check `/betterbarrels status` until it reports
   **UNINSTALL / COMPLETE**. **PAUSED** means do not uninstall; fix the error
   and restart the scan.
4. Collect overflow drops around converted barrels. These are ordinary drops
   that can despawn, burn, or be collected by entities, players, or hoppers.
5. Stop Paper normally, then remove the JAR. Keep world and plugin-data backups
   until you have verified the result.

When a chest cannot hold all the contents, overflow drops at the converted
barrel. A single barrel holding 2,048 stackable items with a 64-item stack size
leaves **320 items** outside the chest (the 27-slot chest holds 1,728). For
16-stack items, the chest holds 432 of 2,048 and 1,616 drop; unstackable items
can make many drops. Drops in chunks without players may remain saved there,
but collect them promptly rather than relying on that behavior. Plan a
maintenance window with players offline or supervised.

Removing the JAR without this conversion does **not** restore BetterBarrels'
virtual stock to vanilla barrel inventories. The items remain in block data and
are not accessible through ordinary vanilla storage.

### Recovery snapshots

Before replacing a nonempty barrel, the plugin writes and flushes a unique
snapshot to `plugins/betterbarrels/conversion-backups/<world-uuid>/`. A file
starts with `BetterBarrels conversion snapshot v1`, followed by one
Base64-encoded Paper `ItemStack.serializeAsBytes()` record per stack. The
coordinates are in its filename. Snapshots include both virtual stock and any
vanilla inventory contents and are never overwritten or automatically replayed
(replaying may duplicate items already saved in a chest). Preserve this folder
with your world backups; manual recovery can decode records using Paper's
`ItemStack.deserializeBytes()`.

A failed backup occurs before block replacement, so the original barrel is
left intact. If the recovery chest cannot be created or saved, the plugin tries
to restore the original barrel and reports the failure. Errors pause the scan;
check the server log and do not uninstall until fixed and rescanned.

## Permissions

| Permission | Default | Grants |
| --- | --- | --- |
| `betterbarrels.admin` | Operators | `/betterbarrels` management commands, tab completion, and admin-only help details |

Ordinary players do not need a permission to use barrel storage. The console
may run management commands because it has the permission by default.

## Configuration and saved data

There is **no `config.yml` and no user-adjustable setting**. Capacity, controls,
and scan interval are fixed by the plugin: 2,048 items per barrel and one
queued chunk per 5 ticks. Migration state is stored in
`plugins/betterbarrels/migration.properties` so the plugin can resume safely;
it is internal progress, not a settings file. Its `mode` is `normal` or
`uninstall`, `complete` records scan completion, and `region.<world-uuid/path>`
values are region scan cursors. Do not edit it while the server is running.

| Setting / saved value | Default | Meaning |
| --- | --- | --- |
| `config.yml` | Not present | No configurable plugin options |
| Barrel capacity | `2048` | Maximum stored item count per barrel |
| Background scan cadence | `5` ticks | At most one queued chunk is processed per interval |
| Migration mode | `normal` | `uninstall` is permanent through commands and converts all barrels |
| Migration completion | `false` on a new/reset scan | Scan progress marker; new worlds and rescans revoke completion |
| Region cursor | `0` | Progress offset for each region file, checkpointed after saves |

Persistent item counts and serialized item type live on each barrel's block
persistent-data container. Conversion snapshots are additional manual recovery
records; they do not automatically replace block or chest data.

## Limits and safety

| Situation | Behavior |
| --- | --- |
| Hoppers / hopper minecarts | Cannot transfer items into or out of barrels |
| Opening a barrel inventory | Cancelled; there is no menu |
| Breaking a nonempty barrel | Cancelled, including in creative mode; empty it or use admin uninstall mode |
| Breaking an empty barrel | Allowed normally |
| Fire and block/entity explosions | Barrel storage is protected |
| Vanilla lock | Prevents use until an administrator unlocks it |
| Protection plugin denies interaction | Storage action is not performed |
| Piston movement | Vanilla barrel blocks cannot be moved by pistons |
| Conversion restrictions | Conversion changes blocks as an administrator-authorized migration and bypasses region-build checks |
| Ordinary process/power failure | Recent operations can roll back between normal world saves; this is not a transactional database |

Direct changes by commands, world editors, other plugins, rollback systems, or
restoring old world files are outside normal interaction protections. Back up
and test compatibility. Do not run another world editor or restore region files
while a scan is active.

## Install

1. Download the latest JAR from [Hangar](https://hangar.papermc.io/rewrite123/betterbarrels).
2. Run **Paper 26.3** on **Java 25**, place the JAR in `plugins/`, and restart
   (do not hot-reload).
3. Back up worlds first and check `/help betterbarrels` for the in-game guide.

There are no runtime dependencies. The MockBukkit registry harness targets
Paper 26.2 while production compiles against Paper 26.3; automated tests are
not a replacement for a backup or a supervised server migration test.

<details>
<summary>Build from source and publish</summary>

Build and run the tests from this plugin directory:

```sh
./gradlew build --console=plain
```

The JAR is written to `build/libs/betterbarrels-0.2.0.jar`. Gradle uses a Java
25 toolchain; tests use the MockBukkit 26.2 harness. This build does not publish
or migrate any live server.

README diagrams can be regenerated from the repository root with:

```sh
python3 tools/docs/betterbarrels.py
```

Publishing targets [rewrite123/betterbarrels on Hangar](https://hangar.papermc.io/rewrite123/betterbarrels),
Release channel, for Paper 26.3. The Hangar Gradle publication reads this
README as its resource page. To publish a version, choose an unused version in
`gradle.properties`, provide `HANGAR_API_TOKEN` with the `create_version`
permission, and supply an appropriate changelog (for example with
`-PhangarChangelog="..."`). Never commit the token. Only run the publish task
when intentionally releasing:

```sh
./gradlew build publishPluginPublicationToHangar --console=plain
```

</details>

## Links and support

- [Source code](https://github.com/rewrite123/papermc-plugins/tree/main/betterbarrels)
- [Hangar project](https://hangar.papermc.io/rewrite123/betterbarrels)
- [Report an issue](https://github.com/rewrite123/papermc-plugins/issues)
