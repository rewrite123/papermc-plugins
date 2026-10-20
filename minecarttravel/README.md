# minecarttravel

Automatic minecart speed boosts and lodestone-to-lodestone fast travel for
**Paper 26.3 / Java 25**, with no runtime dependencies.

**Latest release:** [MinecartTravel 0.2.1 on Hangar](https://hangar.papermc.io/rewrite123/minecarttravel/versions/0.2.1).

**What's new in 0.2.1:** players can opt out of the speed boost and fast travel
separately with `/minecarttravel boost [on|off]` and
`/minecarttravel fasttravel [on|off]`. For example, keep fast travel but ride at
vanilla speed, or keep the boost but never be teleported.

**What's new in 0.2.0:**

- **Ride to register.** Fast-travel lines are now registered by riding them with
  `/minecarttravel register`, replacing background track scanning. Only
  registered lines fast travel. **Lines from 0.1.x must be registered again**;
  the old `rail-graph.txt` is ignored and can be deleted.
- **In-game admin settings.** `/minecarttravel set <setting> <value>` changes the
  boost delay, fast-travel delay, minimum speed, and boost speed live and saves
  them to `config.yml`. Anyone can view them with `/minecarttravel settings`.

Speed boosts and arrivals work in two-block-tall tunnels: only the block directly
above the rail must be passable (air, torches, and redstone torches are fine).

## Riding

The speed boost and fast travel are both on by default. Travel continuously **above 6 blocks/second**:

| Qualifying game time | Effect |
|---|---|
| First 30 seconds | Normal minecart movement |
| After 30 seconds | Target **90 blocks/second** on safe straight track |
| After 120 seconds total | On a registered line, automatically travel to the far lodestone endpoint |

These are the defaults; admins can change them in-game (see
[Admin settings](#admin-settings)). The 120 seconds include the initial 30 seconds. Timings use game ticks:
600 ticks for boost, 2400 for endpoint travel, at 20 TPS. Server lag lengthens
the real-world wait and lowers real-world movement speed.
The timer measures actual displacement, not just commanded velocity. Stopping,
traveling at or below the minimum speed, derailing, dismounting, changing riders,
unloading, or external teleporting resets it. The first sample initializes the
timer, so a new ride needs a full 600 qualifying movement samples.

Each player chooses which features they want:

| Command | Effect |
|---|---|
| `/minecarttravel boost off` | Ride at vanilla speed; fast travel still works |
| `/minecarttravel fasttravel off` | Keep the speed boost; never teleport (and no "register this line" hint) |
| `/minecarttravel normal` | Turn both off |
| `/minecarttravel fast` | Turn both back on |

Use `on` to re-enable one feature, or leave off `on`/`off` to toggle it.
`/minecarttravel status` reports both settings, qualifying time, and whether the
current rail is on a registered route.
`/minecartfasttravel` is an alias. Choices persist across restarts and reconnects.
The qualifying timer keeps running with the boost off, so fast travel still
triggers after the fast-travel delay. Turning fast travel off while your arrival
is being prepared cancels that trip.
`minecarttravel.use` is granted to everyone by default. Players without it do
not get boosts or endpoint travel. There are no fees.

All minecart variants, including empty and cargo carts, can earn the physical
boost. Endpoint teleporting requires a rideable minecart with **exactly one
player rider** who has fast travel on. Carts with animals or cargo are not
teleported. Any player passenger with the boost off, or without permission,
suppresses the cart's boost.

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

## Commands

| Command | Who | Effect |
|---|---|---|
| `/minecarttravel` or `/minecarttravel status` | everyone | Your boost and fast-travel choices, qualifying time, and whether you are on a registered line |
| `/minecarttravel boost [on\|off]` | everyone | Turn your speed boost on or off (toggles without an argument) |
| `/minecarttravel fasttravel [on\|off]` | everyone | Turn your fast travel on or off (toggles without an argument) |
| `/minecarttravel normal` / `fast` | everyone | Turn both off / both on |
| `/minecarttravel register` | `minecarttravel.register` (everyone by default) | Register the line you ride next |
| `/minecarttravel register cancel` | same | Stop a pending or active registration |
| `/minecarttravel settings` | everyone | Show the current timing and speed settings |
| `/minecarttravel set <setting> <value>` | `minecarttravel.admin` (operators) | Change a setting live |
| `/minecarttravel rescan` | `minecarttravel.admin` | Recheck registered routes in loaded chunks |

`/minecartfasttravel` is an alias.

### Admin settings

| Setting | Unit | Default | Allowed |
|---|---|---|---|
| `boost-delay` | seconds | 30 | 0.05-3600, below `fast-travel-delay` |
| `fast-travel-delay` | seconds | 120 | 0.05-7200, above `boost-delay` |
| `min-speed` | blocks/second | 6 | 0.1-8 (vanilla top speed), at most `boost-speed` |
| `boost-speed` | blocks/second | 90 | `min-speed` to 120 |

Example: `/minecarttravel set fast-travel-delay 60`. Changes apply to rides in
progress on their next update and are written to `config.yml`, so they survive
restarts. Invalid values are rejected with an explanation and nothing changes.
Raising `boost-speed` automatically raises `safety-lookahead-blocks` when needed
so the safety check still covers a full tick of travel plus margin.

## Registering a fast-travel line

1. Build a line with a real **lodestone** at each end. Each end rail must be
   **flat and straight** and run directly into the lodestone at the same height:

   ```text
   lodestone | rail = rail = rail = ... = rail | lodestone
   ```

2. Run `/minecarttravel register`. If you are not already in a minecart, you
   have 60 seconds to get into one.
3. Your cart must be within **16 rails** of one of the lodestone ends. Recording
   starts from the nearer end.
4. Ride to the lodestone at the other end. The action bar shows the rails and
   turns recorded so far. Boosts keep working while you register, but fast
   travel is paused for that cart.
5. When the cart reaches the far end rail, the route is registered and you get a
   confirmation. From then on, riding that line for the fast-travel delay
   teleports you to the end you are heading toward.

Registration records the rails your cart actually travels over, in the switch
positions it actually takes, so junctions and side branches are allowed.
Registration fails if the cart leaves the rails, goes backward, stops for 60
seconds, loops back on itself, ends without running straight into a lodestone,
returns to its starting lodestone, has more than **32 turns or slope changes**,
or is longer than **65,536 rails**. Leaving the minecart, changing worlds, or
logging out cancels registration. Registering track that overlaps an existing
route replaces the older route.

### When a route stops working

A registered route is unregistered, and logged in the console, when any of its
rails is removed or changes shape (for example a junction is switched or a
curve is reshaped by a new rail) or either lodestone is removed. Register it
again after fixing the track. Changes are detected from normal block events
(placement, breaking, physics, redstone, fluids, burning, fading, explosions,
entity block changes, and pistons) and verified on the next tick.

Whenever a chunk containing part of a route loads, the route's rails and
lodestones in that chunk are rechecked, which catches offline or world-editor
edits. Fast travel never starts from or arrives in a chunk that is still being
rechecked. After edits that bypass block events in loaded chunks, operators can
run `/minecarttravel rescan`.

## Fast travel

Once a cart on a registered route reaches the fast-travel delay, the plugin
chooses the end in the cart's current direction. The occupied cart pauses while
the arrival area is prepared. Only the destination's **3 x 3 chunk area** is
loaded, asynchronously and without generating terrain, using temporary plugin
chunk tickets. The rest of the line is never loaded.

The same cart and passenger are teleported together, using Paper 26.3's native
passenger-retaining teleport behavior. The cart stops centered on the final rail
before the lodestone. The destination must still have its rail, support,
headroom, lodestone, an in-border position, and no nearby obstructing entities.
If the route is unregistered, the rider leaves, the cart moves during
preparation, loading fails, the destination is blocked, or another plugin
rejects the teleport, travel is cancelled with a message. Preparation times out
after 60 game seconds, and tickets are always released. A successful trip
resets the timer. Riding an unregistered line tells you once how to register it.

## Route storage and performance

Routes are stored compactly: a straight run of same-shape rails is one segment
however long it is, and curves are single cells. Position lookups are interval
searches, so there are no per-rail objects. Routes are saved to `routes.txt`
every 30 game seconds when they changed and on graceful shutdown, using
replace-on-success writes. A corrupt route file or invalid configuration
disables the plugin with a clear server error.

Recording rides has a global **512-steps-per-tick** budget shared by all
registering players. Route verification has a global **4096-blocks-per-tick**
budget. Block events far from any route are rejected with a chunk lookup. There
is no world scanning, permanent chunk ticket, or per-rail task.

## Configuration

The generated `config.yml` contains the four admin settings (as ticks and
blocks/second), the safety lookahead, route length and turn limits, work
budgets, and the save interval. Edit it and restart to change the non-admin
settings; startup validates every bound. Lowering the boost below ice-boat speed
naturally removes its straight-line speed advantage.

## Build and install

Download the compiled plugin from
[Hangar](https://hangar.papermc.io/rewrite123/minecarttravel/versions/0.2.1), or build it:

```sh
./gradlew build
```

Copy `build/libs/minecarttravel-0.2.1.jar` into your Paper server's `plugins/`
directory and restart using Java 25. Gradle runs on Java 17+ and provisions
Java 25 when needed; Windows users can use `gradlew.bat`. No configuration is
required.

`./gradlew test` covers ride recording (straight, curves, slopes, boosted carts,
junctions, limits, failures, unloaded track), route storage and lookup,
invalidation from block changes and chunk loads, admin settings, commands,
timing, per-player boost and fast-travel opt-outs, and travel safety. MockBukkit uses Paper 26.2 registry
data; production compiles against 26.3. Before production use, smoke-test
registration, junction switching on a registered line, 90-block/second
straight-line movement, curves, slopes, braking, chunk boundaries, disconnects
during preparation, passenger retention, and protection-plugin teleport
cancellation on a real Paper 26.3 server. The target speed is configured and
tested mathematically; a live-server movement benchmark is not claimed.

### Upgrading

From 0.2.0, replace the JAR and restart; routes, settings, and player choices
carry over.

From 0.1.x:

1. Stop the server.
2. Remove the old `minecarttravel-0.1.x.jar` from `plugins/` and replace it with
   `minecarttravel-0.2.1.jar`. Do not keep both versions installed.
3. Start the server. Your `config.yml` keeps working: the two renamed work-budget
   keys (`verify-blocks-per-tick`, `record-steps-per-tick`) fall back to their
   defaults, and the old `scan-blocks-per-tick` / `route-steps-per-tick` keys are
   ignored. Players who chose normal mode stay opted out of both features.
4. Register each fast-travel line once with `/minecarttravel register`. Lines
   tracked by 0.1.x are not migrated; `plugins/minecarttravel/rail-graph.txt` is
   no longer used and can be deleted.

## Hangar

Published project: [rewrite123/minecarttravel](https://hangar.papermc.io/rewrite123/minecarttravel).
Version **0.2.1** is available on the **Release** channel for Paper **26.3**.

Publishing uses [HangarMC's official plugin](https://github.com/HangarMC/hangar-publish-plugin).
For a future release, set a new, unique version in `gradle.properties`, set
`HANGAR_API_TOKEN` to a key with `create_version` permission, and use:

```sh
./gradlew build publishPluginPublicationToHangar \
  -PhangarChangelog="Describe the changes in this release."
```

Defaults: `Release` channel, Paper `26.3`. Never commit API keys. Normal builds
do not publish.

To update the Hangar project's main page from this README without publishing a
new version, supply `HANGAR_API_TOKEN` with page-editing permission and run:

```sh
./gradlew syncAllPluginPublicationPagesToHangar
```
