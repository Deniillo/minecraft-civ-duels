package mine.deniilo.duels.combat;

import mine.deniilo.duels.profile.WeaponKind;

public final class DamageCalculator {

    public static final double MIN_BASE = 0.5;
    public static final double FIST_ATTACK = 0.75;
    public static final double THROW_BONUS = 5.0;
    public static final double SOUL_THRESHOLD = 3.5;
    public static final double CRIT_SWORD_TRIDENT = 1.25;
    public static final double CRIT_AXE = 1.5;

    private DamageCalculator() {
    }

    public static DamageBreakdown compute(AttackInput in, double defense, boolean soulT5Set) {
        double a = attack(in);
        double p = in.type() == AttackType.MELEE ? in.piercingLevel() : 0.0;
        double c = critCoefficient(in);
        double h0 = Math.max(0.0, c * (a + p - defense));
        boolean halve = soulT5Set && h0 > SOUL_THRESHOLD;
        double h = halve ? h0 / 2.0 : h0;
        return new DamageBreakdown(a, p, defense, c, h0, halve, h);
    }

    public static double environment(double rawDamage, boolean soulT5Set) {
        return soulT5Set && rawDamage > SOUL_THRESHOLD ? rawDamage / 2.0 : rawDamage;
    }

    static double attack(AttackInput in) {
        double bePlus = in.base() + in.attackLevel();
        return switch (in.type()) {
            case MELEE -> Math.max(MIN_BASE, bePlus) * in.strength() * in.strength();
            case THROWN_TRIDENT -> Math.max(MIN_BASE, bePlus) + THROW_BONUS + in.attackLevel() / 2.0;
            case BOW -> Math.max(MIN_BASE, bePlus * in.strength());
            case CROSSBOW -> Math.max(MIN_BASE, bePlus);
            case FIST -> FIST_ATTACK;
        };
    }

    static double critCoefficient(AttackInput in) {
        if (!in.crit() || in.type() != AttackType.MELEE || in.kind() == null) {
            return 1.0;
        }
        return in.kind() == WeaponKind.AXE ? CRIT_AXE : CRIT_SWORD_TRIDENT;
    }
}
