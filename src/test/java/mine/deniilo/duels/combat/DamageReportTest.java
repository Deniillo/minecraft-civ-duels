package mine.deniilo.duels.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import mine.deniilo.duels.profile.WeaponKind;
import org.junit.jupiter.api.Test;

class DamageReportTest {

    @Test
    void describesSoulHalvedThrow() {
        AttackInput in = new AttackInput(AttackType.THROWN_TRIDENT, WeaponKind.TRIDENT, 28, 0, 0, 1, false);
        String text = DamageReport.describe(in, DamageCalculator.compute(in, 23, true));
        assertEquals("бросок B=28 E=0 | A=33 P=0 D=23 C=1 | H0=10 SOUL/2 => 5 HP", text);
    }

    @Test
    void describesMeleeWithCooldown() {
        AttackInput in = new AttackInput(AttackType.MELEE, WeaponKind.SWORD, 27, 0, 3, 0.5, false);
        String text = DamageReport.describe(in, DamageCalculator.compute(in, 8, false));
        assertEquals("ближний s=0.5 B=27 E=0 | A=6.75 P=3 D=8 C=1 | H0=1.75 => 1.75 HP", text);
    }
}
