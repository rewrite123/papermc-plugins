#!/usr/bin/env python3
"""README images for soulgem. Run: python3 tools/docs/soulgem.py"""

import diagrams as d

PLUGIN = "soulgem"
ACCENT = "#b16ce3"


def main():
    d.cards(PLUGIN, "features.png", "SoulGem features", [
        ("Earn the charge", "An off-hand uncharged gem absorbs XP until one new level is stored."),
        ("Use vanilla offers", "Charged gems fuel the familiar enchanting-table screen."),
        ("Echo boost", "Echo gems raise each new rolled enchantment by one level."),
        ("Upgrade gear", "Already-enchanted items can gain valid, non-conflicting improvements."),
        ("Persistent progress", "Partial charge stays on the item through swaps and restarts."),
        ("Two distinct gems", "Amethyst gives normal rolls; echo shard gives boosted rolls."),
    ], accent=ACCENT)

    d.recipe(PLUGIN, "amethyst-recipe.png", "Craft a Soul Gem", [
        ["Lapis", "Amethyst", None], [None, None, None], [None, None, None],
    ], "Soul Gem", note="Shapeless recipe: one lapis lazuli and one amethyst shard.",
       accent=ACCENT, shapeless=True)
    d.recipe(PLUGIN, "echo-recipe.png", "Craft an Echo Soul Gem", [
        ["Lapis", "Echo shard", None], [None, None, None], [None, None, None],
    ], "Echo Soul Gem", note="Shapeless recipe: one lapis lazuli and one echo shard.",
       accent=ACCENT, shapeless=True)

    d.flow(PLUGIN, "soulgem-flow.png", "From crafting to enchanting", [
        ("Craft", "Combine lapis with an amethyst shard or echo shard."),
        ("Charge", "Hold one uncharged gem off-hand while earning XP."),
        ("Fuel", "Put the charged gem in the table's lapis slot."),
        ("Enchant", "Choose a vanilla offer or upgrade enchanted gear."),
    ], accent=ACCENT, columns=4, card_width=205, card_height=165)

    d.flow(PLUGIN, "charging-flow.png", "How a soul gem charges", [
        ("Hold one gem", "Keep an uncharged gem in the off-hand."),
        ("Earn XP", "Positive XP awards fill its stored one-level target."),
        ("Finish charge", "The gem turns into stackable charged fuel."),
        ("Keep excess", "XP beyond the charge target goes to the player."),
    ], accent=ACCENT, columns=4, card_width=205, card_height=165)


if __name__ == "__main__":
    main()
