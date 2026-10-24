# BetterBarrels

**One item type. 2,048 items. No inventory window.**

Bring classic single-item bulk storage to vanilla barrels. Inspired by old
modded storage barrels, built for **Paper 26.3 / Java 25** with no client mod
or resource pack.

## Just click

| Main-hand action | What happens |
| --- | --- |
| Right-click holding an item | Store one item |
| Sneak-right-click holding a stack | Store the held stack, up to the 2,048-item limit |
| Right-click empty-handed | Retrieve one item |
| Sneak-right-click empty-handed | Retrieve a normal stack |

Each barrel stores one exact item type, including its names, lore, enchantments,
and custom data. Empty the barrel to switch types. Stack sizes follow vanilla
limits: 64 for most blocks, 16 for pearls, 1 for tools. Your action bar shows
the count and item.

No GUI, no accidental mixed storage, no hopper transfers. Nonempty barrels
cannot be broken, and barrels are protected from explosions and fire.
Locked barrels remain locked. Normal protection plugins can deny interactions.

## Existing items are preserved

**Back up your worlds before installing.** On first installation, a throttled
scan visits generated chunks in every loaded Bukkit world, even chunks without
players nearby. Existing barrels containing vanilla inventory items become
chests containing those items. Empty barrels can stay as barrels.

Chunks are visited at most once every five ticks during the scan. Progress
is saved after world saves; interrupted scans resume and may safely repeat
some work. No new terrain is generated. Large worlds take time and chunk
loading/saving can still cause performance spikes.

The scan also runs on chunk loads and handles newly placed barrels. Load any
extra worlds managed by a multiworld plugin so they can be included.

## Ready to uninstall?

An operator can run:

```text
/betterbarrels uninstall confirm
/betterbarrels status
```

The first command starts a fresh full-world scan and permanently enables
uninstall mode. **Every barrel becomes a chest**, including empty barrels.
If the chest fills up, the remaining items **drop on the ground nearby**.
Collect overflow promptly: these are ordinary item drops and can despawn.
Unstackable items can create many drops; plan a maintenance window.

Wait until status reports **UNINSTALL / COMPLETE**, collect overflow, stop Paper
normally, and only then remove the JAR. **Do not uninstall while a scan is
running or paused with an error.** `/betterbarrels rescan` restarts the scan
without leaving uninstall mode.

Uninstall mode survives restarts and keeps converting newly placed barrels.
Before any nonempty barrel is converted, an additional flushed item snapshot
is saved in the plugin's `conversion-backups` folder for manual recovery.
Keep both world and plugin-data backups.

**Important:** scope is all currently loaded Bukkit worlds and their generated
chunks, not unloaded worlds or backup folders. Load every relevant world before
uninstalling. Removing the plugin without conversion leaves virtual items
hidden in the barrel block data, not accessible in vanilla inventories.

## Install

1. Back up your worlds.
2. Put the JAR in `plugins` and restart Paper.
3. Check `/betterbarrels status` for migration progress.

Admin commands require `betterbarrels.admin`, granted to operators by default.
There are no extra plugin dependencies or configuration requirements.

[Source code](https://github.com/rewrite123/papermc-plugins/tree/main/betterbarrels)
| [Documentation and safety notes](https://github.com/rewrite123/papermc-plugins/blob/main/betterbarrels/README.md)
| [Report an issue](https://github.com/rewrite123/papermc-plugins/issues)
