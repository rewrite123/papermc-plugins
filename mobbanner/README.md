# mobbanner

Earn enchanted-looking, mob-themed ward banners by killing mobs. Placing a ward
suppresses that exact mob type's natural spawning throughout its **16x16 chunk**,
at every height. For **Paper 26.3 / Java 25**, with no runtime dependencies.
On a complete valuable-block pyramid, the same banner attracts its mob instead.

**Latest release:** [mobbanner 0.2.0 on Hangar](https://hangar.papermc.io/rewrite123/mobbanner/versions/0.2.0).

## What's new in 0.2.0

- **Enderman banners:** earn them at the same kill milestones or use
  `/mobbanner give Alex enderman`. There are now **38 supported mob types**.
- **Pyramid attraction:** a complete 5x5 / 3x3 / 1x1 valuable-block base switches
  a standing banner from suppression to one extra spawn attempt per eligible
  spawn. Duplicate attractors do not stack; an ordinary matching ward overrides them.
- **Compatible upgrade:** existing banners and kill progress are preserved.

## Rewards

Kills are tracked separately **per player and per mob type**. Banners are awarded
at 100, 200, 300, 500, 1000, 1500, 2000, then 3000, 4000, and every further 1000
kills. Nothing is awarded at 400, 800, 2500, etc. Progress begins when this plugin
is installed; historical Minecraft statistics are not imported.

A kill counts when Paper credits that player as the mob's killer, including
projectile kills credited by the server. Environmental kills with no player
credit do not count. Kills of spawner/raid/command-spawned mobs still count.
Rewards go into the killer's inventory; leftovers drop at their feet if full.
There is no operator or game-mode bypass. Kill counts are stored in player
persistent data and saved with the server's normal player saves.
Each milestone also sends the killer a message, for example:
`You earned a mob banner for killing 100 mobs of type Zombie!`

## Admin give command

Use `/mobbanner give <player> <mob>` to give one ward banner to an online player,
for example `/mobbanner give Alex bat` or
`/mobbanner give Alex enderman` or `/mobbanner give Alex wither_skeleton`.
Tab completion lists players and supported
mob types. Mob names are case-insensitive and multiword types use underscores.
This is the plugin's give subcommand, not Minecraft's vanilla `/give`.

Requires `mobbanner.give` (operators by default); console is also supported.
The recipient is notified that an administrator gave them a banner. This does
not change their kill counts or claim they earned a milestone. If their inventory
is full, the banner drops at their feet, just like earned rewards.

## Supported mobs

Bat, Blaze, Bogged, Breeze, Cave Spider, Creaking, Creeper, Drowned, Elder Guardian,
Ender Dragon, Enderman, Endermite, Evoker, Ghast, Guardian, Hoglin, Husk, Magma Cube, Parched,
Phantom, Piglin Brute, Pillager, Ravager, Shulker, Silverfish, Skeleton, Slime,
Spider, Stray, Vex, Vindicator, Warden, Witch, Wither, Wither Skeleton, Zoglin,
Zombie, and Zombie Villager.

Spiders and cave spiders are included as normally hostile nighttime mobs.
Endermen are included as an explicit exception to the hostile-only list.
Other neutral/provoked mobs such as Piglin, Zombified Piglin, Bee, Wolf, and
Polar Bear are excluded. Variants are separate types: a Zombie ward does not
block Husks, Drowned, or Zombie Villagers.

## Pyramid attraction mode

Place a **standing** ward banner centered on a solid, three-tier pyramid:

```text
          banner
          1 x 1       directly below the banner
          3 x 3       middle layer
          5 x 5       bottom layer
```

All **35 blocks** must be iron, gold, emerald, diamond, or netherite blocks;
mixing those materials is allowed. Copper, ores, raw-metal blocks, and hollow
pyramids do not qualify. Wall banners remain suppressors.

With a complete pyramid, the banner **attracts instead of suppressing** its mob:
each successful eligible spawn triggers **one extra nearby spawn attempt** on
the next tick, in that same chunk, at the same height, up to three blocks away.
This is a **2x attempt rate**, not a guaranteed 2x population or a change to the
server's native spawn-rate setting. Duplicate attractors do not stack. A normal
banner of the same type in that chunk takes precedence and suppresses spawning.

The extra attempt checks the world border, space for the original mob's full
bounding box (including Enderman headroom), ground/water as appropriate, light
no brighter than the original spawn, collisions with living entities, and at
least 24 blocks from non-spectator players. It can fail; no retries occur.
Extra entities use the `CUSTOM` spawn reason and cannot recursively trigger
more attempts. Protection plugins can cancel that reason. These are plugin
spawn attempts, not the full vanilla spawning algorithm: they do not rerun all
biome, structure, mob-cap, or mob-specific placement rules. Original spawns
still obey vanilla rules. Spawners, raids, eggs, and commands never trigger boosts.

Pyramid completeness is checked live, so adding/removing a block switches mode
without re-placing the banner. Pyramid blocks can cross chunk boundaries but all
participating chunks must be loaded; the plugin never force-loads them. An
unloaded or incomplete pyramid behaves as an ordinary suppressing ward.
Existing 0.1.0 banners work unchanged, even if their old lore only mentions
suppression. Kill counts are preserved; Enderman counts start when 0.2.0 is installed.

## Ward behavior

- Without a complete pyramid, blocks only `NATURAL`, `CHUNK_GEN`,
  `REINFORCEMENTS`, and `PATROL` spawn reasons. With a complete pyramid, those
  same reasons can trigger attraction instead.
- Does **not** block spawners, raids, eggs, commands, breeding, conversions,
  summoned mobs, boss construction/respawn, or other spawn reasons. Some mobs
  only spawn through those mechanisms, so their banners are chiefly trophies.
- Does not remove existing mobs or stop mobs entering from another chunk.
- Supports floor and wall banners, multiple wards, and different mob wards in
  the same chunk. Only that chunk is affected, not neighboring chunks.
- Uses persistent markers on items and banner blocks, surviving restarts and
  chunk unload/reload. Ordinary banners with the same patterns/names are not wards.
- Breaking and re-placing a ward preserves its identity. Removing the last ward
  for a type immediately stops protection. Support loss and flowing fluids drop
  the marked banner.
  Explosion drops preserve identity when the explosion's drop chance allows it.
  Pistons that would directly hit wards are blocked to avoid unmarked drops.
- Crafting with ward banners is disabled (including cloning them or putting
  them on shields). This prevents copying the reward via vanilla banner recipes.
- Renaming does not change the target mob; the identity is independent of lore.

Every applicable spawn checks only banner tile entities in the spawn's chunk;
there is no global scan, ticking task, or persisted coordinate cache to become
stale. Protection plugins' cancelled placement/spawn/drop events are respected.
World-editing tools that erase block persistent data erase ward identity too.

## Appearance

Vanilla skull/creeper face patterns, mob-themed colors, names, and enchantment
glow require **no resource pack**. They are stylized faces, not exact mob textures;
vanilla banners cannot display arbitrary mob artwork. The glow is cosmetic; no
functional item enchantment is added.

## Build and install

Download the compiled plugin from
[Hangar](https://hangar.papermc.io/rewrite123/mobbanner/versions/0.2.0), or build it:

```sh
./gradlew build
```

Copy `build/libs/mobbanner-0.2.0.jar` into your Paper server's `plugins/` directory
and restart using Java 25. Gradle runs on Java 17+ and provisions Java 25 when
needed; Windows users can use `gradlew.bat`. No configuration is required.

`./gradlew test` exercises milestones, persistent identities, rewards, and ward
events. The available MockBukkit harness uses Paper 26.2 registry data; production
compiles against 26.3. Smoke-test chunk boundaries, wall/floor banner drops,
explosions, pyramid transitions, attraction safety checks, and your protection
plugins on Paper 26.3 before production use. Remove the old plugin JAR when upgrading.

### Upgrading from 0.1.0

1. Stop the server.
2. Remove the old `mobbanner-0.1.0.jar` from `plugins/` and replace it with
   `mobbanner-0.2.0.jar`. Do not keep both versions installed.
3. Start the server. Existing ward banners and saved player kill counts remain
   usable; no data migration or banner replacement is required.
4. Build a complete pyramid under an existing standing banner to enable attraction,
   or leave it off a pyramid to retain suppression.

## Hangar

Published project: [rewrite123/mobbanner](https://hangar.papermc.io/rewrite123/mobbanner).
Version **0.2.0** is available on the **Release** channel for Paper **26.3**.

Publishing uses [HangarMC's official plugin](https://github.com/HangarMC/hangar-publish-plugin).
For a future release, set a new, unique version in `gradle.properties`, set
`HANGAR_API_TOKEN` to a key with `create_version` permission, and use:

```sh
./gradlew build publishPluginPublicationToHangar \
  -PhangarChangelog="Describe the changes in this release."
```

Defaults: `Release` channel, Paper `26.3`. Set a unique version for each release
in `gradle.properties`. Never commit API keys. Normal builds do not publish.

To update the Hangar project's main page from this README without publishing a
new version, supply `HANGAR_API_TOKEN` with page-editing permission and run:

```sh
./gradlew syncAllPluginPublicationPagesToHangar
```
