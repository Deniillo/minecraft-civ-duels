package mine.deniilo.duels.config;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public record SpawnPoint(String world, double x, double y, double z, float yaw, float pitch) {

    public static SpawnPoint fromLocation(Location l) {
        if (l.getWorld() == null) {
            throw new IllegalArgumentException("У точки нет мира");
        }
        return new SpawnPoint(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public Location toLocation(World w) {
        return new Location(w, x, y, z, yaw, pitch);
    }

    public void writeTo(ConfigurationSection s) {
        s.set("world", world);
        s.set("x", x);
        s.set("y", y);
        s.set("z", z);
        s.set("yaw", (double) yaw);
        s.set("pitch", (double) pitch);
    }

    public static SpawnPoint read(ConfigurationSection s) {
        if (s == null || !s.isString("world")) {
            return null;
        }
        if (!s.isDouble("x") && !s.isInt("x")) {
            return null;
        }
        return new SpawnPoint(
                s.getString("world"),
                s.getDouble("x"),
                s.getDouble("y"),
                s.getDouble("z"),
                (float) s.getDouble("yaw"),
                (float) s.getDouble("pitch"));
    }
}
