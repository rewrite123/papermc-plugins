# FlipDisc

**Two sides. Two songs. One disc.**

Give ordinary music discs a B-side! Place one eligible disc alone in your
crafting grid to flip it to its permanent other song. Flip it again to return
to the original.

No random rerolls, no endless song cycling, and no resource pack required.
Each craft consumes one disc and returns one disc.

## How to flip

1. Put **one eligible disc, with nothing else**, anywhere in your inventory's
   **2 x 2** crafting grid or a crafting table's **3 x 3** grid.
2. Take the output disc.
3. Play it in any vanilla jukebox, or craft it again to flip back.

Recipes appear in your recipe book when you join. Normal crafting handles
shift-clicks and inventory space. Automated crafter blocks cannot flip discs.

## Permanent song pairs

| Side A | Artist | Side B | Artist |
| --- | --- | --- | --- |
| Cat | C418 | Tears | Amos Roddy |
| Blocks | C418 | Lava Chicken | Hyper Potions |
| Chirp | C418 | Bounce | Fingerspit |
| 13 | C418 | Mellohi | C418 |
| Far | C418 | Strad | C418 |
| Mall | C418 | Stal | C418 |
| Wait | C418 | Ward | C418 |

Pairs feature different artists wherever possible. Most eligible songs are by
C418, so the remaining pairs share an artist.

**Every pair is fixed globally.** A Cat disc always flips to Tears, and Tears
always flips back to Cat. Repeated flipping cannot unlock unrelated songs.

## Special discs stay special

**Pigstep, Otherside, 5, Relic, Creator, Creator (Music Box), Precipice, and 11**
remain single-sided and have no flipping recipe. Disc 11 is left unpaired
because there is an odd number of otherwise eligible discs.

Discs with custom jukebox-playback settings are also excluded to avoid changing
another plugin's custom songs. Newly added Minecraft discs are not automatically
assigned a partner.

## Your items stay yours

Renames, lore, and custom item data are preserved when flipping. The result is a
normal vanilla disc for its selected song: no custom jukebox logic, bundled
audio, or client mod. If you remove FlipDisc, existing discs still play the
currently selected song.

## Install

Requires **Paper 26.3** and **Java 25**.

1. Download the plugin JAR.
2. Place it in your server's `plugins` folder.
3. Restart Paper.

No commands, permissions, configuration files, or extra plugins are needed.
To uninstall, stop the server, remove the JAR, and restart.

[Source code and full documentation](https://github.com/rewrite123/papermc-plugins/tree/main/flipdisc)
