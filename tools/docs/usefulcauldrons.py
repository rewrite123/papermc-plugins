#!/usr/bin/env python3
"""README diagrams for UsefulCauldrons. Run from the repository root."""

import diagrams as d

PLUGIN = "usefulcauldrons"
ACCENT = "#75d6a5"


def main():
    d.cards(PLUGIN, "features.png", "UsefulCauldrons at a glance", [
        ("Automatic recipes", "Drop ordinary ingredients into cauldrons. Matching recipes process without menus."),
        ("Custom meals", "Cook stews, soups, warm milk, chocolate and candy with documented effects."),
        ("Dye work", "Color water, recolor items, sheep and players, or harden concrete powder."),
        ("Charcoal", "Seal eligible fuel over a heated, dry cauldron for one minute."),
        ("Stored contents", "Ingredients and food stay inside; retrieval is deliberate and safe."),
        ("Persistent", "Contents save to disk and floating display icons are recreated after reloads."),
    ], accent=ACCENT, columns=3, card_width=300, card_height=156)

    d.flow(PLUGIN, "cauldron-flow.png", "The cauldron workflow", [
        ("Choose the pot", "Use water for stew batches and dye work. Dry cauldrons handle heated recipes."),
        ("Set the conditions", "Add heat below for cooking. Keep the top open, except when making charcoal."),
        ("Drop ingredients", "Use ordinary items. The plugin stores them and runs a recipe as soon as it matches."),
        ("Collect or retrieve", "Take servings with the right gesture, or sneak-right-click empty-handed to empty contents."),
    ], accent=ACCENT, card_width=240, card_height=164)

    d.cards(PLUGIN, "stew-recipes.png", "Stews: two servings per batch", [
        ("Golden Flavor", "Golden apple + golden carrot + glow berries or cocoa beans + blaze powder. Regeneration I, 8 s; 2.5 absorption hearts."),
        ("Speedy Stew", "Raw rabbit or raw fish + nether wart + carrot + sweet berries + blaze powder. Speed I, 30 s."),
        ("Carrota Stew", "4 golden carrots + short grass + spider eye + nether wart + blaze powder. Night vision I, 30 s."),
        ("Spicy Chilli", "Raw beef + beetroot + dried kelp + gunpowder + magma cream + nether wart + blaze powder. Fire resistance I, 30 s."),
        ("Hearty Stew", "Raw beef + porkchop + mutton + pumpkin + potato + nether wart + 2 blaze powder. Strength I, 10 s."),
        ("Fish Soup", "Cod + salmon + carrot + short grass + vine + nether wart + blaze powder. Water breathing I, 10 s."),
        ("Vanilla stews", "Mushroom, beetroot and rabbit stew batches yield two servings. Each uses one water level and no blaze powder."),
        ("Suspicious stew", "Red mushroom + brown mushroom + a supported flower. The flower's vanilla effect and duration are retained."),
    ], accent=ACCENT, columns=2, card_width=410, card_height=166)

    d.cards(PLUGIN, "processing-recipes.png", "Other cauldron processing", [
        ("Warm milk", "Dry + heated + open. 1 milk bucket, 4 bowls and 1 blaze powder make 4 servings; returns a bucket."),
        ("Hot chocolate", "Upgrade all 4 stored warm-milk servings with 6 cocoa beans + 2 sugar. No partial upgrade."),
        ("Candy", "Dry + heated + open. 1 supported food + 26 sugar, or 12 honey bottles. Honey returns 12 bottles."),
        ("Wax and honey", "1 raw beef or porkchop + gunpowder makes wax. Honeycomb + glass bottle makes wax and honey."),
        ("Dyed concrete", "4 sand + 4 gravel in dyed water makes 8 concrete and consumes 8 dye uses. No heat needed."),
        ("Charcoal", "Dry + heated + covered. Eligible wood and plant fuels cook for 60 seconds; groups vary by fuel."),
    ], accent=ACCENT, columns=2, card_width=410, card_height=166)

    d.flow(PLUGIN, "dyed-water.png", "Dyed water uses", [
        ("Color", "Drop one ordinary dye into water. The color has 64 uses; adding the same dye again costs nothing."),
        ("Transform", "Drop dyeable items inside or use one per right-click. Each item uses one charge."),
        ("Finish", "Four sand + four gravel create eight matching concrete for eight uses. Empty the pot with a bucket when full."),
    ], accent=ACCENT, card_width=260, card_height=176)


if __name__ == "__main__":
    main()
