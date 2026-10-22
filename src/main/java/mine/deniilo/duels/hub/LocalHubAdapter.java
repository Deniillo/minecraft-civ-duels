package mine.deniilo.duels.hub;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class LocalHubAdapter implements HubAdapter {

    private final HubSettings settings;

    public LocalHubAdapter(HubSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean sendToHub(Player player) {
        if (!settings.command().isEmpty()) {
            String command = settings.commandFor(player.getName());
            return settings.commandAsConsole()
                    ? Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                    : player.performCommand(command);
        }
        World world = settings.world().isEmpty() ? Bukkit.getWorlds().get(0) : Bukkit.getWorld(settings.world());
        if (world == null) {
            return false;
        }
        return player.teleport(world.getSpawnLocation().add(0.5, 0, 0.5));
    }
}
