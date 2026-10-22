package mine.deniilo.duels.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mine.deniilo.duels.profile.WeaponKind;
import org.junit.jupiter.api.Test;

class DamageCalculatorTest {

    private static final double EPS = 1e-9;

    private static double melee(WeaponKind k, double base, int e, int p, double s, boolean crit, double d, boolean soul) {
        return DamageCalculator.compute(new AttackInput(AttackType.MELEE, k, base, e, p, s, crit), d, soul).finalDamage();
    }

    private static double thrown(double base, int e, double d, boolean soul) {
        return DamageCalculator.compute(new AttackInput(AttackType.THROWN_TRIDENT, WeaponKind.TRIDENT, base, e, 0, 1, false), d, soul).finalDamage();
    }

    private static double bow(double base, int e, double f, double d, boolean soul) {
        return DamageCalculator.compute(new AttackInput(AttackType.BOW, WeaponKind.BOW, base, e, 0, f, false), d, soul).finalDamage();
    }

    @Test
    void ironSwordVsLightT1() {
        assertEquals(6, melee(WeaponKind.SWORD, 14, 0, 0, 1, false, 8, false), EPS);
        assertEquals(7.5, melee(WeaponKind.SWORD, 14, 0, 0, 1, true, 8, false), EPS);
        assertEquals(0, melee(WeaponKind.SWORD, 14, 0, 0, 0.5, false, 8, false), EPS);
    }

    @Test
    void swordT4VsHeavyT4() {
        assertEquals(4, melee(WeaponKind.SWORD, 27, 0, 0, 1, false, 23, false), EPS);
        assertEquals(5, melee(WeaponKind.SWORD, 27, 0, 0, 1, true, 23, false), EPS);
    }

    @Test
    void axeT4VsHeavyT4() {
        assertEquals(7, melee(WeaponKind.AXE, 30, 0, 0, 1, false, 23, false), EPS);
        assertEquals(10.5, melee(WeaponKind.AXE, 30, 0, 0, 1, true, 23, false), EPS);
    }

    @Test
    void tridentT4() {
        assertEquals(5, melee(WeaponKind.TRIDENT, 28, 0, 0, 1, false, 23, false), EPS);
        assertEquals(10, thrown(28, 0, 23, false), EPS);
        assertEquals(5, thrown(28, 0, 23, true), EPS);
    }

    @Test
    void bowT4VsSoulT5() {
        assertEquals(3.5, bow(30, 0, 1, 23, true), EPS);
    }

    @Test
    void swordT4Plus7CritVsSoulT5() {
        assertEquals(6.875, melee(WeaponKind.SWORD, 27, 7, 0, 1, true, 23, true), EPS);
    }

    @Test
    void tridentT4Plus7ThrowVsHeavyT4() {
        assertEquals(20.5, thrown(28, 7, 23, false), EPS);
    }

    @Test
    void protectionFiveOnEverything() {
        assertEquals(0, melee(WeaponKind.SWORD, 27, 0, 0, 1, false, 28, false), EPS);
        assertEquals(2.5, thrown(28, 0, 28, true), EPS, "бросок трезубца T4 (33) минус D 28 = 5, пополам 2,5");
    }

    @Test
    void piercingNotScaledByCooldown() {
        assertEquals(1.75, melee(WeaponKind.SWORD, 27, 0, 3, 0.5, false, 8, false), EPS);
    }

    @Test
    void bowHalfForceVsLight() {
        assertEquals(7, bow(30, 0, 0.5, 8, false), EPS);
    }

    @Test
    void soulThresholdIsStrict() {
        DamageBreakdown exactly = DamageCalculator.compute(
                new AttackInput(AttackType.CROSSBOW, WeaponKind.CROSSBOW, 38, 0, 0, 1, false), 34.5, true);
        assertEquals(3.5, exactly.beforeSoul(), EPS);
        assertFalse(exactly.soulHalved());
        assertEquals(3.5, exactly.finalDamage(), EPS);

        DamageBreakdown above = DamageCalculator.compute(
                new AttackInput(AttackType.CROSSBOW, WeaponKind.CROSSBOW, 38, 0, 0, 1, false), 34.4, true);
        assertTrue(above.soulHalved());
        assertEquals(1.8, above.finalDamage(), EPS);
    }

    @Test
    void soulHalvingNeedsFullSoulT5() {
        assertEquals(10, thrown(28, 0, 23, false), EPS);
        assertEquals(5, thrown(28, 0, 23, true), EPS);
    }

    @Test
    void minimumBaseIsNotMinimumDamage() {
        assertEquals(0, melee(WeaponKind.SWORD, 0, 0, 0, 1, false, 8, false), EPS);
        assertEquals(0.5, DamageCalculator.compute(
                new AttackInput(AttackType.BOW, WeaponKind.BOW, 0, 0, 0, 1, false), 0, false).attack(), EPS);
    }

    @Test
    void critOnlyForMeleeAndKindCoefficients() {
        assertEquals(1.0, DamageCalculator.compute(
                new AttackInput(AttackType.BOW, WeaponKind.BOW, 30, 0, 0, 1, true), 0, false).critCoefficient(), EPS);
        assertEquals(1.0, DamageCalculator.compute(
                new AttackInput(AttackType.THROWN_TRIDENT, WeaponKind.TRIDENT, 28, 0, 0, 1, true), 0, false).critCoefficient(), EPS);
        assertEquals(1.25, DamageCalculator.compute(
                new AttackInput(AttackType.MELEE, WeaponKind.TRIDENT, 28, 0, 0, 1, true), 0, false).critCoefficient(), EPS);
    }

    @Test
    void fistIgnoresWeaponFormula() {
        DamageBreakdown b = DamageCalculator.compute(AttackInput.fist(), 0, false);
        assertEquals(0.75, b.finalDamage(), EPS);
        assertEquals(0, DamageCalculator.compute(AttackInput.fist(), 2, false).finalDamage(), EPS);
    }

    @Test
    void piercingOnlyForMelee() {
        DamageBreakdown thrownWithP = DamageCalculator.compute(
                new AttackInput(AttackType.THROWN_TRIDENT, WeaponKind.TRIDENT, 28, 0, 3, 1, false), 23, false);
        assertEquals(0, thrownWithP.piercing(), EPS);
        assertEquals(10, thrownWithP.finalDamage(), EPS);
    }

    @Test
    void environmentDamageHalvedOnlyAboveThreshold() {
        assertEquals(5, DamageCalculator.environment(10, true), EPS);
        assertEquals(3.5, DamageCalculator.environment(3.5, true), EPS);
        assertEquals(10, DamageCalculator.environment(10, false), EPS);
    }
}
