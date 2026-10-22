package mine.deniilo.duels.match;

import org.bukkit.configuration.ConfigurationSection;

public record MatchSettings(int countdownSeconds, int maxDurationSeconds, int resultDelayTicks) {

    public static MatchSettings from(ConfigurationSection root) {
        ConfigurationSection s = root == null ? null : root.getConfigurationSection("match");
        int countdown = s == null ? 3 : s.getInt("countdown-seconds", 3);
        int duration = s == null ? 600 : s.getInt("max-duration-seconds", 600);
        int delay = s == null ? 60 : s.getInt("result-delay-ticks", 60);
        if (countdown < 1 || countdown > 60) {
            throw new IllegalArgumentException("match.countdown-seconds должно быть от 1 до 60");
        }
        if (duration < 10) {
            throw new IllegalArgumentException("match.max-duration-seconds должно быть не меньше 10");
        }
        if (delay < 0 || delay > 600) {
            throw new IllegalArgumentException("match.result-delay-ticks должно быть от 0 до 600");
        }
        return new MatchSettings(countdown, duration, delay);
    }
}
