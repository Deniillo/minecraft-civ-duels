package mine.deniilo.duels.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import mine.deniilo.duels.queue.QueueService.Key;
import mine.deniilo.duels.queue.QueueService.Pair;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class QueueServiceTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final Key t4 = new Key("t4_heavy", false);
    private final Key t4ranked = new Key("t4_heavy", true);
    private final Key t5 = new Key("t5_soul", false);
    private final QueueSettings settings = new QueueSettings(100, 50, 5, 1000);

    private QueueService queue() {
        return new QueueService(settings);
    }

    @Test
    void casualMatchesInJoinOrderIgnoringRating() {
        QueueService q = queue();
        q.join(a, t4, 0, 0);
        q.join(b, t4, 5000, 0);
        List<Pair> pairs = q.match(0);
        assertEquals(1, pairs.size());
        assertEquals(a, pairs.get(0).first());
        assertEquals(b, pairs.get(0).second());
        assertTrue(q.keyOf(a).isEmpty());
        assertTrue(q.keyOf(b).isEmpty());
    }

    @Test
    void queuesAreSeparateByKitAndMode() {
        QueueService q = queue();
        q.join(a, t4, 1000, 0);
        q.join(b, t5, 1000, 0);
        q.join(c, t4ranked, 1000, 0);
        assertTrue(q.match(0).isEmpty());
    }

    @Test
    void rankedRespectsWindowAndWidensWithWaiting() {
        QueueService q = queue();
        q.join(a, t4ranked, 1000, 0);
        q.join(b, t4ranked, 1300, 0);
        assertTrue(q.match(0).isEmpty(), "разница 300 больше окна 100");
        assertTrue(q.match(4_000).isEmpty(), "шаг ещё не прошёл");
        assertTrue(q.match(5_000).isEmpty(), "окно 150");
        assertTrue(q.match(19_000).isEmpty(), "окно 250");
        assertEquals(1, q.match(20_000).size(), "окно 300 после четырёх шагов");
    }

    @Test
    void rankedPicksClosestRating() {
        QueueService q = queue();
        q.join(a, t4ranked, 1000, 0);
        q.join(b, t4ranked, 1090, 0);
        q.join(c, t4ranked, 1030, 0);
        List<Pair> pairs = q.match(0);
        assertEquals(1, pairs.size());
        assertEquals(a, pairs.get(0).first());
        assertEquals(c, pairs.get(0).second());
        assertEquals(t4ranked, q.keyOf(b).orElseThrow());
    }

    @Test
    void playerIsInOnlyOneQueue() {
        QueueService q = queue();
        q.join(a, t4, 1000, 0);
        q.join(a, t5, 1000, 0);
        assertEquals(0, q.size(t4));
        assertEquals(1, q.size(t5));
        assertEquals(t5, q.keyOf(a).orElseThrow());
    }

    @Test
    void sameQueueTwiceNeverMatchesWithSelf() {
        QueueService q = queue();
        q.join(a, t4, 1000, 0);
        q.join(a, t4, 1000, 0);
        assertEquals(1, q.size(t4));
        assertTrue(q.match(0).isEmpty());
    }

    @Test
    void leaveRemovesPlayer() {
        QueueService q = queue();
        q.join(a, t4, 1000, 0);
        assertTrue(q.leave(a));
        assertFalse(q.leave(a));
        q.join(b, t4, 1000, 0);
        assertTrue(q.match(0).isEmpty());
    }

    @Test
    void settingsValidationAndWindow() {
        assertEquals(100, settings.windowAfter(0));
        assertEquals(150, settings.windowAfter(5_000));
        assertEquals(1000, settings.windowAfter(10_000_000));
        YamlConfiguration y = new YamlConfiguration();
        y.set("queue.step-seconds", 0);
        assertThrows(IllegalArgumentException.class, () -> QueueSettings.from(y));
        assertEquals(100, QueueSettings.from(new YamlConfiguration()).baseWindow());
    }
}
