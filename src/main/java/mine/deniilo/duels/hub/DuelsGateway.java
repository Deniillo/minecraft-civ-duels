package mine.deniilo.duels.hub;

import mine.deniilo.duels.api.DuelsApi;
import mine.deniilo.duels.match.MatchManager;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.queue.QueueCoordinator;
import mine.deniilo.duels.training.TrainingCoordinator;
import mine.deniilo.duels.world.SpawnStore;
import mine.deniilo.duels.world.WorldService;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class DuelsGateway implements DuelsApi {

    private final Messages messages;
    private final SpawnStore spawns;
    private final WorldService worlds;
    private final HubAdapter hub;
    private final MatchManager matches;
    private final QueueCoordinator queue;
    private final TrainingCoordinator training;

    public DuelsGateway(Messages messages, SpawnStore spawns, WorldService worlds, HubAdapter hub,
                        MatchManager matches, QueueCoordinator queue, TrainingCoordinator training) {
        this.messages = messages;
        this.spawns = spawns;
        this.worlds = worlds;
        this.hub = hub;
        this.matches = matches;
        this.queue = queue;
        this.training = training;
    }

    @Override
    public boolean join(Player player) {
        if (isInMatch(player)) {
            messages.send(player, "queue-in-match");
            return false;
        }
        Location lobby = spawns.resolve(SpawnStore.LOBBY);
        if (lobby == null) {
            messages.send(player, "world-missing", "world", "lobby");
            return false;
        }
        player.teleport(lobby);
        messages.send(player, "to-lobby");
        return true;
    }

    @Override
    public boolean exit(Player player) {
        if (isInMatch(player)) {
            messages.send(player, "exit-in-match");
            return false;
        }
        queue.leave(player.getUniqueId());
        training.cancel(player.getUniqueId());
        messages.send(player, "to-hub");
        if (!hub.sendToHub(player)) {
            messages.send(player, "hub-unavailable");
            return false;
        }
        return true;
    }

    @Override
    public boolean isInDuels(Player player) {
        return worlds.isDuelsWorld(player.getWorld());
    }

    @Override
    public boolean isInMatch(Player player) {
        return matches.matchOf(player.getUniqueId()).isPresent();
    }
}
