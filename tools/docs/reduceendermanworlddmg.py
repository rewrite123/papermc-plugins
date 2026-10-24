#!/usr/bin/env python3
"""README diagrams for Reduce Enderman World Damage. Run from the repo root."""

import diagrams as d

PLUGIN = "reduceendermanworlddmg"
ACCENT = "#ff9b54"


def timing():
    figure = d.Figure(920, 220, "Return timing", ACCENT,
                      "Wall-clock delay; checked every 5 server ticks")
    y = 74
    figure.box(30, y, 420, y + 58, fill=d.PANEL, outline=ACCENT)
    figure.text((225, y + 29), "Random wait: 10-45 seconds", size=17, bold=True,
                fill=d.TEXT, anchor="mm")
    figure.arrow(430, y + 29, 455, y + 29, width=4)
    figure.box(465, y, 690, y + 58, fill=d.PANEL, outline=ACCENT)
    figure.text((577, y + 29), "10-second return window", size=14, bold=True,
                fill=d.TEXT, anchor="mm")
    figure.arrow(700, y + 29, 730, y + 29, width=4)
    figure.text((742, y + 29), "Nearby fallback", size=14, bold=True,
                fill=d.TEXT, anchor="lm")
    figure.text((460, 164), "If the origin stays blocked or unreachable, nearby placement may begin.",
                size=14, fill=d.MUTED, anchor="mm")
    return figure.save(PLUGIN, "return-timing.png")

def main():
    d.cards(PLUGIN, "features.png", "Return stolen blocks, safely", [
        ("Original position", "The saved block state is restored to the pickup coordinates when possible."),
        ("Never overwrite", "Occupied spaces and liquids are not replaced."),
        ("Nearby fallback", "After the return window, try a valid spot close to the original."),
        ("Respect the world", "Returns require mobGriefing and honor cancellable placement events."),
        ("Survive restarts", "Origin and deadline are stored on the Enderman's persistent data."),
        ("Normal behavior", "Endermen are not teleported; death still cancels restoration."),
    ], accent=ACCENT, columns=3, card_height=150)
    d.flow(PLUGIN, "return-flow.png", "How a tracked block gets home", [
        ("Pick up", "An Enderman takes a block; its original state and location are saved."),
        ("Wait", "A random 10-45 second wall-clock delay elapses."),
        ("Return", "The Enderman walks back and restores the block if the origin is empty and reachable."),
        ("Fallback", "After 10 seconds of attempts, try a nearby valid spot; otherwise retry later."),
    ], accent=ACCENT, card_width=235, card_height=165)
    timing()


if __name__ == "__main__":
    main()
