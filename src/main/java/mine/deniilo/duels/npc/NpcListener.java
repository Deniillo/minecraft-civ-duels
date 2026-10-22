package mine.deniilo.duels.npc;

import mine.deniilo.duels.menu.DuelMenu;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;

public final class NpcListener implements Listener {

    private final NpcService npcs;
    private final DuelMenu menu;

    public NpcListener(NpcService npcs, DuelMenu menu) {
        this.npcs = npcs;
        this.menu = menu;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!npcs.isNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            menu.open(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (npcs.isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTarget(EntityTargetEvent event) {
        if (npcs.isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
