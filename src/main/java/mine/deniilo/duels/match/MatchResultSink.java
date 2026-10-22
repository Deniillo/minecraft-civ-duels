package mine.deniilo.duels.match;

public interface MatchResultSink {

    void onFinished(MatchResult result, MatchInfo info);

    MatchResultSink NOOP = (result, info) -> { };
}
