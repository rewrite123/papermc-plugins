# phantommenace

![Phantom above an enchanted helmet protected by a glowing ward](assets/icon.png)

Brew **Phantom Repellant** and enchant a helmet with **Phantom Menace** to stop
phantoms spawning because you have not slept. For **Paper 26.3 / Java 25**.
No client mod, resource pack, or datapack is required.

**Latest release:** [PhantomMenace 0.1.1 on Hangar](https://hangar.papermc.io/rewrite123/phantommenace/versions/0.1.1).

**What's new in 0.1.1:** Repellant now looks like a glowing awkward
potion, and successful survival enchanting drops an empty bottle at the table.

## Brewing

1. Put up to three **water bottles** in a brewing stand.
2. Put a **phantom membrane** in the ingredient slot.
3. Supply **blaze powder** as normal brewing fuel.
4. After the normal brewing cycle, each water bottle becomes one repellant.

One membrane makes up to three repellants, depending on how many water bottles
you use. The filled bottles become repellant; each consumed repellant returns
an empty bottle when used for enchanting. Awkward potions and other potion types do not
work. Use plain water bottles without custom names or effects. Brewing uses
Paper's native brewing recipe system, including normal fuel,
ingredient consumption, hoppers, and cancellable brewing events.

Repellant looks like an **enchanted awkward potion** but is **lapis lazuli
underneath**, so it cannot be drunk and the vanilla enchanting table accepts it
in its fuel slot. The potion model, awkward potion contents, and glint are visual;
they do not make it a drinkable potion. It has its own name and
persistent identity; renamed potions or lapis do not work. Vanilla cost
tooltips can still call it lapis. Repellant cannot be used as crafting lapis.
Previously brewed charcoal-model repellant still works and also returns bottles.

## Enchanting

Open an enchanting table, put a helmet in the item slot, and repellant in the
lapis slot. Select the first offer to add **Phantom Menace I** for **one
repellant and one player level**. No bookshelves are needed. Creative mode uses
vanilla's free enchanting behavior.

After a successful non-creative enchantment, **one empty glass bottle drops
above the enchanting table**. Cancelled or unsuccessful enchanting returns
nothing; creative enchanting does not generate bottles because it consumes no
repellant.

Leather, copper, chainmail, iron, golden, diamond, netherite, and turtle helmets
are supported. Already-enchanted helmets work, and keep their enchantments,
name, durability, and other metadata. Helmets already carrying Phantom Menace
cannot buy it again. Books, pumpkins, mob heads, and non-helmet armor do not work.

This is a real, persistent custom enchantment registered using Paper's bootstrap
registry API, not a lore label. It is not added to random enchanting, loot, or
villager enchantment pools. Vanilla anvils and grindstones handle it as an
enchantment; protection only works while the enchanted helmet is worn.

The plugin cancels natural phantom pre-spawns associated with the wearer.
It does not reset insomnia, affect other players' spawning, remove existing
phantoms, make the player immune to attacks, or block command/spawner phantoms.

### SoulGem compatibility

If SoulGem is installed, use **SoulGem 0.2.1 or newer** (the compatibility update
in this repository). Older SoulGem versions reject repellant as ordinary lapis.
Repellant only adds Phantom Menace; soul gems keep their own enchanting behavior.
Other plugins can still cancel native enchanting and brewing events.

## Build and install

```sh
./gradlew build
```

Install `build/libs/phantommenace-0.1.1.jar` in your server's `plugins/` folder
and restart Paper with Java 25. Registry registration requires a full restart;
do not hot-load this plugin. No configuration, commands, or permissions.

Back up your world before installing or removing custom-enchantment plugins.
Keep the plugin installed while retaining items with its custom enchantment;
removing the registry entry can cause Minecraft to discard that enchantment.

## Publishing

Create the `rewrite123/phantommenace` Hangar project, then provide a token through
`HANGAR_API_TOKEN` and run:

```sh
./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar
```

The official Hangar publishing plugin uploads the JAR and syncs this README.
Never commit API tokens.

Regenerate the original 512 x 512 project icon from the repository root:

```sh
python3 tools/generate-project-icons.py --project phantommenace
```
