# tpcost

Player-approved teleportation for **Paper 26.3 / Java 25**. No runtime dependencies.

## Cost

Distance is the straight-line **3D** distance between the players' current X/Y/Z
coordinates. By default, each level buys 1000/3 blocks, rounded up, with a
minimum of 3 levels. Admins can change all four pricing settings:

```text
base cost = min(maximum-cost, max(minimum-cost, ceil(distance * levels-per-1000-blocks / 1000)))
total cost = base cost + (different worlds ? cross-world-surcharge : 0)
```

| Distance (blocks) | Same world | Different worlds |
| --- | --- | --- |
| 0-1,000 | 3 | 6 |
| 2,000 | 6 | 9 |
| 3,000 | 9 | 12 |
| 3,330 | 10 | 13 |
| 8,000 | 24 | 27 |
| 8,000.01 and farther | 25 | 28 |

For different worlds, compare raw coordinates as if both players occupied the
same world, **without Nether coordinate scaling**, then add the surcharge
(3 levels by default) after the
base cap. Only the requesting player pays, in **levels**, not raw XP points.
Creative and spectator players also pay; there is no free operator bypass.

## Commands

| Command | Purpose |
| --- | --- |
| `/tpa <player>` | Request to teleport yourself to that player |
| `/tpaccept [player]` | Accept a request to your location |
| `/tpdeny [player]` | Deny an incoming request |
| `/tpcancel` | Cancel your outgoing request |
| `/tpcost <player>` | Show the current cost without making a request |

All player commands above require `tpcost.use`, granted to everyone by default. Use full online
player names (case-insensitive); tab completion is available. If multiple requests
are waiting, acceptance/denial requires a player name. On servers with conflicting
command names, use the namespaced form, e.g. `/tpcost:tpa <player>`.

Requests expire after 60 seconds. A new request replaces your previous outgoing
request; disconnecting either player cancels it. Both players must be alive and
outside vehicles. Cost is recalculated from current locations on acceptance and
the sender must still have enough levels. Levels are deducted only after a
successful teleport, preserving fractional level progress. Denied, expired,
cancelled, or failed teleports cost nothing. Other plugins can cancel the normal
`PlayerTeleportEvent` (`PLUGIN` cause).

## Admin pricing commands

Operators, the server console, and players with **`tpcost.admin`** can view or
change the distance-based pricing without restarting:

```text
/tpcostadmin
/tpcostadmin minimum-cost 3
/tpcostadmin levels-per-1000-blocks 3
/tpcostadmin maximum-cost 25
/tpcostadmin cross-world-surcharge 3
```

With no arguments, the command shows all active prices. A setting without a
value (for example `/tpcostadmin maximum-cost`) shows just that setting.
Tab completion suggests setting names and their current values.
The ordinary `/tpcost <player>` command is unchanged.

Changes apply immediately to quotes, new requests, and **pending requests when
accepted**, and are saved across restarts. Players still need enough levels at
acceptance, so changing prices can increase or decrease a pending request's cost.
If saving fails, the command reports an error, logs the failure, and leaves
the active pricing unchanged.

Settings are non-negative whole numbers up to 2,147,483,647. The minimum cannot
exceed the maximum; the maximum plus cross-world surcharge must also fit within
2,147,483,647. Increase the maximum before raising the minimum beyond it, or lower
the minimum before lowering the maximum below it. Zero is allowed: setting the
minimum, distance rate and surcharge to zero makes teleports free. Setting just
the distance rate to zero produces the minimum charge plus any world surcharge.
The maximum caps only the distance cost, not the cross-world surcharge.

On first start, `plugins/tpcost/config.yml` is generated with the original defaults:

```yaml
minimum-cost: 3
levels-per-1000-blocks: 3
maximum-cost: 25
cross-world-surcharge: 3
```

Use commands for live changes; restart after manually editing this file.
Invalid configuration prevents plugin startup rather than silently changing
prices. These settings do not affect request expiry, approval, visibility checks,
or the rule that XP is charged only after a successful teleport.

## Build and install

From this directory:

```sh
./gradlew build
```

On Windows use `gradlew.bat`. Gradle runs on Java 17+ and automatically downloads
a Java 25 toolchain if missing; the **server itself must run Java 25**.

Copy `build/libs/tpcost-0.2.0.jar` into your Paper server's `plugins/` directory and
restart the server. Default configuration is created automatically; no additional plugins are required.
Run `./gradlew test` for the pricing and request/command lifecycle tests. Tests
mock the Paper API; they do not replace a multiplayer smoke test on your server.

## Publish to Hangar

Publishing uses [HangarMC's official Gradle plugin](https://github.com/HangarMC/hangar-publish-plugin).

1. Create the Hangar project first, and set `hangarProject` in
   `gradle.properties` to its slug (default: `tpcost`).
2. Create a Hangar API key with **create_version** and **edit_page** permissions.
3. Supply it as the `HANGAR_API_TOKEN` environment variable. Never put tokens in
   source files or commit them.
4. Set `version` in `gradle.properties` to a unique version, then run:

   ```sh
   ./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar \
     -PhangarChangelog="Initial release: XP-level teleport requests."
   ```

The default channel is `Release`, and the declared Paper compatibility is `26.3`.
Overrides are available via `-PhangarProject=your-slug`,
`-PhangarChannel=Snapshot`, `-Pversion=0.1.1`, and
`-PpaperVersions=26.3`. Create a custom channel on Hangar before using it.
Do not advertise additional Paper versions without testing them.

The repository includes CI builds and a **manual** `Publish tpcost to Hangar`
GitHub Actions workflow. After hosting this directory's parent as a GitHub
repository, add the `HANGAR_API_TOKEN` repository secret and, if the project slug
differs, the `HANGAR_PROJECT` repository variable. Update the version/changelog,
then run the workflow. It tests and builds before publishing, and does not
publish automatically on pushes. No project is created or uploaded by a local
build.
