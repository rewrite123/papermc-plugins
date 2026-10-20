# Reduce Enderman World Damage

**Let Endermen borrow blocks, not permanently rearrange your world.**

Endermen keep their normal block-pickup behavior. After a random **10-45 second
delay**, each tracked Enderman walks back and puts its block exactly where it
found it.

**New in 0.2.0:** Endermen no longer get stuck standing next to the gap. They
have 10 seconds to put the block back. After that they still return it as soon
as they can, but may instead place it on the closest free spot nearby.

## Keep the personality. Reduce the damage.

- **Return to sender:** stolen blocks go back to their original coordinates.
  If an Enderman can't get there within 10 seconds, it places the block on the
  free, supported spot nearby that is closest to where it came from, instead of
  carrying it forever.
- **No overwriting:** blocks are only placed into empty air. Buildings, replacement blocks, and liquids are left untouched.
- **Death matters:** kill the Enderman before it returns and restoration is
  cancelled. Normal death drops are unchanged.
- **Restart-friendly:** the pickup location and deadline are saved with the
  Enderman and recovered when it loads again.
- **Protection-aware:** respects `mobGriefing` and lets protection plugins
  cancel the return through `EntityChangeBlockEvent`.
- **Plug and play:** no commands, permissions, configuration, or extra plugins
  required.

## How returns work

The delay is the time before a return **attempt**, not a guaranteed completion
time. The Enderman walks back naturally; it is not teleported. The gap must be
within reach of its eyes, and some part of it must be visible, before the
original block state is restored.

If obstacles, an occupied origin, or an unloaded chunk stop it for 10 seconds,
it keeps trying the original spot and also places the block on the closest
valid nearby spot. A valid spot is empty, has solid ground, is visible, and has
no entities in it. Combat and disabled AI pause returns. The plugin does not
force chunks to load.

The timer uses real elapsed time and is checked every 5 server ticks.
Time spent unloaded counts toward the delay. Endermen already holding blocks
when the plugin is first installed remain unchanged because their pickup
locations are unknown.

## Installation

Requires **Paper 26.3** and **Java 25**.

1. Back up your world.
2. Download the plugin JAR and place it in your server's `plugins` folder.
3. Restart Paper.

Upgrading from `reduceendermanblockdmg` 0.1.x: delete the old JAR. Endermen
already carrying blocks keep their return records.

To uninstall, stop the server, remove the JAR, and restart.

[Source code and documentation](https://github.com/rewrite123/papermc-plugins/tree/main/reduceendermanworlddmg)
