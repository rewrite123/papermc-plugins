#!/usr/bin/env python3
"""README diagrams for Phantom Menace. Run: python3 tools/docs/phantommenace.py"""

import diagrams as d

PLUGIN = "phantommenace"
ACCENT = "#bd8cff"


def brewing():
    figure = d.Figure(780, 230, "Brew Phantom Repellant", ACCENT,
                      "Use the normal brewing stand and blaze-powder fuel")
    figure.item(42, 36, 84, "water bottle", count=3)
    figure.text((84, 132), "Up to 3", size=14, fill=d.MUTED, anchor="ma")
    figure.text((148, 78), "+", size=25, bold=True, fill=ACCENT, anchor="mm")
    figure.item(190, 36, 84, "phantom membrane")
    figure.text((232, 132), "Ingredient", size=14, fill=d.MUTED, anchor="ma")
    figure.arrow(304, 78, 470, 78, width=5)
    figure.text((386, 52), "Brew", size=15, bold=True, fill=d.TEXT, anchor="ma")
    figure.item(502, 26, 112, "Repellant", highlight=True)
    figure.text((558, 146), "One per bottle", size=14, fill=d.MUTED, anchor="ma")
    figure.text((390, 169), "Plain water bottles only; fuel the stand as usual.",
                size=14, fill=d.MUTED, anchor="mm")
    return figure.save(PLUGIN, "brewing-recipe.png")


def main():
    d.cards(PLUGIN, "features.png", "Phantom Menace at a glance", [
        ("A real enchantment", "Paper registers Phantom Menace I; it is not just an item name."),
        ("Natural spawn ward", "Stops natural phantom pre-spawns associated with the helmet wearer."),
        ("Brew the fuel", "Turn up to three plain water bottles into repellant in one brew."),
        ("One-level offer", "The first enchanting-table offer adds only Phantom Menace I."),
        ("Keeps your gear", "Existing helmet enchantments and metadata are preserved."),
        ("No client setup", "No commands, config, resource pack, or client mod required."),
    ], accent=ACCENT, columns=3, card_height=150)
    brewing()
    d.flow(PLUGIN, "enchanting-flow.png", "From brewing to protected helmet", [
        ("Brew", "Water bottles and phantom membrane make the special repellant."),
        ("Load the table", "Place a supported helmet and repellant in the fuel slot."),
        ("Choose offer one", "Spend one player level and one repellant for Phantom Menace I."),
        ("Wear the helmet", "Natural phantom spawns for that wearer are prevented."),
    ], accent=ACCENT, card_width=235, card_height=160)


if __name__ == "__main__":
    main()
