package mine.deniilo.duels.rating;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public final class RatingRepository implements AutoCloseable {

    public static final int SCHEMA_VERSION = 2;

    public record GameRecord(UUID matchId, String kitId, boolean ranked, UUID playerA, String nameA, UUID playerB,
                             String nameB, String outcome, UUID winner, UUID loser, String reason,
                             long durationMillis, long finishedAtMillis) {
    }

    public record Applied(boolean duplicate, Integer aBefore, Integer aAfter, Integer bBefore, Integer bAfter) {
    }

    public record Stat(int rating, int wins, int losses, int draws) {
    }

    public record TopEntry(String name, int rating, int wins, int losses) {
    }

    private final Connection db;
    private final EloSettings elo;

    public RatingRepository(Path file, EloSettings elo) throws SQLException {
        this.elo = elo;
        this.db = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement st = db.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        migrate();
    }

    private void migrate() throws SQLException {
        int version;
        try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            version = rs.next() ? rs.getInt(1) : 0;
        }
        if (version > SCHEMA_VERSION) {
            throw new SQLException("База создана более новой версией плагина (схема " + version + ")");
        }
        if (version == SCHEMA_VERSION) {
            return;
        }
        db.setAutoCommit(false);
        try (Statement st = db.createStatement()) {
            if (version < 1) {
                st.execute("CREATE TABLE players (uuid TEXT PRIMARY KEY, name TEXT NOT NULL)");
                st.execute("CREATE TABLE matches (match_id TEXT PRIMARY KEY, kit_id TEXT NOT NULL, ranked INTEGER NOT NULL,"
                        + " player_a TEXT NOT NULL, player_b TEXT NOT NULL, outcome TEXT NOT NULL, winner TEXT, loser TEXT,"
                        + " reason TEXT NOT NULL, duration_ms INTEGER NOT NULL, finished_at INTEGER NOT NULL,"
                        + " rules_version INTEGER NOT NULL, a_before INTEGER, a_after INTEGER, b_before INTEGER, b_after INTEGER)");
                st.execute("CREATE TABLE ratings (uuid TEXT PRIMARY KEY, rating INTEGER NOT NULL,"
                        + " wins INTEGER NOT NULL DEFAULT 0, losses INTEGER NOT NULL DEFAULT 0,"
                        + " draws INTEGER NOT NULL DEFAULT 0)");
            } else {
                st.execute("CREATE TABLE ratings_v2 (uuid TEXT PRIMARY KEY, rating INTEGER NOT NULL,"
                        + " wins INTEGER NOT NULL DEFAULT 0, losses INTEGER NOT NULL DEFAULT 0,"
                        + " draws INTEGER NOT NULL DEFAULT 0)");
                st.execute("INSERT INTO ratings_v2 (uuid, rating, wins, losses, draws)"
                        + " SELECT uuid, MAX(rating), SUM(wins), SUM(losses), SUM(draws) FROM ratings GROUP BY uuid");
                st.execute("DROP TABLE ratings");
                st.execute("ALTER TABLE ratings_v2 RENAME TO ratings");
                st.execute("DROP INDEX IF EXISTS idx_ratings_kit");
            }
            st.execute("CREATE INDEX IF NOT EXISTS idx_ratings_rating ON ratings (rating DESC)");
            st.execute("PRAGMA user_version = " + SCHEMA_VERSION);
            db.commit();
        } catch (SQLException e) {
            db.rollback();
            throw e;
        } finally {
            db.setAutoCommit(true);
        }
    }

    public int rating(UUID player) throws SQLException {
        return readRating(player).orElse(elo.startRating());
    }

    private Optional<Integer> readRating(UUID player) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement("SELECT rating FROM ratings WHERE uuid = ?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        }
    }

    public Applied record(GameRecord g) throws SQLException {
        db.setAutoCommit(false);
        try {
            Applied result = recordInTransaction(g);
            db.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            db.rollback();
            throw e;
        } finally {
            db.setAutoCommit(true);
        }
    }

    private Applied recordInTransaction(GameRecord g) throws SQLException {
        boolean rated = g.ranked() && !g.outcome().equals("CANCELLED");
        Integer aBefore = null;
        Integer bBefore = null;
        Integer aAfter = null;
        Integer bAfter = null;
        if (rated) {
            aBefore = rating(g.playerA());
            bBefore = rating(g.playerB());
            aAfter = aBefore;
            bAfter = bBefore;
            if (g.outcome().equals("WIN")) {
                boolean aWon = g.winner().equals(g.playerA());
                EloCalculator.Change c = aWon
                        ? EloCalculator.winnerLoser(aBefore, bBefore, elo)
                        : EloCalculator.winnerLoser(bBefore, aBefore, elo);
                aAfter = aWon ? c.winnerNew() : c.loserNew();
                bAfter = aWon ? c.loserNew() : c.winnerNew();
            }
        }
        int inserted;
        try (PreparedStatement ps = db.prepareStatement("INSERT OR IGNORE INTO matches (match_id, kit_id, ranked, player_a,"
                + " player_b, outcome, winner, loser, reason, duration_ms, finished_at, rules_version,"
                + " a_before, a_after, b_before, b_after) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, g.matchId().toString());
            ps.setString(2, g.kitId());
            ps.setInt(3, g.ranked() ? 1 : 0);
            ps.setString(4, g.playerA().toString());
            ps.setString(5, g.playerB().toString());
            ps.setString(6, g.outcome());
            ps.setString(7, g.winner() == null ? null : g.winner().toString());
            ps.setString(8, g.loser() == null ? null : g.loser().toString());
            ps.setString(9, g.reason());
            ps.setLong(10, g.durationMillis());
            ps.setLong(11, g.finishedAtMillis());
            ps.setInt(12, elo.rulesVersion());
            setInt(ps, 13, aBefore);
            setInt(ps, 14, aAfter);
            setInt(ps, 15, bBefore);
            setInt(ps, 16, bAfter);
            inserted = ps.executeUpdate();
        }
        if (inserted == 0) {
            return existing(g.matchId());
        }
        upsertPlayer(g.playerA(), g.nameA());
        upsertPlayer(g.playerB(), g.nameB());
        if (rated) {
            boolean win = g.outcome().equals("WIN");
            boolean draw = g.outcome().equals("DRAW");
            writeRating(g.playerA(), aAfter, win && g.winner().equals(g.playerA()),
                    win && g.loser().equals(g.playerA()), draw);
            writeRating(g.playerB(), bAfter, win && g.winner().equals(g.playerB()),
                    win && g.loser().equals(g.playerB()), draw);
        }
        return new Applied(false, aBefore, aAfter, bBefore, bAfter);
    }

    private Applied existing(UUID matchId) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT a_before, a_after, b_before, b_after FROM matches WHERE match_id = ?")) {
            ps.setString(1, matchId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new Applied(true, getInt(rs, 1), getInt(rs, 2), getInt(rs, 3), getInt(rs, 4));
            }
        }
    }

    private void upsertPlayer(UUID id, String name) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement("INSERT INTO players (uuid, name) VALUES (?, ?)"
                + " ON CONFLICT(uuid) DO UPDATE SET name = excluded.name")) {
            ps.setString(1, id.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    private void writeRating(UUID id, int rating, boolean win, boolean loss, boolean draw) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement("INSERT INTO ratings (uuid, rating, wins, losses, draws)"
                + " VALUES (?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET rating = excluded.rating,"
                + " wins = wins + excluded.wins, losses = losses + excluded.losses, draws = draws + excluded.draws")) {
            ps.setString(1, id.toString());
            ps.setInt(2, rating);
            ps.setInt(3, win ? 1 : 0);
            ps.setInt(4, loss ? 1 : 0);
            ps.setInt(5, draw ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public Optional<Stat> stats(UUID player) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT rating, wins, losses, draws FROM ratings WHERE uuid = ?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new Stat(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)))
                        : Optional.empty();
            }
        }
    }

    public List<TopEntry> top(int limit) throws SQLException {
        List<TopEntry> out = new ArrayList<>();
        try (PreparedStatement ps = db.prepareStatement("SELECT p.name, r.rating, r.wins, r.losses FROM ratings r"
                + " JOIN players p ON p.uuid = r.uuid ORDER BY r.rating DESC, r.wins DESC, p.name LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TopEntry(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)));
                }
            }
        }
        return out;
    }

    public OptionalInt ratedRating(UUID player) throws SQLException {
        Optional<Integer> r = readRating(player);
        return r.isPresent() ? OptionalInt.of(r.get()) : OptionalInt.empty();
    }

    public Optional<UUID> findByName(String name) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement("SELECT uuid FROM players WHERE lower(name) = lower(?)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
            }
        }
    }

    public int matchCount() throws SQLException {
        try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM matches")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public void setRating(UUID player, String name, int value) throws SQLException {
        db.setAutoCommit(false);
        try {
            upsertPlayer(player, name);
            try (PreparedStatement ps = db.prepareStatement("INSERT INTO ratings (uuid, rating) VALUES (?,?)"
                    + " ON CONFLICT(uuid) DO UPDATE SET rating = excluded.rating")) {
                ps.setString(1, player.toString());
                ps.setInt(2, value);
                ps.executeUpdate();
            }
            db.commit();
        } catch (SQLException | RuntimeException e) {
            db.rollback();
            throw e;
        } finally {
            db.setAutoCommit(true);
        }
    }

    private static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static Integer getInt(ResultSet rs, int index) throws SQLException {
        int v = rs.getInt(index);
        return rs.wasNull() ? null : v;
    }

    @Override
    public void close() {
        try {
            db.close();
        } catch (SQLException ignored) {
        }
    }
}
