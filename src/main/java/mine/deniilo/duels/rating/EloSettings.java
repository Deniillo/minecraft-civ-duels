package mine.deniilo.duels.rating;

import org.bukkit.configuration.ConfigurationSection;

public record EloSettings(int startRating, int kFactor, int floor, boolean conserveSum, int rulesVersion) {

    public static final int RULES_VERSION = 1;

    public static EloSettings from(ConfigurationSection root) {
        ConfigurationSection s = root == null ? null : root.getConfigurationSection("elo");
        int start = s == null ? 1000 : s.getInt("start-rating", 1000);
        int k = s == null ? 32 : s.getInt("k-factor", 32);
        int floor = s == null ? 0 : s.getInt("floor", 0);
        boolean conserve = s != null && s.getBoolean("conserve-sum", false);
        if (start < floor || k < 1 || floor < 0) {
            throw new IllegalArgumentException("elo: start-rating не меньше floor, k-factor не меньше 1, floor не меньше 0");
        }
        return new EloSettings(start, k, floor, conserve, RULES_VERSION);
    }
}
