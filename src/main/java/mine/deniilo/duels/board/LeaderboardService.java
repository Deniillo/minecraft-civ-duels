package mine.deniilo.duels.board;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.rating.RatingRepository;
import mine.deniilo.duels.world.SpawnStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public final class LeaderboardService {

    private final Plugin plugin;
    private final Messages messages;
    private final RatingRepository ratings;
    private final SpawnStore spawns;
    private final NamespacedKey tag;
    private final int size;
    private final int updateSeconds;

    public LeaderboardService(Plugin plugin, Messages messages, RatingRepository ratings, SpawnStore spawns,
                              int size, int updateSeconds) {
        this.plugin = plugin;
        this.messages = messages;
        this.ratings = ratings;
        this.spawns = spawns;
        this.tag = new NamespacedKey(plugin, "leaderboard");
        this.size = size;
        this.updateSeconds = updateSeconds;
    }

    public void start(boolean autoCreate) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (autoCreate) {
                ensure(spawns.resolve(SpawnStore.LOBBY));
            }
            refresh();
        }, 40L);
        long period = updateSeconds * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, period, period);
    }

    private boolean isBoard(org.bukkit.entity.Entity e) {
        return e instanceof TextDisplay && e.getPersistentDataContainer().has(tag, PersistentDataType.BYTE);
    }

    private List<TextDisplay> boards(World world) {
        List<TextDisplay> out = new ArrayList<>();
        for (TextDisplay d : world.getEntitiesByClass(TextDisplay.class)) {
            if (isBoard(d)) {
                out.add(d);
            }
        }
        return out;
    }

    private void ensure(Location spawnPoint) {
        if (spawnPoint == null || spawnPoint.getWorld() == null) {
            return;
        }
        ensure(spawnPoint, 0);
    }

    // Paper грузит сущности чанка позже самого чанка: до этого «таблицы нет» ложно и появится дубликат.
    private void ensure(Location spawnPoint, int attempt) {
        Vector forward = spawnPoint.getDirection().setY(0).normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        Location at = spawnPoint.clone().add(forward.multiply(3)).add(right.multiply(4)).add(0, 2.2, 0);
        at.setYaw(spawnPoint.getYaw() + 180f);
        at.setPitch(0f);
        boolean loaded = true;
        for (org.bukkit.Chunk chunk : new org.bukkit.Chunk[] {spawnPoint.getChunk(), at.getChunk()}) {
            chunk.load();
            loaded &= chunk.isEntitiesLoaded();
        }
        if (!loaded) {
            if (attempt < 30) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> ensure(spawnPoint, attempt + 1), 20L);
            }
            return;
        }
        if (!boards(spawnPoint.getWorld()).isEmpty()) {
            return;
        }
        spawn(at);
    }

    public TextDisplay spawn(Location at) {
        return at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setPersistent(true);
            d.setSeeThrough(false);
            d.setShadowed(true);
            d.setLineWidth(260);
            d.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
            d.text(Component.text("…"));
        });
    }

    public int removeAll(World world) {
        int n = 0;
        for (TextDisplay d : boards(world)) {
            d.remove();
            n++;
        }
        return n;
    }

    public void refresh() {
        Component text;
        try {
            List<RatingRepository.TopEntry> top = ratings.top(size);
            List<Component> lines = new ArrayList<>();
            lines.add(messages.raw("leaderboard-title"));
            lines.add(messages.raw("leaderboard-subtitle"));
            lines.add(Component.empty());
            if (top.isEmpty()) {
                lines.add(messages.raw("leaderboard-empty"));
            }
            int place = 1;
            for (RatingRepository.TopEntry e : top) {
                lines.add(messages.raw("leaderboard-line", "place", String.valueOf(place++), "player", e.name(),
                        "rating", String.valueOf(e.rating())));
            }
            text = Component.join(JoinConfiguration.newlines(), lines);
        } catch (SQLException e) {
            plugin.getLogger().warning("Таблица лидеров: " + e.getMessage());
            return;
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay d : boards(world)) {
                Location l = d.getLocation();
                String spot = world.getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
                if (!seen.add(spot)) {
                    d.remove();
                } else {
                    d.text(text);
                }
            }
        }
    }
}
