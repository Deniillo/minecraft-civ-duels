package mine.deniilo.duels.listener;

import mine.deniilo.duels.config.DuelsSettings;
import mine.deniilo.duels.hub.HubAdapter;
import mine.deniilo.duels.world.SpawnStore;
import mine.deniilo.duels.world.WorldService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class LobbyListener implements Listener {

    private static final double VOID_Y = 0;

    private final DuelsSettings settings;
    private final WorldService worlds;
    private final SpawnStore spawns;
    private final HubAdapter hub;

    public LobbyListener(DuelsSettings settings, WorldService worlds, SpawnStore spawns, HubAdapter hub) {
        this.settings = settings;
        this.worlds = worlds;
        this.spawns = spawns;
        this.hub = hub;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (settings.joinTeleportToHub()) {
            hub.sendToHub(player);
        }
        if (worlds.isLobbyWorld(player.getWorld()) && !player.hasPermission("duels.admin")) {
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && worlds.isLobbyWorld(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && worlds.isLobbyWorld(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to.getY() >= VOID_Y || !worlds.isLobbyWorld(to.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        String key = to.getWorld().getName().equals(settings.hub().name()) ? SpawnStore.HUB : SpawnStore.LOBBY;
        Location spawn = spawns.resolve(key);
        if (spawn != null) {
            player.teleport(spawn);
        }
    }
}
