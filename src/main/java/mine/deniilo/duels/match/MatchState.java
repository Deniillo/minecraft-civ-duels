package mine.deniilo.duels.match;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MatchState {

    private final UUID matchId;
    private final UUID a;
    private final UUID b;
    private final boolean ranked;

    private MatchPhase phase = MatchPhase.PREPARING;
    private final Map<UUID, EndReason> lost = new LinkedHashMap<>();
    private boolean leftBeforeStart;
    private boolean timeout;
    private EndReason cancel;
    private MatchResult result;

    public boolean ranked() {
        return ranked;
    }

    public MatchState(UUID matchId, UUID a, UUID b, boolean ranked) {
        if (a.equals(b)) {
            throw new IllegalArgumentException("Участники матча должны быть разными");
        }
        this.matchId = matchId;
        this.a = a;
        this.b = b;
        this.ranked = ranked;
    }

    public UUID matchId() {
        return matchId;
    }

    public MatchPhase phase() {
        return phase;
    }

    public boolean isParticipant(UUID id) {
        return a.equals(id) || b.equals(id);
    }

    public Optional<MatchResult> result() {
        return Optional.ofNullable(result);
    }

    public boolean advanceTo(MatchPhase next) {
        if (next.ordinal() <= phase.ordinal()) {
            return false;
        }
        phase = next;
        return true;
    }

    public void reportLoss(UUID participant, EndReason reason) {
        if (result != null || !isParticipant(participant)) {
            return;
        }
        if (phase != MatchPhase.ACTIVE) {
            if (phase.ordinal() < MatchPhase.ACTIVE.ordinal()) {
                leftBeforeStart = true;
            }
            return;
        }
        lost.putIfAbsent(participant, reason);
    }

    public void reportTimeout() {
        if (result == null && phase == MatchPhase.ACTIVE) {
            timeout = true;
        }
    }

    public void reportCancel(EndReason reason) {
        if (result == null && cancel == null) {
            cancel = reason;
        }
    }

    public Optional<MatchResult> resolve() {
        if (result != null) {
            return Optional.empty();
        }
        MatchResult r = null;
        if (cancel != null) {
            r = new MatchResult(matchId, a, b, MatchResult.Outcome.CANCELLED, null, null, cancel, ranked);
        } else if (leftBeforeStart) {
            r = new MatchResult(matchId, a, b, MatchResult.Outcome.CANCELLED, null, null, EndReason.LEFT_BEFORE_START, ranked);
        } else if (lost.size() >= 2) {
            r = new MatchResult(matchId, a, b, MatchResult.Outcome.DRAW, null, null, EndReason.SIMULTANEOUS, ranked);
        } else if (lost.size() == 1) {
            Map.Entry<UUID, EndReason> first = lost.entrySet().iterator().next();
            UUID loser = first.getKey();
            UUID winner = loser.equals(a) ? b : a;
            r = new MatchResult(matchId, a, b, MatchResult.Outcome.WIN, winner, loser, first.getValue(), ranked);
        } else if (timeout) {
            r = new MatchResult(matchId, a, b, MatchResult.Outcome.DRAW, null, null, EndReason.TIMEOUT, ranked);
        }
        if (r == null) {
            return Optional.empty();
        }
        result = r;
        phase = MatchPhase.FINISHING;
        return Optional.of(r);
    }
}
