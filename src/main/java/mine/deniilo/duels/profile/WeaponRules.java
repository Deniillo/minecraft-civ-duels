package mine.deniilo.duels.profile;

import java.util.Optional;

public final class WeaponRules {

    private WeaponRules() {
    }

    public static Optional<String> violation(ArmorSetStats armor, WeaponProfile weapon) {
        if (weapon.tier() == 5 && !armor.soulT5Set()) {
            return Optional.of("Оружие T5 требует полного комплекта SOUL T5");
        }
        if (weapon.kind() == WeaponKind.AXE && armor.lightCount() >= 4) {
            return Optional.of("Секиру нельзя использовать в полном комплекте LIGHT");
        }
        if (weapon.kind() == WeaponKind.BOW && armor.heavyCount() >= 1) {
            return Optional.of("Лук нельзя использовать с бронёй HEAVY");
        }
        return Optional.empty();
    }

    public static boolean canThrowTrident(ArmorSetStats armor) {
        return armor.heavyCount() < 4;
    }
}
