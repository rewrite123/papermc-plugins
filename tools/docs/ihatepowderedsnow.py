#!/usr/bin/env python3
"""Original README diagrams for ihatepowderedsnow."""
import diagrams as d

PLUGIN = "ihatepowderedsnow"
ACCENT = "#c8e8ff"


def main():
    d.cards(PLUGIN, "features.png", "A small change, exactly where new terrain is made", [
        ("Only new chunks", "Already-generated chunks are not scanned or converted.", "snow"),
        ("Full height", "Every block in the chunk's 256 columns is checked from min to max height.", "snow"),
        ("Snow blocks", "Only POWDER_SNOW becomes SNOW_BLOCK; the rest of the terrain stays as-is.", "stone"),
    ], accent=ACCENT, columns=3, card_width=280, card_height=155)
    d.flow(PLUGIN, "generation-flow.png", "New chunk snow conversion", [
        ("Vanilla generation", "Terrain and features are placed first."),
        ("Inspect", "The populator scans all columns across the world's build height."),
        ("Replace", "Powder snow becomes solid snow blocks; other materials are untouched."),
        ("Explore", "No ongoing scan runs when chunks are loaded later."),
    ], accent=ACCENT, columns=1, card_width=330, card_height=135)


if __name__ == "__main__":
    main()
