package mine.deniilo.duels.combat;

public record DamageBreakdown(
        double attack,
        double piercing,
        double defense,
        double critCoefficient,
        double beforeSoul,
        boolean soulHalved,
        double finalDamage) {
}
