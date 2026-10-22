package mine.deniilo.duels.npc;

import java.util.List;
import mine.deniilo.duels.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class NpcService {

    private final Plugin plugin;
    private final Messages messages;
    private final NamespacedKey tag;

    public NpcService(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.tag = new NamespacedKey(plugin, "menu_npc");
    }

    public boolean isNpc(Entity entity) {
        return entity instanceof Villager
                && entity.getPersistentDataContainer().has(tag, PersistentDataType.BYTE);
    }

    public List<Villager> existing(World world) {
        return world.getEntitiesByClass(Villager.class).stream().filter(this::isNpc).toList();
    }

    public Villager spawn(Location at) {
        return at.getWorld().spawn(at, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setCollidable(false);
            v.setProfession(Villager.Profession.WEAPONSMITH);
            v.customName(messages.raw("npc-name"));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
    }

    public boolean ensure(Location spawnPoint) {
        World world = spawnPoint.getWorld();
        if (world == null) {
            return false;
        }
        spawnPoint.getChunk().load();
        if (!existing(world).isEmpty()) {
            return false;
        }
        Location at = spawnPoint.clone().add(spawnPoint.getDirection().setY(0).normalize().multiply(3));
        at.setYaw(spawnPoint.getYaw() + 180f);
        at.setPitch(0f);
        spawn(at);
        return true;
    }

    public int removeAll(World world) {
        int n = 0;
        for (Villager v : existing(world)) {
            v.remove();
            n++;
        }
        return n;
    }

    public void ensureLater(Location spawnPoint) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> ensure(spawnPoint), 40L);
    }
}
