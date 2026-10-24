#!/usr/bin/env python3
"""Original README diagrams for betterlanterns."""
import diagrams as d

PLUGIN = "betterlanterns"
ACCENT = "#52d7e8"


def main():
    d.cards(PLUGIN, "features.png", "Three ways to make a brighter, safer night", [
        ("Reworked recipes", "Candles replace torches in both lantern recipes; soul candles make blue soul lanterns.", "lantern"),
        ("Light halos", "Lanterns add level-15 invisible lights through air up to three block steps.", "soul lantern"),
        ("Natural spawn ward", "Hostile natural spawns within 24 blocks are blocked only in direct sight.", "soul candle"),
    ], accent=ACCENT, columns=3, card_width=280, card_height=155)
    d.flow(PLUGIN, "crafting-flow.png", "Lantern crafting", [
        ("Lantern", "Gold and iron nuggets, a glass pane and one ordinary candle."),
        ("Soul candles", "Four ordinary candles plus soul sand make four soul candles."),
        ("Soul lantern", "Use a soul candle in place of the ordinary candle recipe slot."),
        ("Place", "Both lantern types spread light and protect against visible natural hostile spawns."),
    ], accent=ACCENT, columns=1, card_width=330, card_height=135)
    d.recipe(PLUGIN, "recipe-lantern.png", "Lantern recipe", [
        ["gold nugget", "candle", "gold nugget"],
        ["iron nugget", "glass pane", "iron nugget"],
        ["iron nugget", "iron nugget", "iron nugget"],
    ], "lantern", note="Ordinary candle only; makes one lantern.", accent=ACCENT)
    d.recipe(PLUGIN, "recipe-soul-candle.png", "Soul candle recipe", [
        [None, "candle", None], ["candle", "candle", "candle"], [None, "soul sand", None],
    ], "soul candle", count=4, note="Four ordinary candles plus one soul sand.", accent=ACCENT)
    d.recipe(PLUGIN, "recipe-soul-lantern.png", "Soul lantern recipe", [
        ["gold nugget", "soul candle", "gold nugget"],
        ["iron nugget", "glass pane", "iron nugget"],
        ["iron nugget", "iron nugget", "iron nugget"],
    ], "soul lantern", note="Uses the same lantern recipe with a soul candle.", accent=ACCENT)


if __name__ == "__main__":
    main()
