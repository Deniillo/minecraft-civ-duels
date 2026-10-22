package mine.deniilo.duels.training;

import java.util.Optional;
import java.util.UUID;
import mine.deniilo.duels.match.MatchManager;
import mine.deniilo.duels.match.MatchResult;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.profile.Kit;
import mine.deniilo.duels.profile.KitCatalog;
import mine.deniilo.duels.queue.QueueCoordinator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class TrainingCoordinator {

    private final TrainingRooms rooms;
    private final MatchManager matches;
    private final QueueCoordinator queue;
    private final KitCatalog kits;
    private final Messages messages;

    public TrainingCoordinator(TrainingRooms rooms, MatchManager matches, QueueCoordinator queue, KitCatalog kits,
                               Messages messages) {
        this.rooms = rooms;
        this.matches = matches;
        this.queue = queue;
        this.kits = kits;
        this.messages = messages;
    }

    public TrainingRooms rooms() {
        return rooms;
    }

    public void host(Player player, int room, Kit kit) {
        if (matches.matchOf(player.getUniqueId()).isPresent() || rooms.isPlaying(player.getUniqueId())) {
            messages.send(player, "queue-in-match");
            return;
        }
        queue.leave(player.getUniqueId());
        if (!rooms.host(room, player.getUniqueId(), kit.id())) {
            messages.send(player, "training-room-unavailable");
            return;
        }
        messages.send(player, "training-room-created", "room", String.valueOf(room + 1), "kit", kit.displayName());
    }

    public void join(Player player, int room) {
        if (matches.matchOf(player.getUniqueId()).isPresent()) {
            messages.send(player, "queue-in-match");
            return;
        }
        Optional<TrainingRooms.Room> joined = rooms.join(room, player.getUniqueId());
        if (joined.isEmpty()) {
            messages.send(player, "training-room-unavailable");
            return;
        }
        Player host = Bukkit.getPlayer(joined.get().host());
        Optional<Kit> kit = kits.get(joined.get().kitId());
        if (host == null || !host.isOnline() || kit.isEmpty()) {
            rooms.revertToWaiting(room);
            rooms.leave(joined.get().host());
            messages.send(player, "training-room-unavailable");
            return;
        }
        queue.leave(player.getUniqueId());
        Optional<MatchManager.StartError> error = matches.start(host, player, kit.get(), false);
        if (error.isPresent()) {
            rooms.revertToWaiting(room);
            String key = MatchManager.errorKey(error.get());
            messages.send(player, key);
            messages.send(host, key);
            return;
        }
        messages.send(host, "training-started", "opponent", player.getName());
        messages.send(player, "training-started", "opponent", host.getName());
    }

    public boolean cancel(UUID player) {
        return rooms.leave(player);
    }

    public void onMatchFinished(MatchResult result) {
        rooms.finished(result.playerA(), result.playerB());
    }
}
