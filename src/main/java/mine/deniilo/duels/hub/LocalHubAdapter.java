package mine.deniilo.duels.hub;

import mine.deniilo.duels.world.SpawnStore;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class LocalHubAdapter implements HubAdapter {

    private final SpawnStore spawns;

    public LocalHubAdapter(SpawnStore spawns) {
        this.spawns = spawns;
    }

    @Override
    public boolean sendToHub(Player player) {
        Location hub = spawns.resolve(SpawnStore.HUB);
        if (hub == null) {
            return false;
        }
        player.teleport(hub);
        return true;
    }
}
