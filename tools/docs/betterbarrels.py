#!/usr/bin/env python3
"""README diagrams for BetterBarrels. Run from the repository root."""

import diagrams as d

PLUGIN = "betterbarrels"
ACCENT = "#df9b45"


def controls():
    figure = d.Figure(1040, 390, "Store and retrieve without a menu", ACCENT,
                      "Interactions use the main hand; each barrel has one exact item type.")
    cards = [
        ("Hold item", "Right-click", "Store 1"),
        ("Hold item + sneak", "Right-click", "Store stack"),
        ("Empty hand", "Right-click", "Take 1"),
        ("Empty hand + sneak", "Right-click", "Take up to stack"),
    ]
    card_width, card_height, gap = 226, 220, 18
    for index, (state, action, result) in enumerate(cards):
        x = 24 + index * (card_width + gap)
        y = 24
        figure.box(x, y, x + card_width, y + card_height)
        figure.text((x + card_width / 2, y + 22), state, size=15, bold=True, anchor="ma")
        figure.box(x + 32, y + 62, x + card_width - 32, y + 124,
                   fill=d.SLOT, outline=figure.accent, radius=8, width=2)
        figure.text((x + card_width / 2, y + 93), "BARREL", size=16, bold=True, anchor="mm")
        figure.text((x + card_width / 2, y + 142), action, size=14, fill=d.MUTED, anchor="ma")
        figure.text((x + card_width / 2, y + 174), result, size=16, bold=True, fill=figure.accent, anchor="ma")
    figure.wrapped((28, 270), "Capacity: 2,048 items. Sneak transfers are limited by held stack, remaining capacity, inventory room, and the item's normal stack size.", 112, size=15)
    return figure.save(PLUGIN, "controls.png")


def main():
    d.cards(PLUGIN, "features.png", "BetterBarrels at a glance", [
        ("Bulk storage", "2,048 of one exact item type per placed barrel.", "barrel"),
        ("Exact matching", "Names, lore, enchantments, and item data are part of the match.", "gem"),
        ("No inventory UI", "Store and retrieve with main-hand right-clicks; hoppers are blocked."),
        ("Legacy-safe migration", "Vanilla contents become chests rather than being discarded."),
        ("Resumable scan", "Generated chunks only, checkpointed, with a visible admin status."),
        ("Recovery precautions", "Unique item snapshots precede nonempty barrel conversion."),
    ], accent=ACCENT, columns=3, card_width=290, card_height=152)

    controls()

    d.flow(PLUGIN, "interaction-flow.png", "The storage interaction loop", [
        ("Choose a barrel", "Use any placed barrel. No crafting recipe or client mod is needed."),
        ("Right-click", "A held item stores one. An empty hand retrieves one."),
        ("Sneak to transfer", "Store the held stack, or retrieve up to one normal stack."),
        ("Check the action bar", "See the count and item. Empty the barrel to change its item type."),
    ], accent=ACCENT, columns=1, card_width=500, card_height=130)

    d.flow(PLUGIN, "migration-flow.png", "Migration keeps legacy barrel items", [
        ("Back up worlds", "Back up every world before installation or uninstall conversion."),
        ("Scan loaded worlds", "Read region headers and visit generated chunks; no terrain is generated."),
        ("Convert safely", "Legacy contents move to a chest; empty barrels become managed storage."),
        ("Monitor and verify", "Admins check status, collect overflow, stop Paper, then remove the JAR."),
    ], accent=ACCENT, columns=1, card_width=500, card_height=130)


if __name__ == "__main__":
    main()
