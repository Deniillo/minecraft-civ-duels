package mine.deniilo.duels.queue;

import org.bukkit.configuration.ConfigurationSection;

public record QueueSettings(int baseWindow, int widenPerStep, int stepSeconds, int maxWindow) {

    public static QueueSettings from(ConfigurationSection root) {
        ConfigurationSection s = root == null ? null : root.getConfigurationSection("queue");
        int base = s == null ? 100 : s.getInt("base-window", 100);
        int widen = s == null ? 50 : s.getInt("widen-per-step", 50);
        int step = s == null ? 5 : s.getInt("step-seconds", 5);
        int max = s == null ? 1000 : s.getInt("max-window", 1000);
        if (base < 0 || widen < 0 || step < 1 || max < base) {
            throw new IllegalArgumentException(
                    "queue: base-window и widen-per-step не меньше 0, step-seconds не меньше 1, max-window не меньше base-window");
        }
        return new QueueSettings(base, widen, step, max);
    }

    public int windowAfter(long waitedMillis) {
        long steps = Math.max(0, waitedMillis) / (stepSeconds * 1000L);
        long window = baseWindow + steps * widenPerStep;
        return (int) Math.min(window, maxWindow);
    }
}
