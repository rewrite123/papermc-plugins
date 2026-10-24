#!/usr/bin/env python3
"""README diagrams for FlipDisc. Run from the repository root."""

import diagrams as d

PLUGIN = "flipdisc"
ACCENT = "#bd8bff"


def main():
    d.cards(PLUGIN, "features.png", "FlipDisc at a glance", [
        ("Fixed pairs", "Every eligible disc has one permanent opposite and flips back to its original."),
        ("Vanilla crafting", "Use a 2x2 inventory grid or a 3x3 table. Each craft consumes and returns one disc."),
        ("Keep your item", "Names, lore and custom item data carry across the Paper item-type conversion."),
        ("No custom audio", "Output is an ordinary vanilla disc. No resource pack, client mod or bundled music."),
        ("Recipe-book ready", "Recipes are discovered when players join. Crafter blocks cannot automate flips."),
        ("Safe exclusions", "Special discs and discs with overridden jukebox playback are not changed."),
    ], accent=ACCENT, columns=3, card_width=300, card_height=156)

    d.recipe(PLUGIN, "flip-recipe.png", "Example: Cat flips to Tears",
             [["Music Disc Cat"]], "Music Disc Tears", note="Eligible discs work alone; pairs flip both ways.",
             accent=ACCENT, shapeless=True)

    d.flow(PLUGIN, "flip-flow.png", "Flip a disc in three steps", [
        ("Place one disc", "Put one eligible disc alone in any slot of the 2x2 or 3x3 crafting grid."),
        ("Take the opposite", "The recipe preview shows its fixed paired song. Crafting produces one disc."),
        ("Play or flip back", "Use it in a vanilla jukebox, or craft it once more to return to the first song."),
    ], accent=ACCENT, card_width=260, card_height=190)


if __name__ == "__main__":
    main()
