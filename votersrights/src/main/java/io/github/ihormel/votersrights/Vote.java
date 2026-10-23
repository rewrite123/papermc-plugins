package io.github.ihormel.votersrights;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class Vote {
    static final long DURATION_MILLIS = 60_000;
    private final Set<UUID> electorate;
    private final Map<UUID, Boolean> ballots = new HashMap<>();
    private final int required;
    private final long deadline;

    Vote(Set<UUID> electorate, int percentage, long now) {
        if (electorate.isEmpty() || percentage < 1 || percentage > 100) {
            throw new IllegalArgumentException("A vote needs voters and a percentage from 1 to 100.");
        }
        this.electorate = Set.copyOf(electorate);
        required = (int) (((long) electorate.size() * percentage + 99) / 100);
        deadline = now + DURATION_MILLIS;
    }

    void cast(UUID voter, boolean yes, long now) {
        if (now >= deadline) throw new IllegalArgumentException("The vote has expired.");
        if (!electorate.contains(voter)) throw new IllegalArgumentException("You were not eligible when this vote started.");
        if (ballots.putIfAbsent(voter, yes) != null) throw new IllegalArgumentException("You have already voted.");
    }

    int yes() { return (int) ballots.values().stream().filter(Boolean::booleanValue).count(); }
    int no() { return ballots.size() - yes(); }
    int required() { return required; }
    int total() { return electorate.size(); }
    boolean passed() { return yes() >= required; }
    boolean expired(long now) { return now >= deadline; }
    long secondsRemaining(long now) { return Math.max(0, (deadline - now + 999) / 1000); }
}
