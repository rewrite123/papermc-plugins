# Paper plugins

Standalone plugins for PaperMC. Each plugin has its own Gradle build and wrapper.

- [tpcost](tpcost/README.md): player-approved teleports paid for with experience levels.
- [inventorysorter](inventorysorter/README.md): shift-double-click an empty slot to
  sort storage by alphabetical item group, then alphabetical item type.
- [enchantcap](enchantcap/README.md): remove the anvil "Too Expensive" restriction
  with a 39-level maximum charge, preserving vanilla enchantment levels.
- [sleepy](sleepy/README.md): skip the night when at least 50% of eligible players
  in a world are sleeping.
- [craftablebottleofenchanting](craftablebottleofenchanting/README.md): bottle XP
  at enchanting tables without lapis and recover empty bottles after use.
- [mobbanner](mobbanner/README.md): earn mob-themed banners from kill milestones
  and place them to suppress that mob's natural spawning in their chunk, or
  attract it with a valuable-block pyramid.
- [soulgem](soulgem/README.md): craft and charge amethyst/echo soul gems with XP,
  then use them instead of lapis to enchant and upgrade gear.
- [minecarttravel](minecarttravel/README.md): earn timed minecart speed boosts
  and fast travel along ride-registered lodestone-to-lodestone rail lines.
- [reduceendermanworlddmg](reduceendermanworlddmg/README.md): Endermen attempt
  to return stolen blocks to their original positions after 10-45 seconds,
  falling back to a nearby spot if they cannot within 10 seconds.
- [flipdisc](flipdisc/README.md): flip ordinary music discs between permanent
  two-song pairs using the crafting grid, preserving single-sided special discs.
- [phantommenace](phantommenace/README.md): brew phantom repellant and enchant
  helmets to prevent insomnia-triggered phantom spawns for the wearer.

## Project icons

Project icons are saved as `assets/icon.png` inside their project folders.
The eleven-icon set uses 512 x 512 images. The matching
Minecraft-inspired pixel art is drawn from scratch, without external textures.
Regenerate all eleven icons with Python 3 and ImageMagick installed:

```sh
python3 tools/generate-project-icons.py
```

To regenerate only one icon, pass `--project reduceendermanworlddmg`.

Regenerating images does not upload them to Hangar or publish a plugin release.
