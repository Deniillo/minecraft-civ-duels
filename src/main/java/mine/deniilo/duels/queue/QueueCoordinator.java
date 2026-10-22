package mine.deniilo.duels.queue;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import mine.deniilo.duels.match.MatchManager;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.profile.Kit;
import mine.deniilo.duels.profile.KitCatalog;
import mine.deniilo.duels.rating.RatingRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class QueueCoordinator {

    private final Plugin plugin;
    private final QueueService queue;
    private final RatingRepository ratings;
    private final MatchManager matches;
    private final KitCatalog kits;
    private final Messages messages;

    public QueueCoordinator(Plugin plugin, QueueService queue, RatingRepository ratings, MatchManager matches,
                            KitCatalog kits, Messages messages) {
        this.plugin = plugin;
        this.queue = queue;
        this.ratings = ratings;
        this.matches = matches;
        this.kits = kits;
        this.messages = messages;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public int searching() {
        return queue.waitingTotal();
    }

    public Optional<QueueService.Key> keyOf(UUID id) {
        return queue.keyOf(id);
    }

    public void enqueue(Player player, Kit kit, boolean ranked) {
        if (matches.matchOf(player.getUniqueId()).isPresent()) {
            messages.send(player, "queue-in-match");
            return;
        }
        int rating = 0;
        if (ranked) {
            try {
                rating = ratings.rating(player.getUniqueId());
            } catch (SQLException e) {
                plugin.getLogger().severe("Не удалось прочитать рейтинг: " + e.getMessage());
                messages.send(player, "stats-error");
                return;
            }
        }
        queue.join(player.getUniqueId(), new QueueService.Key(kit.id(), ranked), rating, System.currentTimeMillis());
        messages.send(player, "queue-joined", "kit", kit.displayName(),
                "mode", ranked ? "рейтинговый" : "тренировочный");
        tick();
    }

    public boolean leave(UUID id) {
        return queue.leave(id);
    }

    private void tick() {
        for (QueueService.Pair pair : queue.match(System.currentTimeMillis())) {
            Player a = Bukkit.getPlayer(pair.first());
            Player b = Bukkit.getPlayer(pair.second());
            Optional<Kit> kit = kits.get(pair.key().kitId());
            if (a == null || b == null || kit.isEmpty()) {
                requeue(a, pair.key());
                requeue(b, pair.key());
                continue;
            }
            Optional<MatchManager.StartError> error = matches.start(a, b, kit.get(), pair.key().ranked());
            if (error.isEmpty()) {
                String mode = pair.key().ranked() ? "рейтинговый" : "тренировочный";
                plugin.getLogger().info("Подбор: " + a.getName() + " и " + b.getName() + ", " + pair.key().kitId() + ", " + mode);
                messages.send(a, "queue-found", "opponent", b.getName(), "mode", mode);
                messages.send(b, "queue-found", "opponent", a.getName(), "mode", mode);
            } else if (error.get() == MatchManager.StartError.NO_ARENA) {
                requeue(a, pair.key());
                requeue(b, pair.key());
            } else {
                String key = MatchManager.errorKey(error.get());
                messages.send(a, key);
                messages.send(b, key);
            }
        }
    }

    private void requeue(Player player, QueueService.Key key) {
        if (player == null || !player.isOnline()) {
            return;
        }
        int rating = 0;
        try {
            rating = key.ranked() ? ratings.rating(player.getUniqueId()) : 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Не удалось прочитать рейтинг: " + e.getMessage());
            return;
        }
        queue.join(player.getUniqueId(), key, rating, System.currentTimeMillis());
    }
}
