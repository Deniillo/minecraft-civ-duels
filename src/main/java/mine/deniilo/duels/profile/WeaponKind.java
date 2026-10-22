package mine.deniilo.duels.profile;

public enum WeaponKind {
    SWORD(-2.4),
    AXE(-3.0),
    TRIDENT(-2.9),
    BOW(0.0),
    CROSSBOW(0.0);

    private final double attackSpeedDelta;

    WeaponKind(double attackSpeedDelta) {
        this.attackSpeedDelta = attackSpeedDelta;
    }

    public double attackSpeedDelta() {
        return attackSpeedDelta;
    }

    public boolean isMelee() {
        return this == SWORD || this == AXE || this == TRIDENT;
    }
}
