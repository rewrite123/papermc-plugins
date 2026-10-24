#!/usr/bin/env python3
"""README diagrams for votersrights. Run: python3 tools/docs/votersrights.py"""

import diagrams as d

PLUGIN = "votersrights"
ACCENT = "#61a8ff"


def main():
    d.cards(PLUGIN, "features.png", "Player-approved server actions", [
        ("Fixed electorate", "Eligible online players are snapshotted when each vote starts."),
        ("Clear threshold", "A configurable share of yes votes is rounded up; defaults to 50%."),
        ("Nine built-ins", "Restart, distance, timeout, difficulty, and time-of-day actions."),
        ("Single ballot", "One yes or no per voter; the proposer automatically votes yes."),
        ("Voter controls", "UUID whitelist and blacklist, plus a separate vote permission."),
        ("Custom actions", "Admins can register carefully controlled, fixed console commands."),
    ], accent=ACCENT, columns=3)

    d.flow(PLUGIN, "vote-flow.png", "From proposal to action", [
        ("Propose", "An eligible player selects an enabled action; at least 3 eligible players online by default."),
        ("Vote", "The proposer starts with yes. Other members of the fixed electorate vote once."),
        ("Pass or expire", "Reaching the rounded-up threshold passes immediately; otherwise the vote ends at 60 seconds."),
        ("Apply", "The action executes in every applicable world, or starts its configured warning period."),
    ], accent=ACCENT, columns=2, card_width=280, card_height=160)

    d.chart(PLUGIN, "threshold.png", "Yes votes needed at the default 50%", [
        ("Required yes votes", [(players, (players + 1) // 2) for players in range(1, 13)], ACCENT),
    ], "Eligible players in electorate", "Yes votes", accent=ACCENT,
       width=860, height=380, step=True, y_max=7, x_max=12,
       markers=[(3, 2, "3 voters: 2 yes", -138, -34), (5, 3, "5 voters: 3 yes", 20, 12)])

    d.chat(PLUGIN, "vote-chat.png", "Proposal and clickable ballot", [
        [("[votersrights] Alex proposes setTimeNight: 1 yes, 0 no; ", "#eaf6ff")],
        [("2 yes required out of 3 eligible players. 60 seconds remaining. ", "#eaf6ff")],
        [("[Vote YES]", "#55ff55", True), ("   ", "#eaf6ff"), ("[Vote NO]", "#ff5555", True)],
        [("[votersrights] Steve voted yes. 2 yes, 0 no; action passes.", "#eaf6ff")],
    ], accent=ACCENT)


if __name__ == "__main__":
    main()
