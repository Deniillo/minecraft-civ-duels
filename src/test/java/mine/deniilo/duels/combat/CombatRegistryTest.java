package mine.deniilo.duels.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatRegistryTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID s1 = UUID.randomUUID();
    private final UUID s2 = UUID.randomUUID();

    @Test
    void damageOnlyInsideSameActiveSession() {
        CombatRegistry r = new CombatRegistry();
        r.join(a, s1);
        r.join(b, s1);
        assertFalse(r.mayDamage(a, b), "до ACTIVE урон запрещён");
        r.setActive(s1, true);
        assertTrue(r.mayDamage(a, b));
    }

    @Test
    void noDamageAcrossSessionsOrFromOutsiders() {
        CombatRegistry r = new CombatRegistry();
        r.join(a, s1);
        r.join(b, s1);
        r.join(c, s2);
        r.setActive(s1, true);
        r.setActive(s2, true);
        assertFalse(r.mayDamage(a, c));
        assertFalse(r.mayDamage(UUID.randomUUID(), a), "зритель не бьёт");
        assertFalse(r.mayDamage(a, UUID.randomUUID()), "зрителя не бьют");
    }

    @Test
    void leaveAndFinishStopDamage() {
        CombatRegistry r = new CombatRegistry();
        r.join(a, s1);
        r.join(b, s1);
        r.setActive(s1, true);
        r.setActive(s1, false);
        assertFalse(r.mayDamage(a, b));
        r.setActive(s1, true);
        r.leave(b);
        assertFalse(r.mayDamage(a, b));
        assertEquals(1, r.members(s1).size());
    }

    @Test
    void adminTestSessionIsAlwaysActiveAndDummiesJoinIt() {
        CombatRegistry r = new CombatRegistry();
        r.join(a, CombatRegistry.ADMIN_TEST);
        r.addDummy(b);
        assertTrue(r.mayDamage(a, b));
        assertTrue(r.isDummy(b));
        r.setActive(CombatRegistry.ADMIN_TEST, false);
        assertTrue(r.mayDamage(a, b), "тестовая сессия не отключается");
        r.leave(b);
        assertFalse(r.isDummy(b));
    }
}
