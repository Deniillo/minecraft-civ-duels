package mine.deniilo.duels.listener;

import mine.deniilo.duels.hub.DuelsGateway;
import mine.deniilo.duels.world.SpawnStore;
import mine.deniilo.duels.world.WorldService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class LobbyListener implements Listener {

    private static final double VOID_Y = 0;

    private final WorldService worlds;
    private final SpawnStore spawns;
    private final DuelsGateway gateway;
    private final boolean joinToLobby;

    public LobbyListener(WorldService worlds, SpawnStore spawns, DuelsGateway gateway, boolean joinToLobby) {
        this.worlds = worlds;
        this.spawns = spawns;
        this.gateway = gateway;
        this.joinToLobby = joinToLobby;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (joinToLobby && !gateway.isInMatch(player)) {
            gateway.join(player);
        }
        adventureInLobby(player);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        adventureInLobby(event.getPlayer());
    }

    private void adventureInLobby(Player player) {
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
        Location spawn = spawns.resolve(SpawnStore.LOBBY);
        if (spawn != null) {
            event.getPlayer().teleport(spawn);
        }
    }
}
