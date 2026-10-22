package mine.deniilo.duels.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArenaPoolTest {

    @Test
    void arenaIsNotGivenToTwoMatches() {
        ArenaPool pool = new ArenaPool(1);
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        assertTrue(pool.tryAcquire(m1));
        assertTrue(pool.tryAcquire(m1), "повтор того же матча безвреден");
        assertFalse(pool.tryAcquire(m2));
        pool.release(m1);
        assertTrue(pool.tryAcquire(m2));
        assertEquals(0, pool.free());
    }

    @Test
    void needsAtLeastOne() {
        assertThrows(IllegalArgumentException.class, () -> new ArenaPool(0));
    }
}
