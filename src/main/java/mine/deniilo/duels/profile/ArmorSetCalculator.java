package mine.deniilo.duels.profile;

import java.util.List;

public final class ArmorSetCalculator {

    public static final double BASE_MAX_HP = 20.0;
    public static final int FULL_SET = 4;

    private ArmorSetCalculator() {
    }

    public static ArmorSetStats compute(List<ArmorPiece> pieces) {
        double defense = 0;
        double hp = 0;
        double speed = 0;
        int light = 0;
        int heavy = 0;
        int soul = 0;
        int soulT5 = 0;
        for (ArmorPiece p : pieces) {
            ArmorProfile profile = p.profile();
            defense += p.defense();
            hp += profile.hp();
            speed += profile.speed();
            switch (profile.armorClass()) {
                case LIGHT -> light++;
                case HEAVY -> heavy++;
                case SOUL -> {
                    soul++;
                    if (profile.tier() == 5) {
                        soulT5++;
                    }
                }
            }
        }
        boolean soulSet = soul == FULL_SET && pieces.size() == FULL_SET;
        boolean soulT5Set = soulT5 == FULL_SET && pieces.size() == FULL_SET;
        return new ArmorSetStats(defense, BASE_MAX_HP + hp, speed, light, heavy, soul, soulSet, soulT5Set);
    }
}
