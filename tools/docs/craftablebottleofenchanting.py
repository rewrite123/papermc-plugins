#!/usr/bin/env python3
"""README images for craftablebottleofenchanting. Run: python3 tools/docs/craftablebottleofenchanting.py"""

import diagrams as d

PLUGIN = "craftablebottleofenchanting"
ACCENT = "#4ca7d8"


def main():
    d.cards(PLUGIN, "features.png", "Bottle Experience", [
        ("Bottle at a table", "Right-click with a main-hand glass bottle to open the menu."),
        ("Pay raw XP", "One conversion costs 11 XP points, not 11 levels."),
        ("No lapis", "No lapis or bookshelves are needed for the conversion."),
        ("Recover glass", "A broken experience bottle returns one glass bottle."),
        ("Safe conversion", "No changes occur when XP or output inventory space is missing."),
        ("Vanilla XP drops", "Throwing keeps the ordinary experience-bottle XP behavior."),
    ], accent=ACCENT)

    d.flow(PLUGIN, "bottle-flow.png", "Bottle experience at an enchanting table", [
        ("Open menu", "Right-click a table while holding glass bottles in the main hand."),
        ("Check requirements", "Keep the bottle slot, empty cursor, XP, and inventory space."),
        ("Convert", "Spend 11 raw XP points and one bottle for one throwable."),
        ("Recover", "When it breaks, a glass bottle drops at the impact location."),
    ], accent=ACCENT, columns=4, card_width=215, card_height=165)


if __name__ == "__main__":
    main()
