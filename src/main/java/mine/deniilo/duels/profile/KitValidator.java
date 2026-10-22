package mine.deniilo.duels.profile;

import java.util.ArrayList;
import java.util.List;

public final class KitValidator {

    private KitValidator() {
    }

    public static List<String> validate(Kit kit, ItemCatalog items) {
        List<String> errors = new ArrayList<>();
        if (kit.attackLevel() < 0 || kit.attackLevel() > Kit.MAX_ATTACK_LEVEL) {
            errors.add("attack-level вне диапазона 0.." + Kit.MAX_ATTACK_LEVEL + ": " + kit.attackLevel());
        }
        if (kit.protectionLevel() < 0 || kit.protectionLevel() > Kit.MAX_PROTECTION_LEVEL) {
            errors.add("protection-level вне диапазона 0.." + Kit.MAX_PROTECTION_LEVEL + ": " + kit.protectionLevel());
        }
        if (kit.piercingLevel() < 0 || kit.piercingLevel() > Kit.MAX_PIERCING_LEVEL) {
            errors.add("piercing-level вне диапазона 0.." + Kit.MAX_PIERCING_LEVEL + ": " + kit.piercingLevel());
        }
        if (kit.weaponIds().isEmpty()) {
            errors.add("в наборе нет оружия");
        }

        List<ArmorPiece> pieces = new ArrayList<>();
        for (ArmorSlot slot : ArmorSlot.values()) {
            var profile = items.armor(kit.armorClass(), kit.armorTier(), slot);
            if (profile.isEmpty()) {
                errors.add("нет брони " + ArmorProfile.idOf(kit.armorClass(), kit.armorTier(), slot) + " в items.yml");
            } else {
                pieces.add(new ArmorPiece(profile.get(), kit.protectionLevel()));
            }
        }
        if (pieces.size() != ArmorSlot.values().length) {
            return errors;
        }

        ArmorSetStats stats = ArmorSetCalculator.compute(pieces);
        for (String id : kit.weaponIds()) {
            var weapon = items.weapon(id);
            if (weapon.isEmpty()) {
                errors.add("неизвестное оружие: " + id);
                continue;
            }
            WeaponRules.violation(stats, weapon.get()).ifPresent(reason -> errors.add(id + ": " + reason));
        }
        return errors;
    }
}
