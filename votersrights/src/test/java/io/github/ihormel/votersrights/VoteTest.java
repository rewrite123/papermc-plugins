package io.github.ihormel.votersrights;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VoteTest {
    @ParameterizedTest
    @CsvSource({"1,50,1", "2,50,1", "3,50,2", "4,50,2", "5,50,3", "3,67,3", "10,100,10", "10,1,1"})
    void thresholdIsRoundedUpAgainstFullElectorate(int count, int percent, int expected) {
        var voters = IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).collect(Collectors.toSet());
        assertEquals(expected, new Vote(voters, percent, 0).required());
    }

    @Test
    void eligibilityDuplicatesNoVotesAndExactExpiryAreEnforced() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var third = UUID.randomUUID();
        var vote = new Vote(Set.of(first, second, third), 50, 100);
        assertThrows(IllegalArgumentException.class, () -> vote.cast(UUID.randomUUID(), true, 100));
        vote.cast(first, true, 100);
        assertThrows(IllegalArgumentException.class, () -> vote.cast(first, false, 101));
        vote.cast(second, false, 101);
        assertEquals(1, vote.yes());
        assertEquals(1, vote.no());
        assertFalse(vote.passed());
        assertFalse(vote.expired(60099));
        assertTrue(vote.expired(60100));
        assertThrows(IllegalArgumentException.class, () -> vote.cast(third, true, 60100));
        assertEquals(0, vote.secondsRemaining(60100));
    }

    @Test
    void electorateIsImmutableAndInvalidModelsAreRejected() {
        var voters = new java.util.HashSet<>(Set.of(UUID.randomUUID()));
        var vote = new Vote(voters, 100, 0);
        voters.clear();
        assertEquals(1, vote.total());
        assertThrows(IllegalArgumentException.class, () -> new Vote(Set.of(), 50, 0));
        assertThrows(IllegalArgumentException.class, () -> new Vote(Set.of(UUID.randomUUID()), 0, 0));
    }
}
