# mobbanner

**Earn mob-themed ward banners that suppress a mob in one chunk—or attract it with a complete valuable-block pyramid.** Kill rewards are tracked per player and mob type.

For **Paper 26.3 / Java 25**, with no runtime dependencies.

![MobBanner features](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/mobbanner/docs/images/features.png)

> **In game:** use `/help mobbanner` for the player guide and details. Users with `mobbanner.give` also see the admin give command. The guide ends with clickable links to GitHub and the issue tracker.

## Contents

- [Quick start](#quick-start)
- [Kill rewards](#kill-rewards)
- [Get a banner](#get-a-banner)
- [Supported mobs](#supported-mobs)
- [Place and use wards](#place-and-use-wards)
- [Pyramid attraction](#pyramid-attraction)
- [Spawn behavior and limits](#spawn-behavior-and-limits)
- [Commands and permissions](#commands-and-permissions)
- [Install](#install)
- [Links and support](#links-and-support)

## Quick start

![From kill reward to ward behavior](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/mobbanner/docs/images/ward-flow.png)

1. Kill supported mobs; the server tracks your kills separately for every mob type.
2. Reach a banner milestone, or ask an administrator for a banner.
3. Place the marked banner in the chunk you want to affect. A regular ward suppresses natural spawns of its exact mob type in that 16x16 chunk at every height.
4. To attract instead, put a **standing** banner over the complete 5x5 / 3x3 / 1x1 valuable-block pyramid shown below. The matching mob can then trigger one extra nearby spawn attempt.

## Kill rewards

Kills are counted separately **per player and per mob type**. The plugin awards banners at:

| Total credited kills of one type | Reward |
| ---: | --- |
| 100, 200, 300 | One matching ward banner at each milestone |
| 500, 1,000, 1,500, 2,000 | One matching ward banner at each 500-kill milestone |
| 3,000, 4,000, 5,000, and every further 1,000 | One matching ward banner each time |

There is no reward at other counts such as 400, 800, or 2,500. Progress begins when the plugin is installed; old Minecraft statistics are not imported. The server must credit the player as the killer; credited projectile kills count, while environmental deaths without a player killer do not. Kills from spawners, raids, or command-spawned mobs still count toward rewards. There is no operator or game-mode bypass.

Counters are stored in the killer's persistent data and saved with normal player data. Reaching a milestone sends a message. The banner enters the killer's inventory; if it is full, the item drops at the killer's feet.

## Get a banner

The only player-earned route is a kill milestone. Administrators can also issue one to an online player:

```text
/mobbanner give Alex enderman
/mobbanner give Alex wither_skeleton
```

This is the plugin's `/mobbanner` subcommand, not vanilla `/give`. Mob IDs are case-insensitive and multiword names use underscores. Tab completion suggests online players and supported mob IDs. An admin-given item does not increment or alter kill progress, and the recipient is told it came from an administrator.

There is **no crafting recipe** for ward banners. Crafting with a ward banner is blocked, including banner cloning and banner-to-shield recipes, to prevent copying the special marker. Ordinary banners remain usable in vanilla crafting.

## Supported mobs

A ward applies only to its exact entity type; for example, a Zombie banner does not block Husk, Drowned, or Zombie Villager spawns.

| Supported mob types |
| --- |
| Bat, Blaze, Bogged, Breeze, Cave Spider, Creaking, Creeper, Drowned, Elder Guardian, Ender Dragon, Enderman, Endermite, Evoker, Ghast, Guardian, Hoglin, Husk, Magma Cube, Parched |
| Phantom, Piglin Brute, Pillager, Ravager, Shulker, Silverfish, Skeleton, Slime, Spider, Stray, Vex, Vindicator, Warden, Witch, Wither, Wither Skeleton, Zoglin, Zombie, Zombie Villager |

Spiders and cave spiders are included; Enderman is an explicit exception to the generally hostile-mob set. Neutral/passive types such as Piglin, Zombified Piglin, Bee, Wolf, and Cow are not supported. Some supported types rarely or never use the affected natural spawn reasons, so those banners may chiefly be collectibles.

## Place and use wards

Marked floor and wall banners work. The plugin identifies them with persistent item/block data; a regular banner that merely has the same name or appearance is not a ward. Identity and kill progress survive restarts. Breaking and re-placing a ward preserves its mob type. Removing the last matching ward in a chunk immediately ends its effect. Renaming the item does not change which mob it targets.

A normal, unpowered matching banner **suppresses** the eligible spawn reasons for its exact mob in that chunk. It does not remove mobs already present or stop them entering from another chunk. Different mob types can have separate wards in the same chunk. Only the matching chunk matters; there is no radius across neighboring chunks.

## Pyramid attraction

![Side view of the valuable-block pyramid](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/mobbanner/docs/images/pyramid.png)

A **standing** ward banner directly above a complete pyramid changes that matching mob's mode from suppression to attraction:

| Layer under the banner | Required footprint | Blocks |
| --- | ---: | ---: |
| Top | 1 × 1 | 1 |
| Middle | 3 × 3 | 9 |
| Base | 5 × 5 | 25 |
| **Total** |  | **35** |

Every block must be an iron, gold, emerald, diamond, or netherite block. Materials can be mixed. Copper, ores, raw-metal blocks, and hollow or incomplete layers do not qualify. Wall banners cannot activate attraction. Completeness is checked live, so changing any block changes the mode without replacing the banner. The structure can cross chunk boundaries, but every relevant chunk must already be loaded; the plugin does not force-load chunks. An incomplete or unloaded pyramid behaves as an ordinary suppressing ward.

A successful eligible spawn in an attraction chunk causes **one extra spawn attempt** on the next tick, at the same height and within three blocks on each horizontal axis, within that same chunk. It is an additional attempt—not a guaranteed second mob, a change to Minecraft's spawn-rate setting, or a guaranteed doubling of population. Duplicate matching attractors do not stack. If a matching unpowered banner is also present, suppression takes precedence regardless of banner order.

The extra attempt checks the world border, room for the original mob's full bounding box (including Enderman headroom), suitable ground or water, light no brighter than the original spawn, living-entity collisions, and a distance of at least 24 blocks from non-spectator players. It can fail and is not retried. Extra entities use the `CUSTOM` spawn reason, so they cannot recursively trigger more attraction; protection plugins may also veto that reason. This is not the full vanilla spawning algorithm: the extra attempt does not rerun every biome, structure, mob-cap, or mob-specific placement rule.

## Spawn behavior and limits

Only these Bukkit spawn reasons are intercepted for supported types:

| Spawn reason | Suppressed by ordinary ward | Can trigger pyramid attraction |
| --- | :---: | :---: |
| `NATURAL` | Yes | Yes |
| `CHUNK_GEN` | Yes | Yes |
| `REINFORCEMENTS` | Yes | Yes |
| `PATROL` | Yes | Yes |

Spawners, raids, eggs, commands, breeding, conversions, summoned mobs, boss construction/respawn, other spawn reasons, and existing entities are not blocked or boosted. Original eligible spawns still follow Minecraft's normal rules. Attraction attempts are limited to eligible natural spawn events; spawners, raids, eggs, and commands never trigger them.

Ward blocks retain their identity when mined and dropped. Support loss and flowing fluids drop a marked banner; explosion drops preserve identity when the explosion drop chance allows it. Pistons that would directly hit a ward are blocked to prevent unmarked drops. Protection plugins' cancelled placement, spawn, and drop events are respected. World-editing tools that remove block persistent data can also erase ward identity.

Every applicable spawn checks only banner tile entities in that spawn's chunk. There is no global scan, ticking task, or saved coordinate cache that can become stale.

## Appearance

Banners use vanilla skull or creeper-face patterns, mob-themed colors, a mob name, and an enchantment glint. No resource pack is needed. These are stylized vanilla banner patterns, not exact mob textures; the glint is cosmetic and adds no functional enchantment.

## Upgrading from 0.2.0

Stop the server, remove the old `mobbanner-0.2.0.jar`, and install `mobbanner-0.3.0.jar` (do not keep both versions). Existing marked banners and player kill counters remain usable; no data migration or banner replacement is needed.

## Commands and permissions

| Command | Who | What it does |
| --- | --- | --- |
| `/mobbanner give <player> <mob>` | `mobbanner.give` | Give one marked banner to an online player. |
| `/help mobbanner` | Everyone | Open the in-game player guide and clickable links. |

| Permission | Default | Grants |
| --- | --- | --- |
| `mobbanner.give` | Operators | Give banners without earning kill milestones; view the admin help section. |

Console is supported for the give command. If the target is offline or a mob ID is unsupported, the command reports an error. The full list of types is available through tab completion.

## Install

1. Download `mobbanner` from [Hangar](https://hangar.papermc.io/rewrite123/mobbanner).
2. Place `mobbanner-0.3.0.jar` in the server's `plugins/` directory and restart Paper.
3. The server must run Java 25. Optional: grant `mobbanner.give` to trusted administrators. No configuration is required.

## Links and support

- **Source and full guide:** [GitHub repository](https://github.com/rewrite123/papermc-plugins/tree/main/mobbanner)
- **Download:** [Hangar project](https://hangar.papermc.io/rewrite123/mobbanner)
- **Report a bug or request a feature:** [GitHub issues](https://github.com/rewrite123/papermc-plugins/issues)

<details>
<summary>Build from source and publish</summary>

```sh
./gradlew build --console=plain
```

Copy the resulting jar from `build/libs/`; Windows users can run `gradlew.bat`. Gradle runs on Java 17+ and provisions Java 25 for compilation if needed. MockBukkit tests use the available Paper 26.2 registry harness while production compiles against Paper 26.3. Smoke-test chunk boundaries, floor/wall banner drops, explosions, pyramid transitions, attraction safety checks, and your protection plugins on Paper 26.3 before production use. Remove the old JAR when upgrading.

Published as `rewrite123/mobbanner` on Hangar's **Release** channel for Paper **26.3**. Publishing uses [HangarMC's Gradle publishing plugin](https://github.com/HangarMC/hangar-publish-plugin). Set a unique `version` in `gradle.properties` and provide a Hangar key with `create_version` and `edit_page` permissions via `HANGAR_API_TOKEN`. Never commit API keys.

```sh
./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar \
  -PhangarChangelog="Describe this release"
```

This README is synced as the Hangar project page. Regenerate the original diagrams from the repository root with `python3 tools/docs/mobbanner.py`. A normal build does not publish anything.

</details>
