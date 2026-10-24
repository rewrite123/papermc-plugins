# endercraftbuildings

Copy buildings into amethyst crystals, store their materials in the empty space
of the End, and recall the finished building anywhere. For
**Paper 26.3 / Java 25**. No resource pack or runtime dependencies required.

## How it works

```text
Memory storage crystal -> scan a building -> name it
Memory (or storage) crystal + empty storage crystal + materials
    -> right-click an Ender Biconical Taper -> storage crystal
Storage crystal -> hold right-click on a block -> building appears, crystal shatters
```

The light bouncing off a building is stored in amethyst like a hologram. Ender
pearls find empty End space to keep the materials in, and the soul lantern's
light makes the hologram stick. The taper splits that entangled light: one beam
to the End, one to an empty storage crystal's ender eye, so the crystal can call
the materials back through the hologram.

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

A placeable block that looks like a vault. Vault behavior is disabled: trial
keys do nothing. Breaking it returns the taper (nothing in creative), and
explosions cannot destroy it.

## Scanning a building

1. Hold a memory storage crystal and right-click a block: the first corner.
2. A purple particle box follows the block you look at. It turns red if the
   selection is larger than **32 x 32 x 32**.
3. Right-click the opposite corner. Sneak + right-click (or switching away from
   the crystal) cancels.
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

Hold a storage crystal and hold right-click on a block:

- the building's **front-left bottom corner** goes on the face you clicked
  (or replaces the clicked grass/flowers/snow layer), and it is rotated to face
  the way you are looking;
- a teal particle box shows where it will go;
- every position the building needs must be air or a dry replaceable plant or
  snow layer. Water, lava and waterlogged blocks block placement. If anything
  is in the way, a red box flashes and the action bar says how many blocks are
  blocking it;
- holding takes **2 seconds + 0.05 seconds per block**, up to 30 seconds.
  Releasing, moving, or looking at another block cancels.

When the hold finishes, the whole building appears at once and the storage
crystal shatters. Each block is checked with a normal block-place event first,
so protection plugins can deny it (nothing is placed and the crystal is kept).

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
