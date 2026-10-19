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
  and fast travel along automatically tracked lodestone-to-lodestone rail lines.

## Project icons

Project icons are saved as `assets/icon.png` inside their project folders.
The eight-icon set uses 512 x 512 images. The matching
Minecraft-inspired pixel art is drawn from scratch, without external textures.
Regenerate all eight icons with Python 3 and ImageMagick installed:

```sh
python3 tools/generate-project-icons.py
```

Regenerating images does not upload them to Hangar or publish a plugin release.
