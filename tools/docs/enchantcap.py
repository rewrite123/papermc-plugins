#!/usr/bin/env python3
"""README images for enchantcap. Run: python3 tools/docs/enchantcap.py"""

import diagrams as d

PLUGIN = "enchantcap"
ACCENT = "#e3a72f"


def main():
    d.cards(PLUGIN, "features.png", "EnchantCap at a glance", [
        ("No Too Expensive wall", "Valid anvil results remain available; costs cap at 39 levels."),
        ("Material repair pricing", "Repair cost follows the consumed fraction of crafting materials."),
        ("Optional higher levels", "Admins can allow higher enchantment levels through anvil merges."),
        ("Vanilla compatibility", "Ingredients, conflicts, prior work, and repair behavior stay vanilla."),
        ("Live configuration", "The extra-level limit can be saved and applied without restart."),
        ("No client changes", "The 39-level cap avoids vanilla's misleading 40-level display."),
    ], accent=ACCENT)

    d.flow(PLUGIN, "anvil-flow.png", "A normal anvil operation with EnchantCap", [
        ("Open anvil", "Raise the server threshold before vanilla calculates its result."),
        ("Check result", "Keep valid vanilla operations; do not create rejected results."),
        ("Price operation", "Cap cost at 39, then scale valid raw-material repairs."),
        ("Take result", "Vanilla ingredients and XP payment rules still apply."),
    ], accent=ACCENT, columns=4, card_width=210, card_height=160)

    points = [(n, (39 * n + 2) // 3) for n in range(1, 4)]
    two = [(n, (39 * n + 1) // 2) for n in range(1, 3)]
    five = [(n, (39 * n + 4) // 5) for n in range(1, 6)]
    eight = [(n, (39 * n + 7) // 8) for n in range(1, 9)]
    d.chart(PLUGIN, "repair-pricing.png", "Material-repair charge at the 39-level cap", [
        ("Pickaxe / axe (3)", points, ACCENT),
        ("Sword / hoe (2)", two, "#5ab4ff"),
        ("Helmet (5)", five, "#a78bfa"),
        ("Chestplate (8)", eight, "#55c7a5"),
    ], "Repair materials consumed", "XP levels", accent=ACCENT, y_max=42)


if __name__ == "__main__":
    main()
