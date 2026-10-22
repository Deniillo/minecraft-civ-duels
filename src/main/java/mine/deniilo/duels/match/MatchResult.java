package mine.deniilo.duels.match;

import java.util.UUID;

public record MatchResult(
        UUID matchId,
        UUID playerA,
        UUID playerB,
        Outcome outcome,
        UUID winner,
        UUID loser,
        EndReason reason,
        boolean ranked) {

    public enum Outcome {
        WIN,
        DRAW,
        CANCELLED
    }

    public boolean changesRating() {
        return ranked && outcome == Outcome.WIN;
    }

    public boolean trainingLeft() {
        return !ranked && outcome == Outcome.WIN && reason == EndReason.FORFEIT;
    }
}
