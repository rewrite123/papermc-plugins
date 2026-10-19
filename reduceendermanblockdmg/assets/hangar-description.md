# Reduce Enderman World Damage

**Let Endermen borrow blocks, not permanently rearrange your world.**

Endermen keep their normal block-pickup behavior. After a random **10-45 second
delay**, each tracked Enderman attempts to walk back and put its block exactly
where it found it.

## Keep the personality. Reduce the damage.

- **Return to sender:** stolen blocks go back to their original coordinates,
  rather than being placed somewhere else.
- **No overwriting:** a block is returned only if its original space is empty
  air. Buildings, replacement blocks, and liquids are left untouched.
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
time. The Enderman walks back naturally; it is not teleported. It must be nearby
and have line of sight before restoring the original block state.

Combat, obstacles, an occupied origin, disabled AI, or unloaded chunks can delay
or prevent a return. The plugin retries while the Enderman is loaded and still
carrying the matching block type. It does not force chunks to load.

The timer uses real elapsed time and is checked once every 20 server ticks.
Time spent unloaded counts toward the delay. Endermen already holding blocks
when the plugin is first installed remain unchanged because their pickup
locations are unknown.

## Installation

Requires **Paper 26.3** and **Java 25**.

1. Back up your world.
2. Download the plugin JAR and place it in your server's `plugins` folder.
3. Restart Paper.

To uninstall, stop the server, remove the JAR, and restart.

[Source code and documentation](https://github.com/rewrite123/papermc-plugins/tree/main/reduceendermanblockdmg)
