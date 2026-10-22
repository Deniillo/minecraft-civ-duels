package mine.deniilo.duels.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArmorSetCalculatorTest {

    private static void assertSet(ArmorClass c, int tier, double d, double maxHp, double speed) {
        ArmorSetStats s = ArmorSetCalculator.compute(TestData.fullSet(c, tier, 0));
        String name = c + " T" + tier;
        assertEquals(d, s.defense(), 1e-9, name + " D");
        assertEquals(maxHp, s.maxHp(), 1e-9, name + " maxHP");
        assertEquals(speed, s.speed(), 1e-9, name + " speed");
    }

    @Test
    void lightSets() {
        assertSet(ArmorClass.LIGHT, 1, 8, 20, 0.04);
        assertSet(ArmorClass.LIGHT, 2, 12, 22, 0.08);
        assertSet(ArmorClass.LIGHT, 3, 16, 24, 0.14);
        assertSet(ArmorClass.LIGHT, 4, 20, 26, 0.18);
        assertSet(ArmorClass.LIGHT, 5, 30, 20, 0.30);
    }

    @Test
    void heavySets() {
        assertSet(ArmorClass.HEAVY, 1, 11, 24, -0.04);
        assertSet(ArmorClass.HEAVY, 2, 15, 28, -0.08);
        assertSet(ArmorClass.HEAVY, 3, 19, 30, -0.10);
        assertSet(ArmorClass.HEAVY, 4, 23, 32, -0.14);
    }

    @Test
    void soulSets() {
        assertSet(ArmorClass.SOUL, 1, 11, 26, 0.04);
        assertSet(ArmorClass.SOUL, 2, 15, 30, 0.08);
        assertSet(ArmorClass.SOUL, 3, 19, 32, 0.14);
        assertSet(ArmorClass.SOUL, 4, 23, 36, 0.18);
        assertSet(ArmorClass.SOUL, 5, 23, 38, 0.15);
    }

    @Test
    void soulFlags() {
        ArmorSetStats t5 = ArmorSetCalculator.compute(TestData.fullSet(ArmorClass.SOUL, 5, 0));
        assertTrue(t5.soulSet());
        assertTrue(t5.soulT5Set());

        ArmorSetStats t4 = ArmorSetCalculator.compute(TestData.fullSet(ArmorClass.SOUL, 4, 0));
        assertTrue(t4.soulSet(), "SOUL любых тиров даёт эффекты");
        assertFalse(t4.soulT5Set());
    }

    @Test
    void mixedSoulTiersStillGiveEffectsButNotT5() {
        List<ArmorPiece> pieces = new ArrayList<>();
        pieces.addAll(TestData.fullSet(ArmorClass.SOUL, 5, 0).subList(0, 3));
        pieces.add(TestData.fullSet(ArmorClass.SOUL, 1, 0).get(3));
        ArmorSetStats s = ArmorSetCalculator.compute(pieces);
        assertTrue(s.soulSet());
        assertFalse(s.soulT5Set());
    }

    @Test
    void lightT5IsNotSoul() {
        ArmorSetStats s = ArmorSetCalculator.compute(TestData.fullSet(ArmorClass.LIGHT, 5, 0));
        assertFalse(s.soulSet());
        assertFalse(s.soulT5Set());
        assertEquals(4, s.lightCount());
    }

    @Test
    void threeSoulPlusOneOtherGivesNoSet() {
        List<ArmorPiece> pieces = new ArrayList<>(TestData.fullSet(ArmorClass.SOUL, 5, 0).subList(0, 3));
        pieces.add(TestData.fullSet(ArmorClass.HEAVY, 4, 0).get(3));
        ArmorSetStats s = ArmorSetCalculator.compute(pieces);
        assertFalse(s.soulSet());
        assertFalse(s.soulT5Set());
        assertEquals(3, s.soulCount());
        assertEquals(1, s.heavyCount());
    }

    @Test
    void partialSetNeverGivesSetBonuses() {
        List<ArmorPiece> pieces = TestData.fullSet(ArmorClass.SOUL, 5, 0).subList(0, 3);
        ArmorSetStats s = ArmorSetCalculator.compute(pieces);
        assertFalse(s.soulSet());
        assertEquals(20 + 3 * 4.5, s.maxHp(), 1e-9);
    }

    @Test
    void emptyIsBasePlayer() {
        ArmorSetStats s = ArmorSetCalculator.compute(List.of());
        assertEquals(0, s.defense(), 1e-9);
        assertEquals(20, s.maxHp(), 1e-9);
        assertEquals(0, s.speed(), 1e-9);
    }

    @Test
    void mixedClassesSumPerPiece() {
        List<ArmorPiece> pieces = new ArrayList<>();
        pieces.add(TestData.fullSet(ArmorClass.HEAVY, 4, 0).get(0));
        pieces.add(TestData.fullSet(ArmorClass.LIGHT, 5, 0).get(1));
        ArmorSetStats s = ArmorSetCalculator.compute(pieces);
        assertEquals(13.25, s.defense(), 1e-9);
        assertEquals(23, s.maxHp(), 1e-9);
        assertEquals(0.04, s.speed(), 1e-9);
    }

    @Test
    void protectionAddsQuarterPerLevelPerPiece() {
        double[] expectedPerPiece = {0, 0.25, 0.5, 0.75, 1.0, 1.25};
        for (int level = 0; level <= 5; level++) {
            ArmorSetStats s = ArmorSetCalculator.compute(TestData.fullSet(ArmorClass.HEAVY, 4, level));
            assertEquals(23 + 4 * expectedPerPiece[level], s.defense(), 1e-9, "Protection " + level);
        }
        assertEquals(28, ArmorSetCalculator.compute(TestData.fullSet(ArmorClass.HEAVY, 4, 5)).defense(), 1e-9);
    }
}
