# soulgem

Craft soul gems, charge them with newly earned XP, and use them instead of lapis
to enchant or upgrade gear. For **Paper 26.3 / Java 25**, with no runtime
dependencies or required resource pack.

**Latest release:** [SoulGem 0.2.1 on Hangar](https://hangar.papermc.io/rewrite123/soulgem/versions/0.2.1).

**What's new in 0.2.1:** PhantomMenace compatibility.

## Crafting and charging

Both recipes are shapeless and produce **one uncharged gem**:

| Ingredients | Result | Appearance |
|---|---|---|
| 1 lapis lazuli + 1 amethyst shard | Soul Gem | Amethyst shard |
| 1 lapis lazuli + 1 echo shard | Echo Soul Gem | Echo shard |

Hold one uncharged gem in your **off-hand** and earn XP. The gem absorbs newly
earned XP until it contains the full amount needed to advance from your current
level to the next (for example, 7 points at level 0 or 112 at level 30).
The target is fixed when that gem first absorbs XP; existing progress in your
player XP bar does not reduce the cost. Your existing XP is not removed.

Partial progress stays on the gem across swapping, trading, death drops, and
server restarts. Uncharged gems do not stack, so partial progress cannot be
duplicated by splitting a stack. Charged gems glow and stack up to 64.
Only the one off-hand gem charges; excess XP from the completing pickup goes
to the player. Swap another uncharged gem into your off-hand to charge more.
Both kinds have the same charging cost.

Charging handles positive `PlayerExpChangeEvent` awards, including normal XP
pickups after vanilla Mending processing. It does not intercept `/xp`, direct
plugin XP setters, or XP already spent repairing gear. Other plugins that alter
XP awards after SoulGem's highest-priority listener can affect the remainder.
Ordinary renamed shards are not gems. Gems cannot be recycled through crafting
recipes as if they were ordinary shards.

## Enchanting and upgrading

SoulGem uses the **normal vanilla enchanting table screen**. Put your gear or
book in the item slot and **charged soul gems in the lapis slot**, then pick
an offer exactly as in vanilla. Offers, bookshelf power, previews, and seeds
are all vanilla:

| Offer | Charged gems consumed | Player levels consumed |
|---|---|---|
| 1 | 1 | 1 |
| 2 | 2 | 2 |
| 3 | 3 | 3 |

Plain lapis lazuli no longer produces offers; the action bar explains that
soul gems are needed. In creative mode, an empty fuel slot still works as in
vanilla. Don't mix gem kinds: the fuel slot holds one stack.

**Amethyst gems** give the normal vanilla roll. **Echo gems** raise each rolled
enchantment by one, up to its vanilla maximum +1 (Sharpness V becomes
Sharpness VI, Protection IV becomes Protection V, and level-I-only enchantments
can become II). The hover preview already shows the boosted level. Treasure
enchantments such as Mending are not added to the pool, and some enchantments
have little extra effect above their vanilla maximum.

**Already-enchanted gear can be upgraded**: vanilla greys out the table for
enchanted items, but SoulGem re-enables it. Existing enchantments, names,
durability, and lore are kept. Only rolled enchantments that raise a level and
don't conflict with what the item already has are applied; lower rolls never
reduce a level. If an offer would not improve the item, the click is refused
and nothing is consumed. Pick another offer or change your bookshelves to
reroll. Echo gems only boost new rolls, never existing levels, so repeated
enchanting cannot climb indefinitely. Enchanted books can't be re-enchanted
(vanilla doesn't allow it).

### Why charged gems are lapis underneath

The vanilla table's fuel slot only accepts lapis lazuli, and the client
enforces that too, so no plugin can make it take amethyst or echo shards. A
charged gem is therefore a lapis lazuli item that keeps the amethyst or echo
shard **model**, name, glint, and lore. In the inventory it still looks like a
shard. The client may show "Lapis Lazuli" in places that read the base item,
such as the table's "x Lapis Lazuli" cost tooltip. Uncharged gems are still
real shards. Gems can't be used as crafting lapis. Ordinary lapis renamed to
"Soul Gem" is not a gem.

Upgrading from 0.1.x: old charged gems (charged shards) are converted
automatically when the holder joins or opens any inventory, keeping their
count. 0.2.0 removes the custom chest menu, so you no longer hold gear and
gems in your hands.

## Compatibility

- SoulGem 0.2.1 recognizes PhantomMenace's marked phantom repellant and leaves
  its helmet-enchanting offers and purchases to that plugin. Ordinary lapis is
  still rejected. Both plugins must be installed for this exception to apply.

- `soulgem.use` defaults to everyone and permits charging and enchanting.
  Players without it cannot enchant at tables at all.
- Enchanting fires the native `PrepareItemEnchantEvent` and `EnchantItemEvent`,
  so protection, logging, and enchant-modifying plugins keep working. SoulGem
  validates at `HIGH` priority. Plugins that edit enchantments at `HIGHEST`
  can still change the result.
- Anvils, loot, villagers, and enchanted books used outside the table are not
  modified. `craftablebottleofenchanting` works independently.
- No resource pack, datapack, or client mod is required.

## Build and install

```sh
./gradlew build
```

Copy `build/libs/soulgem-0.2.1.jar` into your server's `plugins/` directory and
restart Paper 26.3 using Java 25. Gradle runs on Java 17+ and provisions Java 25;
Windows users can run `gradlew.bat`. No configuration is required.

Tests cover gem identity, legacy conversion, recipes, charging accounting,
lapis refusal, echo boosts, and upgrade merging. The MockBukkit
registry harness targets Paper 26.2; production compiles against Paper 26.3.
Before production deployment, smoke-test the table with gems, enchanted-gear
upgrades, XP orbs with Mending, and your protection plugins on an actual
Paper 26.3 server.

## Publish to Hangar

Create the `soulgem` project under your Hangar account, then:

```sh
export HANGAR_API_TOKEN='your-token'
./gradlew build publishPluginPublicationToHangar
```

Uses the official Hangar publishing Gradle plugin. The token needs version and
page editing permissions. Publication includes this README as the resource
page. Never commit the token or a `hangarkey` file.
