#!/usr/bin/env python3
"""README diagrams for Spider Eye Nether Wart Block. Run from repository root."""

import diagrams as d

PLUGIN = "spidereyenetherwartblock"
ACCENT = "#e5835f"


def main():
    d.cards(PLUGIN, "features.png", "Spider eyes into compact storage", [
        ("Nine for one", "Craft a full 3x3 of ordinary spider eyes into one nether wart block."),
        ("Vanilla result", "The output is a normal block and works with vanilla building and storage."),
        ("Recipe-book ready", "Online players and players joining discover the recipe automatically."),
        ("No extra setup", "No commands, permissions, configuration, client mods or runtime dependencies."),
    ], accent=ACCENT, columns=2, card_width=360, card_height=154)

    d.recipe(PLUGIN, "spider-eye-recipe.png", "Nine spider eyes -> one nether wart block",
             [["Spider Eye", "Spider Eye", "Spider Eye"],
              ["Spider Eye", "Spider Eye", "Spider Eye"],
              ["Spider Eye", "Spider Eye", "Spider Eye"]],
             "Nether Wart Block", note="Shaped recipe: fill every cell of a crafting table's 3x3 grid.", accent=ACCENT)

    d.flow(PLUGIN, "quick-start.png", "Craft the block", [
        ("Open a table", "The recipe needs a 3x3 crafting grid; the 2x2 inventory grid is too small."),
        ("Fill all cells", "Place one ordinary spider eye in each of the nine slots. Fermented eyes do not count."),
        ("Take one block", "The recipe yields one vanilla nether wart block and consumes nine spider eyes."),
    ], accent=ACCENT, card_width=260, card_height=160)


if __name__ == "__main__":
    main()
