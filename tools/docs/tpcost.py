#!/usr/bin/env python3
"""README images for tpcost. Run: python3 tools/docs/tpcost.py"""

import math

import diagrams as d

PLUGIN = "tpcost"
ACCENT = "#7dde45"


def cost(distance, world_surcharge=0):
    return min(25, max(3, math.ceil(distance * 3 / 1000))) + world_surcharge


def main():
    points = [(x, cost(x)) for x in range(0, 10001, 10)]
    cross = [(x, cost(x, 3)) for x in range(0, 10001, 10)]
    d.chart(PLUGIN, "cost-by-distance.png", "Default teleport cost by distance",
            [("Same world", points, ACCENT), ("Different worlds (+3)", cross, "#5ab4ff")],
            "Distance between players (blocks)", "Levels", accent=ACCENT, y_max=30,
            markers=[(1000, 3, "up to 1K: 3", 10, -30), (3000, 9, "3K: 9", 14, 6), (3330, 10, "3.33K: 10", 10, -32),
                     (8010, 25, "past 8K: capped at 25", -40, 12)])

    d.flow(PLUGIN, "request-flow.png", "How a paid teleport works", [
        ("Request", "Steve runs /tpa Alex. The request lasts 60 seconds; nothing is paid yet."),
        ("Approve", "Alex clicks [Accept] in chat (or types /tpaccept). [Deny] or /tpdeny refuses."),
        ("Recalculate", "The cost is recalculated from both players' current positions."),
        ("Teleport & pay", "Steve is teleported to Alex and only then loses the levels."),
    ], accent=ACCENT)

    green, white, red, gray, yellow = "#55ff55", "#ffffff", "#ff5555", "#aaaaaa", "#ffff55"
    d.chat(PLUGIN, "chat-request.png", "What players see in chat", [
        [("<Steve> ", white), ("/tpa Alex", yellow)],
        [("[tpcost] Request sent to Alex. You pay XP levels on acceptance.", white)],
        [("— on Alex's screen —", gray)],
        [("[tpcost] Steve wants to teleport to you. ", white), ("[Accept]", green, True), (" ", white),
         ("[Deny]", red, True)],
        [("Expires in 60 seconds. You can also use /tpaccept or /tpdeny Steve.", white)],
        [("[tpcost] Teleported to Alex for 9 levels.", white), ("   (Steve, 3,000 blocks away)", gray)],
    ], accent=ACCENT)

    d.cards(PLUGIN, "features.png", "Features at a glance", [
        ("Pay with levels", "3 levels per 1,000 blocks, minimum 3, capped at 25. Admins can change every number."),
        ("Player approval", "Nobody arrives uninvited: the target must accept. Requests expire after 60 s."),
        ("Clickable chat", "Accept or deny straight from the chat message without typing commands."),
        ("Do not disturb", "/tpclose refuses all incoming requests until /tpopen. Saved across restarts."),
        ("Location privacy", "Price lookups are off by default so distance quotes cannot reveal positions."),
        ("Live admin tuning", "/tpcostadmin changes prices instantly and saves them to config.yml."),
    ], accent=ACCENT)


if __name__ == "__main__":
    main()
