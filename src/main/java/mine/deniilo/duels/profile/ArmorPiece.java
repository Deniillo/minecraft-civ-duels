package mine.deniilo.duels.profile;

public record ArmorPiece(ArmorProfile profile, int protectionLevel) {

    public static final double PROTECTION_PER_LEVEL = 0.25;

    public double defense() {
        return profile.defense() + PROTECTION_PER_LEVEL * protectionLevel;
    }
}
