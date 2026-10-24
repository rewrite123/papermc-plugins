#!/usr/bin/env python3
"""Original README diagrams for endercraftbuildings."""
import diagrams as d

PLUGIN = "endercraftbuildings"
ACCENT = "#b57cff"


def main():
    d.cards(PLUGIN, "features.png", "From a blueprint to a recalled building", [
        ("Remember", "Scan two corners; the memory crystal keeps the named design.", "amethyst shard"),
        ("Pay for copies", "A taper consumes an empty crystal and the survival materials.", "vault"),
        ("Place safely", "The amethyst cluster outlines space before you commit.", "amethyst"),
        ("Recall", "Hold right-click; the building appears and the storage crystal breaks.", "ender pearl"),
    ], accent=ACCENT, columns=2, card_width=330, card_height=145)
    d.flow(PLUGIN, "building-flow.png", "A building's journey", [
        ("Scan", "Two opposite corners; up to 32 blocks on each side."),
        ("Name", "Name the design in private chat; the memory crystal is reusable."),
        ("Imprint", "At a taper, spend an empty crystal and building materials."),
        ("Place", "Choose the front-left corner and check the 30-second outline."),
        ("Recall", "Hold right-click for 2 + 0.05 seconds per block, up to 30 seconds."),
    ], accent=ACCENT, columns=1, card_width=330, card_height=135)
    d.recipe(PLUGIN, "recipe-memory-crystal.png", "Memory storage crystal", [
        [None, "amethyst shard", None], [None, "ender pearl", None], [None, "soul lantern", None],
    ], "memory crystal", note="Reusable; shown with the soul-lantern icon.", accent=ACCENT)
    d.recipe(PLUGIN, "recipe-empty-crystal.png", "Empty storage crystal", [
        ["amethyst shard", None, None], [None, None, None], [None, None, "eye of ender"],
    ], "empty crystal", note="Any of the four diagonal arrangements works.", accent=ACCENT)
    d.recipe(PLUGIN, "recipe-taper.png", "Ender Biconical Taper", [
        ["ender pearl", None, "diamond"], ["obsidian", None, "obsidian"], ["obsidian", "obsidian", "obsidian"],
    ], "vault taper", note="Placeable vault block; vault behavior is disabled.", accent=ACCENT)


if __name__ == "__main__":
    main()
