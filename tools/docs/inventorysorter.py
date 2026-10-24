#!/usr/bin/env python3
"""README diagrams for inventorysorter. Run from the repository root."""

import diagrams as d

PLUGIN = "inventorysorter"
ACCENT = "#ffb454"


def example_sort():
    figure = d.Figure(1080, 290, "A sort combines stacks, then orders them", ACCENT,
                      "Example only: categories and material identifiers are sorted alphabetically")
    before = [("Stone", 40), ("Carrot", 40), ("Bone", 4), ("Bread", 8),
              ("Stone", 40), ("Carrot", 24), (None, None), (None, None), (None, None)]
    after = [("Stone", 64), ("Stone", 16), ("Bread", 8), ("Carrot", 64),
             ("Bone", 4), (None, None), (None, None), (None, None), (None, None)]
    figure.text((24, 83), "BEFORE", size=14, bold=True, fill=ACCENT)
    figure.text((24, 207), "AFTER", size=14, bold=True, fill=ACCENT)
    start_x, slot, gap = 120, 78, 10
    for row, stacks in enumerate((before, after)):
        y = 55 + row * 124
        for index, (label, count) in enumerate(stacks):
            figure.item(start_x + index * (slot + gap), y, slot, label, count)
    return figure.save(PLUGIN, "example-sort.png")


def main():
    d.cards(PLUGIN, "features.png", "Inventory sorting at a glance", [
        ("Quick gesture", "Shift-click the same empty slot twice within 500 ms."),
        ("Combine first", "Identical partial stacks merge up to the inventory's stack limit."),
        ("Predictable order", "Categories, then English material identifiers, sort alphabetically."),
        ("Choose the target", "Sort player storage or a supported open container, never together."),
        ("Works in menus", "Your storage slots can be sorted while other inventory screens are open."),
        ("Guarded changes", "Cancelled gestures reset; the next-tick action rechecks the view and slots."),
    ], accent=ACCENT, columns=3, card_width=300, card_height=142)

    d.flow(PLUGIN, "double-click-flow.png", "Sort with a double Shift-click", [
        ("Prepare", "Empty your cursor and find an empty slot in the section you want sorted."),
        ("Double-click", "Hold Shift and click that same slot twice within half a second."),
        ("Recheck", "On the next tick, the plugin checks the same view, empty slot and cursor."),
        ("Sort", "That section is combined and sorted; other inventory sections stay put."),
    ], accent=ACCENT, card_width=244, card_height=160)

    example_sort()


if __name__ == "__main__":
    main()
