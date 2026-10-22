package mine.deniilo.duels.match;

import java.util.Optional;
import java.util.UUID;
import mine.deniilo.duels.item.ItemReader;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.profile.ItemCatalog;
import mine.deniilo.duels.world.SpawnStore;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public final class MatchListener implements Listener {

    private final MatchManager manager;
    private final ItemReader reader;
    private final ItemCatalog items;
    private final SpawnStore spawns;
    private final Messages messages;
    private final String arenaWorld;

    public MatchListener(MatchManager manager, ItemReader reader, ItemCatalog items, SpawnStore spawns,
                         Messages messages, String arenaWorld) {
        this.manager = manager;
        this.reader = reader;
        this.items = items;
        this.spawns = spawns;
        this.messages = messages;
        this.arenaWorld = arenaWorld;
    }

    private Optional<DuelMatch> matchOf(UUID id) {
        return manager.matchOf(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBotLethal(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity living) || living instanceof Player) {
            return;
        }
        Optional<DuelMatch> match = matchOf(living.getUniqueId());
        if (match.isEmpty() || !match.get().isSparringBot(living.getUniqueId())
                || match.get().phase() != MatchPhase.ACTIVE) {
            return;
        }
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            match.get().returnBot(living);
            return;
        }
        if (living.getHealth() - event.getFinalDamage() > 0) {
            return;
        }
        event.setCancelled(true);
        match.get().onBotDefeated(living);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Optional<DuelMatch> match = matchOf(player.getUniqueId());
        if (match.isEmpty()) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.deathMessage(null);
        match.get().onDeath(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        Optional<DuelMatch> match = matchOf(event.getEntity().getUniqueId());
        if (match.isEmpty()) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        match.get().onDeath(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Optional<DuelMatch> match = matchOf(player.getUniqueId());
        if (match.isPresent()) {
            event.setRespawnLocation(match.get().spawnOf(player.getUniqueId()));
            return;
        }
        if (manager.store().has(player.getUniqueId())) {
            Location lobby = spawns.resolve(SpawnStore.LOBBY);
            if (lobby != null) {
                event.setRespawnLocation(lobby);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        matchOf(event.getPlayer().getUniqueId()).ifPresent(m -> m.onLeave(event.getPlayer().getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Optional<DuelMatch> match = matchOf(player.getUniqueId());
        if (match.isPresent() && match.get().phase().ordinal() < MatchPhase.FINISHING.ordinal()
                && !player.getWorld().equals(match.get().arenaWorld())) {
            match.get().onLeave(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (manager.restoreOnJoin(event.getPlayer())) {
            messages.send(event.getPlayer(), "match-restored");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && manager.registry().session(player.getUniqueId()).isPresent()
                && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
            player.setSaturation(20f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        if (matchOf(event.getPlayer().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Optional<DuelMatch> match = matchOf(event.getPlayer().getUniqueId());
        if (match.isEmpty() || !match.get().frozen()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
            event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Optional<DuelMatch> match = matchOf(event.getPlayer().getUniqueId());
        if (match.isEmpty() || match.get().phase().ordinal() >= MatchPhase.FINISHING.ordinal()) {
            return;
        }
        TeleportCause cause = event.getCause();
        if (cause == TeleportCause.COMMAND || cause == TeleportCause.ENDER_PEARL || cause == TeleportCause.CONSUMABLE_EFFECT) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || matchOf(player.getUniqueId()).isEmpty()) {
            return;
        }
        boolean armorSlot = event.getSlotType() == InventoryType.SlotType.ARMOR;
        boolean shiftArmor = event.isShiftClick() && event.getCurrentItem() != null
                && reader.armorPiece(event.getCurrentItem(), items).isPresent();
        if (armorSlot || shiftArmor) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (matchOf(event.getPlayer().getUniqueId()).isPresent()
                && reader.itemId(event.getItemDrop().getItemStack()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && matchOf(player.getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (matchOf(player.getUniqueId()).isEmpty() || player.hasPermission("duels.admin")) {
            return;
        }
        String first = event.getMessage().toLowerCase(java.util.Locale.ROOT).split(" ")[0];
        if (!first.equals("/duel") && !first.equals("/duels")) {
            event.setCancelled(true);
            messages.send(player, "match-command-blocked");
        }
    }
}
