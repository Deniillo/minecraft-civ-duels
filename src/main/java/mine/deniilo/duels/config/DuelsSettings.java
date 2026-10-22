package mine.deniilo.duels.config;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

public record DuelsSettings(
        WorldSettings lobby,
        WorldSettings arena) {

    private static final Pattern WORLD_NAME = Pattern.compile("[A-Za-z0-9_\\-]+");

    public record WorldSettings(String name, boolean generateIfMissing, String platformMaterial) {
    }

    public static DuelsSettings from(ConfigurationSection root) {
        if (root == null) {
            throw new IllegalArgumentException("Пустой config.yml");
        }
        ConfigurationSection worlds = root.getConfigurationSection("worlds");
        if (worlds == null) {
            throw new IllegalArgumentException("В config.yml нет раздела worlds");
        }
        WorldSettings lobby = readWorld(worlds, "lobby");
        WorldSettings arena = readWorld(worlds, "arena");

        Set<String> names = new HashSet<>();
        for (WorldSettings w : new WorldSettings[] {lobby, arena}) {
            if (!names.add(w.name())) {
                throw new IllegalArgumentException(
                        "Имена миров не должны повторяться, повторяется: " + w.name());
            }
        }
        return new DuelsSettings(lobby, arena);
    }

    private static WorldSettings readWorld(ConfigurationSection worlds, String key) {
        ConfigurationSection s = worlds.getConfigurationSection(key);
        if (s == null) {
            throw new IllegalArgumentException("В config.yml нет раздела worlds." + key);
        }
        String name = s.getString("name", "");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("worlds." + key + ".name не задан");
        }
        if (!WORLD_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "worlds." + key + ".name содержит недопустимые символы: " + name);
        }
        return new WorldSettings(
                name,
                s.getBoolean("generate-if-missing", true),
                s.getString("platform-material", "STONE_BRICKS"));
    }
}
