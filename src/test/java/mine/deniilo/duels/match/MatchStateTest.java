package mine.deniilo.duels.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import mine.deniilo.duels.match.MatchResult.Outcome;
import org.junit.jupiter.api.Test;

class MatchStateTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    private MatchState active(boolean ranked) {
        MatchState s = new MatchState(UUID.randomUUID(), a, b, ranked);
        s.advanceTo(MatchPhase.COUNTDOWN);
        s.advanceTo(MatchPhase.ACTIVE);
        return s;
    }

    @Test
    void deathGivesWinToOpponent() {
        MatchState s = active(true);
        s.reportLoss(a, EndReason.DEATH);
        MatchResult r = s.resolve().orElseThrow();
        assertEquals(Outcome.WIN, r.outcome());
        assertEquals(b, r.winner());
        assertEquals(a, r.loser());
        assertEquals(EndReason.DEATH, r.reason());
        assertTrue(r.changesRating());
        assertEquals(MatchPhase.FINISHING, s.phase());
    }

    @Test
    void forfeitIsLossWithOwnReason() {
        MatchState s = active(true);
        s.reportLoss(b, EndReason.FORFEIT);
        MatchResult r = s.resolve().orElseThrow();
        assertEquals(a, r.winner());
        assertEquals(EndReason.FORFEIT, r.reason());
    }

    @Test
    void bothLoseInSameTickIsDrawWithoutRating() {
        MatchState s = active(true);
        s.reportLoss(a, EndReason.DEATH);
        s.reportLoss(b, EndReason.DEATH);
        MatchResult r = s.resolve().orElseThrow();
        assertEquals(Outcome.DRAW, r.outcome());
        assertEquals(EndReason.SIMULTANEOUS, r.reason());
        assertFalse(r.changesRating());
        assertNull(r.winner());
    }

    @Test
    void doubleExitIsDraw() {
        MatchState s = active(true);
        s.reportLoss(a, EndReason.FORFEIT);
        s.reportLoss(b, EndReason.FORFEIT);
        assertEquals(Outcome.DRAW, s.resolve().orElseThrow().outcome());
    }

    @Test
    void firstDeathDoesNotCloseMatchBeforeResolve() {
        MatchState s = active(true);
        s.reportLoss(a, EndReason.DEATH);
        assertEquals(MatchPhase.ACTIVE, s.phase());
        assertTrue(s.result().isEmpty());
    }

    @Test
    void timeoutIsDrawButLossWinsOverTimeoutInSameTick() {
        MatchState t = active(true);
        t.reportTimeout();
        MatchResult draw = t.resolve().orElseThrow();
        assertEquals(Outcome.DRAW, draw.outcome());
        assertEquals(EndReason.TIMEOUT, draw.reason());

        MatchState l = active(true);
        l.reportTimeout();
        l.reportLoss(a, EndReason.DEATH);
        assertEquals(Outcome.WIN, l.resolve().orElseThrow().outcome());
    }

    @Test
    void leavingBeforeActiveCancelsWithoutRating() {
        MatchState s = new MatchState(UUID.randomUUID(), a, b, true);
        s.advanceTo(MatchPhase.COUNTDOWN);
        s.reportLoss(a, EndReason.FORFEIT);
        MatchResult r = s.resolve().orElseThrow();
        assertEquals(Outcome.CANCELLED, r.outcome());
        assertEquals(EndReason.LEFT_BEFORE_START, r.reason());
        assertFalse(r.changesRating());
    }

    @Test
    void technicalCancelBeatsEverything() {
        MatchState s = active(true);
        s.reportLoss(a, EndReason.DEATH);
        s.reportCancel(EndReason.SHUTDOWN);
        MatchResult r = s.resolve().orElseThrow();
        assertEquals(Outcome.CANCELLED, r.outcome());
        assertEquals(EndReason.SHUTDOWN, r.reason());
    }

    @Test
    void resultIsFixedOnceAndLateEventsAreIgnored() {
        MatchState s = active(true);
        s.reportLoss(a, EndReason.DEATH);
        MatchResult first = s.resolve().orElseThrow();
        s.reportLoss(b, EndReason.FORFEIT);
        s.reportCancel(EndReason.ERROR);
        assertTrue(s.resolve().isEmpty());
        assertEquals(first, s.result().orElseThrow());
    }

    @Test
    void unrankedWinDoesNotChangeRating() {
        MatchState s = active(false);
        s.reportLoss(a, EndReason.DEATH);
        assertFalse(s.resolve().orElseThrow().changesRating());
    }

    @Test
    void leavingTrainingIsNotAnnouncedAsLoss() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        MatchResult left = new MatchResult(UUID.randomUUID(), a, b, Outcome.WIN, b, a, EndReason.FORFEIT, false);
        assertTrue(left.trainingLeft());
        assertFalse(new MatchResult(UUID.randomUUID(), a, b, Outcome.WIN, b, a, EndReason.FORFEIT, true).trainingLeft(),
                "в рейтинговом выход по-прежнему поражение");
        assertFalse(new MatchResult(UUID.randomUUID(), a, b, Outcome.WIN, b, a, EndReason.DEATH, false).trainingLeft(),
                "гибель в тренировке остаётся поражением");
    }

    @Test
    void phasesOnlyMoveForward() {
        MatchState s = new MatchState(UUID.randomUUID(), a, b, false);
        assertTrue(s.advanceTo(MatchPhase.COUNTDOWN));
        assertFalse(s.advanceTo(MatchPhase.PREPARING));
        assertFalse(s.advanceTo(MatchPhase.COUNTDOWN));
        assertTrue(s.advanceTo(MatchPhase.ACTIVE));
    }

    @Test
    void outsiderEventsAreIgnored() {
        MatchState s = active(true);
        s.reportLoss(UUID.randomUUID(), EndReason.DEATH);
        assertTrue(s.resolve().isEmpty());
    }

    @Test
    void noOutcomeMeansNoResult() {
        assertTrue(active(true).resolve().isEmpty());
    }
}
