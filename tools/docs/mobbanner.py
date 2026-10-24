#!/usr/bin/env python3
"""README diagrams for mobbanner. Run: python3 tools/docs/mobbanner.py"""

import diagrams as d

PLUGIN = "mobbanner"
ACCENT = "#e5a74e"


def main():
    d.cards(PLUGIN, "features.png", "One mob type, one chunk, two modes", [
        ("Earn a ward", "Personal kill milestones, tracked independently for each supported mob."),
        ("Suppress spawns", "A regular marked banner blocks four eligible spawn reasons in its chunk."),
        ("Attract instead", "A complete 35-block pyramid enables one extra safe spawn attempt."),
        ("Keep its identity", "Persistent markers survive restarts and preserve type when moved."),
        ("No copying", "Ward banners cannot be used in crafting recipes."),
        ("Admin grants", "Trusted admins can give a banner without changing kill progress."),
    ], accent=ACCENT, columns=3)

    d.flow(PLUGIN, "ward-flow.png", "From milestone to mob ward", [
        ("Earn or receive", "Reach a per-player, per-type kill milestone or use the admin give command."),
        ("Place banner", "Place the marked floor or wall banner in the target 16x16 chunk."),
        ("Choose the mode", "No pyramid means suppression; a complete standing pyramid means attraction."),
        ("Apply to matching spawns", "Only the marked mob and four eligible spawn reasons are affected."),
    ], accent=ACCENT, columns=2, card_width=280, card_height=155)

    d.grid_map(PLUGIN, "pyramid.png", "Attraction pyramid: side section", [
        "...B...",
        "...V...",
        "..VVV..",
        ".VVVVV.",
    ], {"B": ("#e5a74e", "Standing ward banner"),
        "V": ("#72a7d8", "Valuable block"),
        ".": ("#142035", "Open space")}, accent=ACCENT, cell=54,
       caption="1x1, 3x3, and 5x5 tiers directly below the banner; 35 blocks total.")


if __name__ == "__main__":
    main()
