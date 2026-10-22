package mine.deniilo.duels.profile;

public record ArmorSetStats(
        double defense,
        double maxHp,
        double speed,
        int lightCount,
        int heavyCount,
        int soulCount,
        boolean soulSet,
        boolean soulT5Set) {
}
