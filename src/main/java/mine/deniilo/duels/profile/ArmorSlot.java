package mine.deniilo.duels.profile;

public enum ArmorSlot {
    HELMET("helmet"),
    CHESTPLATE("chestplate"),
    LEGGINGS("leggings"),
    BOOTS("boots");

    private final String id;

    ArmorSlot(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
