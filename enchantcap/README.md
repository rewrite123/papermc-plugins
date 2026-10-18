# enchantcap

An anvil cost-limit plugin for **Paper 26.3 / Java 25**. No runtime dependencies.

## Behavior

- Removes the server's normal 40-level anvil cutoff before results are calculated.
- Caps the displayed **and charged** cost of valid operations at **39 XP levels**,
  so vanilla clients do not display "Too Expensive".
- Leaves costs below 40 unchanged. Players must still have enough XP levels.
- Keeps normal enchantment maximum levels, item compatibility, enchantment
  conflicts, repair-material consumption and prior-work penalties unchanged.
- Applies to all players and all normal anvil operations, including repairs,
  renaming, tools, armor, weapons and enchanted books.
- Does not modify enchanting tables, grant unsafe enchantments, change items
  directly, or bypass another plugin's rejected/empty result.

The discount to 39 levels is intentional: vanilla clients hard-code the
"Too Expensive" display for costs of 40 or more. Full uncapped XP charging would
require accepting that misleading display or using a client modification.

The maximum server threshold is Java's `Integer.MAX_VALUE`; operations that
reach that representational limit are not supported. Prior-work costs still grow,
so this is not an infinite-integer repair system.

There are no commands, permissions or configuration files. Other anvil plugins
can conflict if they replace results, thresholds or costs after enchantcap runs.
Restart the server when installing/removing; do not use plugin hot-reload.

## Build and install

```sh
./gradlew build --console=plain
```

On Windows use `gradlew.bat`. Gradle runs on Java 17+ and downloads a Java 25
toolchain if needed. The server itself must run Java 25.

Copy `build/libs/enchantcap-0.1.0.jar` to the server's `plugins/` directory and
restart. To uninstall, stop the server, remove the JAR and restart.
No server deployment or Hangar publication happens during a build.

## Publish to Hangar

The publication targets [rewrite123/enchantcap](https://hangar.papermc.io/rewrite123/enchantcap),
the `Release` channel, and Paper `26.3`. Create the Hangar project first and
provide a token with `create_version` permission through `HANGAR_API_TOKEN`.
Never store credentials in build files.

```sh
./gradlew build publishPluginPublicationToHangar --console=plain
```

For subsequent releases, choose an unused version in `gradle.properties` and
provide release notes with `-PhangarChangelog="..."`. Normal `build` and `test`
tasks do not publish.

## Verification

`./gradlew test` covers early threshold setup, cost boundaries, cancelled opens,
non-anvil inventories, invalid results and preservation of level restrictions.
These tests mock Paper; they do not simulate Minecraft's full anvil menu.

Before using on valuable items, test on a Paper 26.3 server:

1. In survival, combine compatible items whose prior-work cost would exceed 39:
   verify a valid output appears for 39 levels.
2. With fewer than 39 levels, verify taking that output is refused; with enough
   levels, verify the correct XP and input materials are consumed once.
3. Verify an inexpensive repair keeps its original cost.
4. Verify Efficiency V + Efficiency V stays at V and incompatible enchantments
   stay incompatible.
5. Verify rejected combinations and unrelated inventory menus are unchanged.
