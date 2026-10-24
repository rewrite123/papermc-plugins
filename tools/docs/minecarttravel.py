#!/usr/bin/env python3
"""Generate original MinecartTravel documentation diagrams."""

import diagrams

PLUGIN = "minecarttravel"
ACCENT = "#f0a34a"


diagrams.cards(
    PLUGIN,
    "features.png",
    "MinecartTravel at a glance",
    [
        ("Timed boost", "Ride continuously above the minimum speed; safe straight track earns the configured boost."),
        ("Ride to register", "Record a route by riding between lodestones. Curves and actual switch choices are tracked."),
        ("Fast travel", "After the ride timer, a single-player rideable cart can travel to the endpoint ahead."),
        ("Safety first", "Loaded track, clear headroom, clear entities, route checks, and temporary arrival tickets."),
    ],
    accent=ACCENT,
    columns=2,
    card_width=340,
    card_height=150,
)


diagrams.flow(
    PLUGIN,
    "ride-flow.png",
    "From a ride to fast travel",
    [
        ("Ride", "Stay on rails and move continuously above 6 blocks/second."),
        ("Register", "Ride a lodestone-to-lodestone line once with /minecarttravel register."),
        ("Boost", "After 15 seconds, safe straight track targets 90 blocks/second."),
        ("Travel", "After 30 seconds total, a qualified cart prepares the destination ahead."),
    ],
    accent=ACCENT,
    columns=4,
    card_width=245,
    card_height=158,
)


diagrams.grid_map(
    PLUGIN,
    "route-layout.png",
    "A registered route",
    [
        "L=========C=========L",
        ".....................",
        ".....................",
    ],
    {
        "L": ("#ba8be8", "Lodestone endpoint"),
        "=": ("#aeb7c5", "Recorded straight rail"),
        "C": (ACCENT, "Cart on route"),
        ".": ("#0f1727", "Open space"),
    },
    accent=ACCENT,
    cell=26,
    caption="Both end rails run straight into lodestones at the same height. The cart rides this line once to register it.",
)
