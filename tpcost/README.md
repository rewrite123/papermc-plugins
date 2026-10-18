# tpcost

Player-approved teleportation for **Paper 26.3 / Java 25**. No runtime dependencies.

## Cost

Distance is the straight-line **3D** distance between the players' current X/Y/Z
coordinates. Each level buys 1000/3 blocks, rounded up, with a minimum of 3 levels:

```text
base cost = min(25, max(3, ceil(distance * 3 / 1000)))
total cost = base cost + (different worlds ? 3 : 0)
```

| Distance (blocks) | Same world | Different worlds |
| --- | --- | --- |
| 0-1,000 | 3 | 6 |
| 2,000 | 6 | 9 |
| 3,000 | 9 | 12 |
| 3,330 | 10 | 13 |
| 8,000 | 24 | 27 |
| Over 8,000 | 25 | 28 |

For different worlds, compare raw coordinates as if both players occupied the
same world, **without Nether coordinate scaling**, then add 3 levels after the
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

All commands require `tpcost.use`, granted to everyone by default. Use full online
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

## Build and install

From this directory:

```sh
./gradlew build
```

On Windows use `gradlew.bat`. Gradle runs on Java 17+ and automatically downloads
a Java 25 toolchain if missing; the **server itself must run Java 25**.

Copy `build/libs/tpcost-0.1.0.jar` into your Paper server's `plugins/` directory and
restart the server. No configuration file or additional plugins are required.
Run `./gradlew test` for the pricing and request/command lifecycle tests. Tests
mock the Paper API; they do not replace a multiplayer smoke test on your server.

## Publish to Hangar

Publishing uses [HangarMC's official Gradle plugin](https://github.com/HangarMC/hangar-publish-plugin).

1. Create the Hangar project first, and set `hangarProject` in
   `gradle.properties` to its slug (default: `tpcost`).
2. Create a Hangar API key with **create_version** permission.
3. Supply it as the `HANGAR_API_TOKEN` environment variable. Never put tokens in
   source files or commit them.
4. Set `version` in `gradle.properties` to a unique version, then run:

   ```sh
   ./gradlew build publishPluginPublicationToHangar \
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
