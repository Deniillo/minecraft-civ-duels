package mine.deniilo.duels.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import mine.deniilo.duels.rating.RatingRepository.Applied;
import mine.deniilo.duels.rating.RatingRepository.GameRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RatingRepositoryTest {

    @TempDir
    Path dir;

    private final EloSettings elo = new EloSettings(1000, 32, 0, false, 1);
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private RatingRepository repo;

    @BeforeEach
    void open() throws SQLException {
        repo = new RatingRepository(dir.resolve("duels.db"), elo);
    }

    private GameRecord win(UUID id, UUID winner, UUID loser, boolean ranked, String kit) {
        return new GameRecord(id, kit, ranked, a, "Alpha", b, "Beta", "WIN", winner, loser, "DEATH", 12_000, 1L);
    }

    @Test
    void newPlayerHasStartRating() throws SQLException {
        assertEquals(1000, repo.rating(a));
        assertTrue(repo.ratedRating(a).isEmpty());
    }

    @Test
    void rankedWinMovesRatingsAndCountsStats() throws SQLException {
        Applied r = repo.record(win(UUID.randomUUID(), a, b, true, "t4_heavy"));
        assertFalse(r.duplicate());
        assertEquals(1000, r.aBefore());
        assertEquals(1016, r.aAfter());
        assertEquals(984, r.bAfter());
        assertEquals(1016, repo.rating(a));
        RatingRepository.Stat sa = repo.stats(a).orElseThrow();
        assertEquals(1, sa.wins());
        assertEquals(0, sa.losses());
        assertEquals(1, repo.stats(b).orElseThrow().losses());
    }

    @Test
    void ratingIsSharedAcrossKits() throws SQLException {
        repo.record(win(UUID.randomUUID(), a, b, true, "t4_heavy"));
        assertEquals(1016, repo.rating(a));
        Applied second = repo.record(win(UUID.randomUUID(), a, b, true, "t5_soul"));
        assertEquals(1016, second.aBefore(), "второй матч другого набора продолжает тот же рейтинг");
        assertTrue(second.aAfter() > 1016);
        assertEquals(2, repo.stats(a).orElseThrow().wins());
    }

    @Test
    void trainingDoesNotTouchRatings() throws SQLException {
        Applied r = repo.record(win(UUID.randomUUID(), a, b, false, "t4_heavy"));
        assertEquals(null, r.aBefore());
        assertEquals(1000, repo.rating(a));
        assertTrue(repo.stats(a).isEmpty());
        assertEquals(1, repo.matchCount());
    }

    @Test
    void replayOfSameMatchIsIdempotent() throws SQLException {
        UUID id = UUID.randomUUID();
        Applied first = repo.record(win(id, a, b, true, "t4_heavy"));
        Applied second = repo.record(win(id, a, b, true, "t4_heavy"));
        assertTrue(second.duplicate());
        assertEquals(first.aAfter(), second.aAfter());
        assertEquals(1016, repo.rating(a));
        assertEquals(1, repo.matchCount());
        assertEquals(1, repo.stats(a).orElseThrow().wins());
    }

    @Test
    void rankedDrawKeepsRatingAndCountsDraw() throws SQLException {
        repo.record(new GameRecord(UUID.randomUUID(), "t4_heavy", true, a, "Alpha", b, "Beta", "DRAW", null, null,
                "TIMEOUT", 600_000, 1L));
        assertEquals(1000, repo.rating(a));
        assertEquals(1, repo.stats(a).orElseThrow().draws());
    }

    @Test
    void cancelledRankedMatchIsLoggedWithoutRating() throws SQLException {
        repo.record(new GameRecord(UUID.randomUUID(), "t4_heavy", true, a, "Alpha", b, "Beta", "CANCELLED", null, null,
                "ADMIN", 0, 1L));
        assertEquals(1, repo.matchCount());
        assertTrue(repo.stats(a).isEmpty());
    }

    @Test
    void failedWriteRollsBackEverything() throws SQLException {
        GameRecord bad = new GameRecord(UUID.randomUUID(), "t4_heavy", true, a, "Alpha", b, null, "WIN", a, b,
                "DEATH", 1, 1L);
        assertThrows(SQLException.class, () -> repo.record(bad));
        assertEquals(0, repo.matchCount(), "запись матча откатывается вместе с рейтингами");
        assertEquals(1000, repo.rating(a));
        repo.record(win(UUID.randomUUID(), a, b, true, "t4_heavy"));
        assertEquals(1, repo.matchCount());
    }

    @Test
    void topSortsByRatingAndFindByName() throws SQLException {
        repo.record(win(UUID.randomUUID(), a, b, true, "t4_heavy"));
        List<RatingRepository.TopEntry> top = repo.top(10);
        assertEquals("Alpha", top.get(0).name());
        assertEquals("Beta", top.get(1).name());
        assertEquals(a, repo.findByName("alpha").orElseThrow());
        assertTrue(repo.findByName("nobody").isEmpty());
    }

    @Test
    void dataSurvivesReopenAndMigrationIsRepeatable() throws SQLException {
        repo.record(win(UUID.randomUUID(), a, b, true, "t4_heavy"));
        repo.close();
        repo = new RatingRepository(dir.resolve("duels.db"), elo);
        assertEquals(1016, repo.rating(a));
    }

    @Test
    void adminSetRating() throws SQLException {
        repo.setRating(a, "Alpha", 1500);
        assertEquals(1500, repo.rating(a));
        assertEquals(1500, repo.ratedRating(a).getAsInt());
    }

    @Test
    void migratesSchemaOneWithPerKitRatingsIntoOneRating() throws SQLException {
        repo.close();
        Path old = dir.resolve("old.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + old.toAbsolutePath());
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE players (uuid TEXT PRIMARY KEY, name TEXT NOT NULL)");
            st.execute("CREATE TABLE ratings (uuid TEXT NOT NULL, kit_id TEXT NOT NULL, rating INTEGER NOT NULL,"
                    + " wins INTEGER NOT NULL DEFAULT 0, losses INTEGER NOT NULL DEFAULT 0,"
                    + " draws INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (uuid, kit_id))");
            st.execute("CREATE TABLE matches (match_id TEXT PRIMARY KEY, kit_id TEXT NOT NULL, ranked INTEGER NOT NULL,"
                    + " player_a TEXT NOT NULL, player_b TEXT NOT NULL, outcome TEXT NOT NULL, winner TEXT, loser TEXT,"
                    + " reason TEXT NOT NULL, duration_ms INTEGER NOT NULL, finished_at INTEGER NOT NULL,"
                    + " rules_version INTEGER NOT NULL, a_before INTEGER, a_after INTEGER, b_before INTEGER, b_after INTEGER)");
            st.execute("INSERT INTO ratings VALUES ('" + a + "', 't4_heavy', 1100, 3, 1, 0)");
            st.execute("INSERT INTO ratings VALUES ('" + a + "', 't5_soul', 1250, 2, 0, 1)");
            st.execute("PRAGMA user_version = 1");
        }
        try (RatingRepository migrated = new RatingRepository(old, elo)) {
            RatingRepository.Stat s = migrated.stats(a).orElseThrow();
            assertEquals(1250, s.rating(), "берётся лучший рейтинг");
            assertEquals(5, s.wins());
            assertEquals(1, s.losses());
            assertEquals(1, s.draws());
        }
    }
}
