package mine.deniilo.duels.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DuelsSettingsTest {

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return y;
    }

    private static final String VALID = """
            join-teleport-to-hub: false
            worlds:
              hub: {name: duels_hub, generate-if-missing: true, platform-material: SMOOTH_QUARTZ}
              lobby: {name: duels_lobby, generate-if-missing: true, platform-material: STONE_BRICKS}
              arena: {name: duels_arena, generate-if-missing: false}
            """;

    @Test
    void readsValidConfig() {
        DuelsSettings s = DuelsSettings.from(yaml(VALID));
        assertFalse(s.joinTeleportToHub());
        assertEquals("duels_hub", s.hub().name());
        assertEquals("SMOOTH_QUARTZ", s.hub().platformMaterial());
        assertTrue(s.lobby().generateIfMissing());
        assertFalse(s.arena().generateIfMissing());
        assertEquals("STONE_BRICKS", s.arena().platformMaterial(), "значение по умолчанию");
    }

    @Test
    void failsWithoutWorldsSection() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> DuelsSettings.from(yaml("a: 1")));
        assertTrue(e.getMessage().contains("worlds"));
    }

    @Test
    void failsWhenWorldNameMissing() {
        String text = VALID.replace("name: duels_lobby, ", "");
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> DuelsSettings.from(yaml(text)));
        assertTrue(e.getMessage().contains("worlds.lobby.name"));
    }

    @Test
    void failsOnDuplicateNames() {
        String text = VALID.replace("duels_arena", "duels_hub");
        assertThrows(IllegalArgumentException.class, () -> DuelsSettings.from(yaml(text)));
    }

    @Test
    void failsOnBadWorldName() {
        String text = VALID.replace("duels_hub", "../etc");
        assertThrows(IllegalArgumentException.class, () -> DuelsSettings.from(yaml(text)));
    }

    @Test
    void spawnPointRoundTrip() {
        YamlConfiguration y = new YamlConfiguration();
        SpawnPoint p = new SpawnPoint("duels_arena", 10.5, 70, -3.25, 90f, 12.5f);
        p.writeTo(y.createSection("arena-a"));
        SpawnPoint back = SpawnPoint.read(y.getConfigurationSection("arena-a"));
        assertNotNull(back);
        assertEquals(p, back);
    }

    @Test
    void spawnPointMissingIsNull() {
        YamlConfiguration y = new YamlConfiguration();
        assertNull(SpawnPoint.read(y.getConfigurationSection("nope")));
        y.createSection("empty");
        assertNull(SpawnPoint.read(y.getConfigurationSection("empty")));
    }
}
