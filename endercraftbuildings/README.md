# endercraftbuildings

Copy buildings into amethyst crystals, store their materials in the empty space
of the End, and recall the finished building anywhere. For
**Paper 26.3 / Java 25**. No resource pack or runtime dependencies required.

## How it works

```text
Memory storage crystal -> scan a building -> name it
Memory (or storage) crystal + empty storage crystal + materials
    -> right-click an Ender Biconical Taper -> storage crystal
Storage crystal -> place it as an amethyst cluster -> check the outline
    -> hold right-click on the cluster -> building appears, crystal shatters
```

## Lore

Ender pearls don't really fly. They carry whoever throws them through the
empty space of the End and drop them back out where the pearl lands.
Endercrafters worked out that the same empty space could hold far more than a
traveller, if only something could remember where everything belongs.

- **Memory storage crystal.** The soul lantern gives off a special light that
  lets amethyst hold onto an image. Light bouncing off a building is caught in
  the crystal like a hologram, and the ender pearl finds empty End space where
  the building's materials can later be kept. The memory crystal only
  remembers; it never sends anything away, so it is never used up.
- **Empty storage crystal.** Amethyst bound to an eye of ender. It has no
  hologram yet, but the eye is ready to keep track of blocks stored in the End.
- **Ender Biconical Taper.** Two cones of obsidian focus the hologram's light
  and split the entangled beam in two. One beam carries the materials into
  empty End space. The other goes into the empty crystal's ender eye, along
  with a copy of the hologram from the memory or storage crystal. That crystal
  now knows where the materials are stored and where every block must go.
- **Storage crystal.** When used, it pulls the materials back out of the End,
  through the hologram and into the world, rebuilding the structure exactly as
  it was remembered. The crystal can't survive the strain and shatters.

The End doesn't always let go cleanly. Now and then something hitches a ride
back with the materials: an endermite, crawling out of the taper or the newly
recalled building.

## Crafting

All four recipes appear in the recipe book automatically.

### Memory storage crystal

```text
Amethyst shard
Ender pearl
Soul lantern
```

Shown with the soul lantern icon. Not placeable, does not stack and is never
consumed.

### Empty storage crystal

```text
Amethyst shard
               Eye of ender
```

Any diagonal works, in either order. Shown with the amethyst shard icon.

### Ender Biconical Taper

```text
Ender pearl                Diamond
Obsidian                   Obsidian
Obsidian      Obsidian     Obsidian
```

A placeable block that looks like a vault. Vault behavior is disabled: it
always shows a floating eye of ender, never opens or reacts when players come
near, never cycles items, and trial keys do nothing. Like any vault showing an
item, it still gives off faint flames and an occasional ambient hum. Breaking
it returns the taper (nothing in creative), and explosions cannot destroy it.

## Scanning a building

1. Hold a memory storage crystal and right-click a block: the first corner.
2. A purple particle box follows the block you look at. It turns red if the
   selection is larger than **32 x 32 x 32**.
3. Right-click the opposite corner. Sneak + right-click (or switching away from
   the crystal) cancels.

Corners don't need a block. If you aren't looking at a block within reach, the
corner is the empty spot **3 blocks in front of you**, marked with a small
purple box, so you can select open space above or beside a building without
placing marker blocks.
4. Type the building's name in chat (1-32 characters), or `cancel`. The prompt
   expires after 60 seconds; the chat message is not broadcast.

Scanning stores block shapes and states only: chest contents, sign text,
banner patterns and other block-entity data are not copied. Air is ignored, so
placement only needs room where the building has blocks. Fluids, bedrock,
portals, spawners, command/structure blocks, budding amethyst, infested blocks
and other unobtainable blocks are skipped, and you are told how many. Waterlogged
blocks are copied dry, filled cauldrons become empty cauldrons and pistons are
copied retracted. Scanning again replaces the crystal's building.

The building is remembered relative to the direction you faced when you clicked
the second corner, from the nearest left corner.

## Imprinting storage crystals

Right-click a placed taper with a named memory storage crystal or an existing
storage crystal. Your inventory must contain:

- one **empty storage crystal**, and
- the building's materials as plain, unrenamed items.

Both are consumed and you receive a storage crystal for that building. The
source crystal is kept, so you can make as many copies as you can afford. If
anything is missing, nothing is taken and chat lists what you still need.
Creative players still need an empty storage crystal but no materials.

Materials are the items used to place each block: doors, beds and tall plants
cost one item, double slabs cost two slabs, and candles, sea pickles, turtle
eggs and snow layers cost one item per piece. Farmland and dirt paths cost dirt.

## Placing a building

1. Right-click a block with a storage crystal to set it down as an **amethyst
   cluster**. The cluster marks the building's **front-left bottom corner**
   (it replaces grass, flowers or a snow layer, otherwise it sits on the face
   you clicked), and the building is rotated to face the way you were looking.
2. A teal particle box shows exactly where the building will go for
   **30 seconds**. Right-click the cluster at any time to show it for another
   30 seconds. The box turns red while something is in the way.
3. Every position the building needs must be air or a dry replaceable plant or
   snow layer. Water, lava and waterlogged blocks block placement, and the
   action bar says how many blocks are in the way.
4. Hold right-click on the cluster to recall the building. Charging takes
   **2 seconds + 0.05 seconds per block**, up to 30 seconds, and shows progress
   in the action bar. Letting go cancels.

When the charge finishes, the whole building appears at once and the crystal
shatters. Each block is checked with a normal block-place event first, so
protection plugins can deny it (nothing is placed and the crystal is kept).

A placed crystal stays until it is used or broken. Breaking it, or knocking it
loose by removing the block it sits on, returns the storage crystal (nothing is
returned when broken in creative). Explosions, pistons and mobs can't move or
destroy it. Sneak + right-click a cluster while holding a block to build
against it normally.

## Endermites

Something occasionally follows the materials out of the End. By default there is
a **5%** chance of an endermite when a taper imprints a crystal and a **5%**
chance when a building is placed. Change them in `config.yml` and restart:

```yaml
endermite-chance:
  taper: 0.05
  placement: 0.05
```

## Notes

- Crystals cannot be used as amethyst shards in crafting, crafters or smithing,
  and cannot be given to allays.
- Holding right-click with a usable item (food, a bow, a shield) in the other
  hand can stop the client from repeating the click; use an empty hand or a
  plain block if charging keeps cancelling.
- Blocks are placed without physics updates so multi-block pieces such as doors
  and beds arrive intact. Gravity blocks may fall once something updates them.

## Build

```sh
./gradlew build
```

The plugin JAR is written to `build/libs/`.

## Source and support

- [GitHub](https://github.com/rewrite123/papermc-plugins)
- [Report an issue](https://github.com/rewrite123/papermc-plugins/issues)
