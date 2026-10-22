package mine.deniilo.duels.world;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mine.deniilo.duels.config.DuelsSettings;
import mine.deniilo.duels.config.SpawnPoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpawnStore {

    public static final String HUB = "hub";
    public static final String LOBBY = "lobby";
    public static final String ARENA_A = "arena-a";
    public static final String ARENA_B = "arena-b";
    public static final List<String> KEYS = List.of(HUB, LOBBY, ARENA_A, ARENA_B);

    private final JavaPlugin plugin;
    private final DuelsSettings settings;
    private final File file;
    private final Map<String, SpawnPoint> points = new HashMap<>();

    public SpawnStore(JavaPlugin plugin, DuelsSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.file = new File(plugin.getDataFolder(), "spawns.yml");
        load();
    }

    private void load() {
        points.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : KEYS) {
            SpawnPoint p = SpawnPoint.read(yaml.getConfigurationSection(key));
            if (p != null) {
                points.put(key, p);
            }
        }
    }

    public boolean isKnownKey(String key) {
        return KEYS.contains(key);
    }

    public boolean isConfigured(String key) {
        return points.containsKey(key);
    }

    public void set(String key, Location location) throws IOException {
        points.put(key, SpawnPoint.fromLocation(location));
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, SpawnPoint> e : points.entrySet()) {
            e.getValue().writeTo(yaml.createSection(e.getKey()));
        }
        plugin.getDataFolder().mkdirs();
        yaml.save(file);
    }

    public Location resolve(String key) {
        SpawnPoint p = points.get(key);
        if (p != null) {
            World w = Bukkit.getWorld(p.world());
            if (w != null) {
                return p.toLocation(w);
            }
            plugin.getLogger().warning("Точка '" + key + "' указывает на незагруженный мир " + p.world());
        }
        World w = Bukkit.getWorld(defaultWorldName(key));
        if (w == null) {
            return null;
        }
        Location base = w.getSpawnLocation().add(0.5, 0, 0.5);
        if (key.equals(ARENA_A) || key.equals(ARENA_B)) {
            base.setY(w.getHighestBlockYAt(base) + 1);
        }
        return base;
    }

    private String defaultWorldName(String key) {
        return switch (key) {
            case HUB -> settings.hub().name();
            case LOBBY -> settings.lobby().name();
            default -> settings.arena().name();
        };
    }
}
