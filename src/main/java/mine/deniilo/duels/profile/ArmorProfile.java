package mine.deniilo.duels.profile;

import java.util.Locale;

public record ArmorProfile(
        String id,
        ArmorClass armorClass,
        int tier,
        ArmorSlot slot,
        String materialBase,
        double defense,
        double hp,
        double speed) {

    public String materialName() {
        return materialBase + "_" + slot.name();
    }

    public static String idOf(ArmorClass armorClass, int tier, ArmorSlot slot) {
        return armorClass.name().toLowerCase(Locale.ROOT) + "_t" + tier + "_" + slot.id();
    }
}
