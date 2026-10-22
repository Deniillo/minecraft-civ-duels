package mine.deniilo.duels.rating;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;
import mine.deniilo.duels.match.MatchInfo;
import mine.deniilo.duels.match.MatchResult;
import mine.deniilo.duels.match.MatchResultSink;
import mine.deniilo.duels.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class RatingService implements MatchResultSink {

    private final RatingRepository repo;
    private final Messages messages;
    private final Logger log;

    public RatingService(RatingRepository repo, Messages messages, Logger log) {
        this.repo = repo;
        this.messages = messages;
        this.log = log;
    }

    @Override
    public void onFinished(MatchResult result, MatchInfo info) {
        if (info.vsBot()) {
            return;
        }
        RatingRepository.Applied applied;
        try {
            applied = repo.record(new RatingRepository.GameRecord(result.matchId(), info.kitId(), result.ranked(),
                    result.playerA(), name(result.playerA()), result.playerB(), name(result.playerB()),
                    result.outcome().name(), result.winner(), result.loser(), result.reason().name(),
                    info.durationMillis(), System.currentTimeMillis()));
        } catch (SQLException | RuntimeException e) {
            log.severe("Не удалось сохранить матч " + result.matchId() + ": " + e.getMessage());
            for (UUID id : new UUID[] {result.playerA(), result.playerB()}) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    messages.send(p, "stats-save-failed");
                }
            }
            return;
        }
        if (applied.aBefore() == null) {
            return;
        }
        notifyPlayer(result.playerA(), applied.aBefore(), applied.aAfter());
        notifyPlayer(result.playerB(), applied.bBefore(), applied.bAfter());
    }

    private void notifyPlayer(UUID id, int before, int after) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) {
            return;
        }
        int delta = after - before;
        messages.send(p, "rating-change", "before", String.valueOf(before), "after", String.valueOf(after),
                "delta", (delta >= 0 ? "+" : "") + delta);
    }

    private static String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString() : n;
    }
}
