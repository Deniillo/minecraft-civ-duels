package mine.deniilo.duels.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MatchSettingsTest {

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return y;
    }

    @Test
    void defaultsWhenSectionMissing() {
        MatchSettings s = MatchSettings.from(yaml("a: 1"));
        assertEquals(3, s.countdownSeconds());
        assertEquals(600, s.maxDurationSeconds());
        assertEquals(60, s.resultDelayTicks());
    }

    @Test
    void readsValues() {
        MatchSettings s = MatchSettings.from(yaml("match:\n  countdown-seconds: 3\n  max-duration-seconds: 120\n  result-delay-ticks: 20"));
        assertEquals(3, s.countdownSeconds());
        assertEquals(120, s.maxDurationSeconds());
        assertEquals(20, s.resultDelayTicks());
    }

    @Test
    void rejectsBadValues() {
        assertThrows(IllegalArgumentException.class, () -> MatchSettings.from(yaml("match:\n  countdown-seconds: 0")));
        assertThrows(IllegalArgumentException.class, () -> MatchSettings.from(yaml("match:\n  max-duration-seconds: 5")));
        assertThrows(IllegalArgumentException.class, () -> MatchSettings.from(yaml("match:\n  result-delay-ticks: -1")));
    }
}
