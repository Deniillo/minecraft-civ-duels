package mine.deniilo.duels.match;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

public final class RestoreStore {

    private final Plugin plugin;
    private final File dir;

    public RestoreStore(Plugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "restore");
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    public boolean has(UUID id) {
        return file(id).isFile();
    }

    public void save(Player p) throws IOException {
        if (has(p.getUniqueId())) {
            // Прошлая запись ещё не восстановлена: перезаписывать её нельзя, иначе потеряем настоящие вещи.
            throw new IOException("Для игрока уже есть неприменённая запись восстановления");
        }
        Files.createDirectories(dir.toPath());
        YamlConfiguration y = new YamlConfiguration();
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && !contents[i].getType().isAir()) {
                y.set("inv." + i, contents[i]);
            }
        }
        y.set("size", contents.length);
        y.set("held", inv.getHeldItemSlot());
        Location l = p.getLocation();
        y.set("loc.world", l.getWorld().getName());
        y.set("loc.x", l.getX());
        y.set("loc.y", l.getY());
        y.set("loc.z", l.getZ());
        y.set("loc.yaw", (double) l.getYaw());
        y.set("loc.pitch", (double) l.getPitch());
        y.set("health", p.getHealth());
        y.set("food", p.getFoodLevel());
        y.set("saturation", (double) p.getSaturation());
        y.set("exhaustion", (double) p.getExhaustion());
        y.set("level", p.getLevel());
        y.set("exp", (double) p.getExp());
        y.set("gamemode", p.getGameMode().name());
        y.set("effects", new ArrayList<>(p.getActivePotionEffects()));

        Path target = file(p.getUniqueId()).toPath();
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, y.saveToString());
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        if (!Files.isRegularFile(target)) {
            throw new IOException("Запись восстановления не подтверждена");
        }
    }

    public void discard(UUID id) {
        File f = file(id);
        if (f.isFile() && !f.delete()) {
            plugin.getLogger().warning("Не удалось удалить запись восстановления " + f.getName());
        }
    }

    public boolean restore(Player p, Location fallback) {
        File f = file(p.getUniqueId());
        if (!f.isFile()) {
            return false;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);

        PlayerInventory inv = p.getInventory();
        int size = inv.getContents().length;
        ItemStack[] contents = new ItemStack[size];
        ConfigurationSection items = y.getConfigurationSection("inv");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                try {
                    int index = Integer.parseInt(key);
                    if (index >= 0 && index < size) {
                        contents[index] = items.getItemStack(key);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        inv.clear();
        inv.setContents(contents);
        inv.setHeldItemSlot(Math.max(0, Math.min(8, y.getInt("held", 0))));

        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) {
            p.removePotionEffect(e.getType());
        }
        List<?> effects = y.getList("effects");
        if (effects != null) {
            for (Object o : effects) {
                if (o instanceof PotionEffect e) {
                    p.addPotionEffect(e);
                }
            }
        }

        p.setFoodLevel(y.getInt("food", 20));
        p.setSaturation((float) y.getDouble("saturation", 5.0));
        p.setExhaustion((float) y.getDouble("exhaustion", 0.0));
        p.setLevel(y.getInt("level", 0));
        p.setExp((float) y.getDouble("exp", 0.0));
        try {
            p.setGameMode(GameMode.valueOf(y.getString("gamemode", "SURVIVAL")));
        } catch (IllegalArgumentException e) {
            p.setGameMode(GameMode.SURVIVAL);
        }

        Location target = fallback;
        World world = Bukkit.getWorld(y.getString("loc.world", ""));
        if (world != null) {
            target = new Location(world, y.getDouble("loc.x"), y.getDouble("loc.y"), y.getDouble("loc.z"),
                    (float) y.getDouble("loc.yaw"), (float) y.getDouble("loc.pitch"));
        }
        if (target != null) {
            p.teleport(target);
        }

        Runnable applyHealth = () -> {
            if (p.isOnline() && !p.isDead()) {
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                double limit = max == null ? 20.0 : max.getValue();
                p.setHealth(limit);
            }
        };
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, applyHealth);
        } else {
            applyHealth.run();
        }

        if (!f.delete()) {
            plugin.getLogger().warning("Не удалось удалить запись восстановления " + f.getName());
        }
        return true;
    }
}
