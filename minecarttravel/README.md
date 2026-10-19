# minecarttravel

Automatic minecart speed boosts and lodestone-to-lodestone fast travel for
**Paper 26.3 / Java 25**, with no runtime dependencies.

**Latest release:** [MinecartTravel 0.1.1 on Hangar](https://hangar.papermc.io/rewrite123/minecarttravel/versions/0.1.1).

**What's new in 0.1.1:** speed boosts and endpoint arrivals now support two-block-tall
tunnels: the rail occupies the lower block, and only the block immediately above
it must be passable. A stone ceiling two blocks above the rail is allowed.
Normal and redstone torches in the passable block are allowed; solid blocks,
liquids, and hazardous headroom are still rejected. This is a clearance rule,
not a full cart-and-rider collision simulation.

## Riding

Fast mode is enabled by default. Travel continuously **above 6 blocks/second**:

| Qualifying game time | Effect |
|---|---|
| First 30 seconds | Normal minecart movement |
| After 30 seconds | Target **90 blocks/second** on safe straight track |
| After 120 seconds total | Automatically travel to the far lodestone endpoint |

The 120 seconds include the initial 30 seconds. Timings use game ticks:
600 ticks for boost, 2400 for endpoint travel, at 20 TPS. Server lag lengthens
the real-world wait and lowers real-world movement speed.
The timer measures actual displacement, not just commanded velocity. Stopping,
traveling at or below the minimum speed, derailing, dismounting, changing riders,
unloading, or external teleporting resets it. The first sample initializes the
timer, so a new ride needs a full 600 qualifying movement samples.

`/minecarttravel normal` opts out. `/minecarttravel fast` opts back in.
`/minecarttravel status` reports mode and qualifying time.
`/minecartfasttravel` is an alias. Modes persist across restarts and reconnects.
`minecarttravel.use` is granted to everyone by default. Players without it do
not get boosts or endpoint travel. There are no fees.

All minecart variants, including empty and cargo carts, can earn the physical
boost. Endpoint teleporting requires a rideable minecart with **exactly one
player rider**. Carts with animals or cargo are not teleported. An opted-out or
unauthorized player passenger suppresses the cart's boost.

## Safe speed boosts

The 90-block/second target exceeds blue-ice boating's approximately
73 blocks/second **on long, straight, level, clear, loaded tracks at 20 TPS**.
It is not an unconditional average-speed guarantee.

Boosting sets the cart's maximum speed and velocity using Paper's minecart API.
At least 12 straight, level rail blocks ahead must already be loaded, supported,
and have one passable block of headroom directly above the rail. Curves, slopes, endpoints, gaps,
unloaded track, nearby entities, unpowered powered rails, and powered activator rails return
boosted carts to their original maximum speed and clamp their boosted velocity
to at most vanilla speed. Normal powered-rail braking is never overridden.
The timer can continue through slower sections while actual speed remains
above the minimum; otherwise it resets.

No chunks are loaded to sustain the physical speed boost. At chunk-loading
boundaries carts slow down until normal player chunk loading catches up.
Original maximum speeds are restored on opt-out, dismount, unload, destruction,
and plugin shutdown. A persistent recovery marker restores the original speed
on the next cart update after an interrupted boosted ride.

## Building a fast-travel line

Use real **lodestone** blocks as stoppers, horizontally adjacent to the outward
ends of **flat, straight endpoint rails**, at the same height as the rails:

```text
lodestone | rail = rail = rail = ... = rail | lodestone
```

No registration command or names are needed. Place rails first or lodestones
first; the graph connects automatically as blocks are added. Extending a
straight line moves its stored tail instead of adding a node for every rail.
Joining unfinished tracks combines their connected path. Removing any required
rail, removing a stopper, or changing a connection immediately invalidates
in-flight route checks; after the edit is reconciled the disconnected line
cannot fast travel. Rebuilding it re-enables travel automatically.

A valid route has exactly two different lodestone stoppers, reciprocal rail
connections, no branches/junctions or loops, at most **65,536 rails**, and at
most **32 bends/slope transitions**. Each curved rail counts as one bend;
changing between straight/sloped shapes also counts as a transition.
Parallel tracks without a connecting rail are independent. A third rail aimed
into the side of a track counts as a junction even if a switch points elsewhere.
All vanilla rail types and ascending shapes are tracked.

At the two-minute threshold the plugin walks the in-memory graph, incrementally,
and chooses the endpoint in the cart's current direction. The occupied cart
pauses while the arrival area is prepared. Only the destination's **3 x 3 chunk
area** may be loaded, asynchronously, without generating new terrain. Temporary
plugin chunk tickets keep that area loaded during preparation. The normal
chunk-load reconciliation finishes, then the graph is checked again.
The rest of the line is never loaded for route verification.

The same cart and passenger are teleported together, using Paper 26.3's native
passenger-retaining teleport behavior. No cart is destroyed/recreated, no inventory is copied,
and the rider is not deliberately ejected. The cart stops centered on the final
rail before the lodestone. The destination must still have its rail, support,
headroom, stopper, an in-border position, and no nearby obstructing entities.
If the graph changes, the rider leaves, the cart moves during preparation,
loading fails, the destination is blocked, or another plugin rejects the
teleport, travel fails explicitly without charging anything.
Preparation has a 60-game-second timeout; all tickets are released on success,
failure, or shutdown. A successful trip resets the timer, preventing ping-pong.
If a moving cart's line is incomplete, checks retry at most every five seconds.

## Graph model and persistence

The graph is separate from world blocks and chunks. Straight collinear rails
with the same shape are stored as an interval with two endpoints; an arbitrarily
long straight line needs one rail-run record rather than one record per block.
Slope runs are compressed too. Curves remain individual nodes.
Inserting/removing a rail splits or merges those intervals. Route direction
metadata is stored per run during a check, not per rail.

Newly encountered chunks use a cheap block-palette check to skip terrain with
no rail or lodestone states. Chunks containing existing tracks are seeded under
a global **4096-block-per-tick** scan budget. Once a chunk is known, subsequent
loads only recheck its recorded rails and stoppers, not its entire terrain.
Known-empty chunks need no scan. Known-chunk markers persist across restarts.
A chunk being reconciled is not eligible for fast travel.
Normal block placement, breaking, physics, redstone,
fluids, burning, fading, support destruction, explosions, entity block changes,
and pistons update nearby graph observations on subsequent ticks, under that
same global budget. Cancelled edits are ignored.
Track scans never force-load a chunk. Clean saved graph data remains usable
when track chunks unload.

Route walks have a global round-robin **512-steps-per-tick** budget across all
carts. The length and bend limits prevent unbounded searches, including loops.
Each actual graph edit invalidates in-flight checks, including unrelated edits
(conservative global revision tracking). Editing constantly can postpone fast
travel, but does not disable the physical boost.

The compressed graph and pending-chunk markers are saved to `rail-graph.txt`
every 30 game seconds and on graceful shutdown, using replace-on-success file
writes. Invalid configuration or corrupt graph data disables the plugin with
a clear server error rather than silently trusting an empty graph.
After a crash, recorded rails reconcile when next loaded. No event-based plugin
can reliably discover newly added branches placed offline or by a world editor
that bypasses Bukkit events; these require a full rescan of affected chunks.
Recorded rail/stopper removals are detected on their next chunk load, but not
while the altered chunk remains unloaded.

After edits that bypass normal events, use `/minecarttravel rescan` (operator
permission `minecarttravel.admin`) to reconcile **loaded chunks**, or unload and
reload affected chunks before trusting the route. The console's status command
shows compressed-run, stopper, and pending-chunk counts.
There is no permanent chunk ticket, global every-tick world scan, or per-rail
task.

## Configuration

The generated `config.yml` exposes speed/time thresholds, lookahead, maximum
route length, maximum bends, global work budgets, and save interval.
Restart after editing. Bounds are validated on startup. Lowering the boost
below ice-boat speed naturally removes its straight-line speed advantage.

## Build and install

```sh
./gradlew build
```

Copy `build/libs/minecarttravel-0.1.1.jar` into the Paper server's `plugins/`
directory and restart on Java 25. Gradle provisions Java 25 if needed;
Windows users can use `gradlew.bat`.

Tests cover compression/splitting, graph connectivity, tails, break/rebuild,
junctions, curves, slopes, limits, persistence, event reconciliation, timing,
mode preferences, and travel safety. MockBukkit uses Paper 26.2 registry data;
production compiles against 26.3.
Before production use, smoke-test actual 90-block/second straight-line movement,
curves, slopes, braking, chunk boundaries, disconnects during preparation,
occupied-cart passenger retention, and protection-plugin teleport cancellation
on a real Paper 26.3 server. High-speed physics and experimental minecart
settings can vary by server version/configuration.
The target speed is configured and tested mathematically; a live-server
movement benchmark is not claimed by the unit tests.

## Hangar publication

The official Hangar publishing Gradle plugin is configured for `minecarttravel`:

```sh
export HANGAR_API_TOKEN='your-token'
./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar
```

Create the Hangar project first. Never commit credentials.
