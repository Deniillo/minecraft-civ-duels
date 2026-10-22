package mine.deniilo.duels.profile;

import java.util.List;

public record Kit(
        String id,
        String displayName,
        ArmorClass armorClass,
        int armorTier,
        int protectionLevel,
        List<String> weaponIds,
        int attackLevel,
        int piercingLevel) {

    public static final int MAX_ATTACK_LEVEL = 7;
    public static final int MAX_PROTECTION_LEVEL = 5;
    public static final int MAX_PIERCING_LEVEL = 3;

    public boolean enhanced() {
        return protectionLevel > 0 || attackLevel > 0;
    }
}
