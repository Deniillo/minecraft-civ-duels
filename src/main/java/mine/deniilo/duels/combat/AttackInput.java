package mine.deniilo.duels.combat;

import mine.deniilo.duels.profile.WeaponKind;

public record AttackInput(
        AttackType type,
        WeaponKind kind,
        double base,
        int attackLevel,
        int piercingLevel,
        double strength,
        boolean crit) {

    public static AttackInput fist() {
        return new AttackInput(AttackType.FIST, null, 0, 0, 0, 1, false);
    }
}
