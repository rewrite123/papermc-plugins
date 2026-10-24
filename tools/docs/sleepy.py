#!/usr/bin/env python3
"""README diagrams for sleepy. Run: python3 tools/docs/sleepy.py"""

import diagrams as d

PLUGIN = "sleepy"
ACCENT = "#62d6c5"


def required(active, percent, minimum=0):
    by_percent = max(1, (active * percent + 99) // 100)
    return min(by_percent, minimum) if minimum else by_percent


def main():
    d.cards(PLUGIN, "features.png", "A smarter night-skip threshold", [
        ("Percentage", "Default 50%, rounded up among non-spectator players."),
        ("Optional minimum", "A fixed number can be enough on a busy server."),
        ("Whichever is fewer", "The two targets are alternatives, not cumulative requirements."),
        ("Per-world", "Each world uses its own active player count and sleep target."),
        ("Vanilla beds", "Minecraft still handles bed rules, the countdown, and morning."),
        ("Live refresh", "Settings and player counts are reapplied as the server changes."),
    ], accent=ACCENT, columns=3)

    d.flow(PLUGIN, "sleep-flow.png", "How Sleepy updates the rule", [
        ("Count players", "Count non-spectators in one world; spectators do not count."),
        ("Find target", "Compare rounded-up percentage with the optional minimum; use fewer sleepers."),
        ("Set gamerule", "Choose the effective whole-percent value for vanilla's sleep check."),
        ("Minecraft skips night", "When enough players sleep, vanilla handles the transition and waking."),
    ], accent=ACCENT, columns=2, card_width=280, card_height=150)

    players = range(1, 51)
    d.chart(PLUGIN, "sleeper-targets.png", "Required sleepers: 50% with optional minimum 10", [
        ("Percentage only", [(count, required(count, 50)) for count in players], "#62d6c5"),
        ("50% or minimum 10", [(count, required(count, 50, 10)) for count in players], "#f2b84b"),
    ], "Non-spectator players in this world", "Sleepers required", accent=ACCENT,
       width=900, height=410, step=True, y_max=28, x_max=50,
       markers=[(30, 10, "30 players: 10 sleepers", 18, -28), (4, 2, "4 players: 2 sleepers", 18, 10)])


if __name__ == "__main__":
    main()
