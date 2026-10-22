package mine.deniilo.duels.world;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mine.deniilo.duels.config.DuelsSettings;
import mine.deniilo.duels.match.ArenaPool;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ArenaInstanceService {

    public enum Failure {
        NO_SPAWNS, NO_ARENA, CREATE_FAILED
    }

    public record Instance(UUID matchId, World world, Location spawnA, Location spawnB) {
    }

    public record Acquire(Instance instance, Failure failure) {
        public boolean ok() {
            return instance != null;
        }
    }

    // chunk_tickets.dat: принудительно загруженные чанки карты (у DKP их 442), копии они не нужны
    private static final Set<String> SKIP = Set.of("uid.dat", "session.lock", "playerdata", "stats", "advancements",
            "chunk_tickets.dat");
    private static final Set<String> REGION_DIRS = Set.of("region", "entities", "poi");
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final int GROUND_SEARCH = 64;
    /** Метаданные Paper с идентификатором мира: с ними копия считается дубликатом шаблона и не загружается. */
    private static final Path PAPER_METADATA = Path.of("data", "paper", "metadata.dat");

    private final Plugin plugin;
    private final DuelsSettings settings;
    private final WorldService worlds;
    private final SpawnStore spawns;
    private final ArenaPool pool;
    private final int borderSize;
    private final int copyRadius;
    private final Map<UUID, Instance> active = new HashMap<>();

    public ArenaInstanceService(Plugin plugin, DuelsSettings settings, WorldService worlds, SpawnStore spawns,
                                int capacity, int borderSize, int copyRadius) {
        this.plugin = plugin;
        this.settings = settings;
        this.worlds = worlds;
        this.spawns = spawns;
        this.pool = new ArenaPool(capacity);
        this.borderSize = borderSize;
        this.copyRadius = copyRadius;
    }

    private String prefix() {
        return settings.arena().name() + "_";
    }

    public void cleanupLeftovers() {
        cleanupLeftovers(Bukkit.getWorldContainer());
        if (!Bukkit.getWorlds().isEmpty()) {
            cleanupLeftovers(Bukkit.getWorlds().get(0).getWorldFolder().getParentFile());
        }
    }

    private void cleanupLeftovers(File parent) {
        File[] dirs = parent == null ? null : parent.listFiles(f -> f.isDirectory() && f.getName().startsWith(prefix()));
        if (dirs == null) {
            return;
        }
        for (File dir : dirs) {
            if (Bukkit.getWorld(dir.getName()) == null) {
                try {
                    deleteRecursively(dir.toPath());
                    plugin.getLogger().info("Удалена старая копия арены " + dir.getName());
                } catch (IOException e) {
                    plugin.getLogger().warning("Не удалось удалить " + dir.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    public Acquire acquire(UUID matchId) {
        Location a = spawns.resolve(SpawnStore.ARENA_A);
        Location b = spawns.resolve(SpawnStore.ARENA_B);
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null
                || !a.getWorld().getName().equals(settings.arena().name())
                || !b.getWorld().getName().equals(settings.arena().name())) {
            return new Acquire(null, Failure.NO_SPAWNS);
        }
        if (!pool.tryAcquire(matchId)) {
            return new Acquire(null, Failure.NO_ARENA);
        }
        String name = prefix() + matchId.toString().substring(0, 8);
        try {
            World template = a.getWorld();
            template.save();
            File source = template.getWorldFolder();
            File target = new File(source.getParentFile(), name);
            boolean generated = worlds.isGenerated(template);
            int[] box = generated || copyRadius <= 0 ? null : new int[] {
                    (int) Math.floor(Math.min(a.getX(), b.getX())) - copyRadius,
                    (int) Math.floor(Math.max(a.getX(), b.getX())) + copyRadius,
                    (int) Math.floor(Math.min(a.getZ(), b.getZ())) - copyRadius,
                    (int) Math.floor(Math.max(a.getZ(), b.getZ())) + copyRadius};
            copyWorld(source.toPath(), target.toPath(), box);
            WorldCreator creator = new WorldCreator(name);
            if (generated) {
                creator.generator(new VoidChunkGenerator());
                creator.generateStructures(false);
            }
            World world = creator.createWorld();
            if (world == null) {
                throw new IllegalStateException("мир не создан");
            }
            worlds.configureArena(world);
            if (borderSize > 0) {
                world.getWorldBorder().setCenter((a.getX() + b.getX()) / 2.0, (a.getZ() + b.getZ()) / 2.0);
                world.getWorldBorder().setSize(borderSize);
            }
            Location spawnA = onGround(in(world, a));
            Location spawnB = onGround(in(world, b));
            if (spawnA == null || spawnB == null) {
                throw new IllegalStateException("под точкой " + (spawnA == null ? "arena-a" : "arena-b")
                        + " нет пола (пустота). Задайте точки заново: /duel admin setspawn");
            }
            world.setSpawnLocation(spawnA);
            if (!generated) {
                placeExitSign(spawnA);
                placeExitSign(spawnB);
            }
            Instance instance = new Instance(matchId, world, spawnA, spawnB);
            active.put(matchId, instance);
            return new Acquire(instance, null);
        } catch (Exception e) {
            plugin.getLogger().severe("Не удалось создать копию арены: " + e.getMessage());
            World leftover = Bukkit.getWorld(name);
            if (leftover != null) {
                Bukkit.unloadWorld(leftover, false);
            }
            try {
                deleteRecursively(new File(a.getWorld().getWorldFolder().getParentFile(), name).toPath());
            } catch (IOException ignored) {
            }
            pool.release(matchId);
            return new Acquire(null, Failure.CREATE_FAILED);
        }
    }

    private static Location in(World world, Location l) {
        return new Location(world, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public static Location onGround(Location l) {
        World world = l.getWorld();
        int x = l.getBlockX();
        int z = l.getBlockZ();
        int bottom = Math.max(world.getMinHeight(), l.getBlockY() - GROUND_SEARCH);
        for (int y = l.getBlockY(); y > bottom; y--) {
            if (world.getBlockAt(x, y - 1, z).isSolid()
                    && world.getBlockAt(x, y, z).isPassable() && world.getBlockAt(x, y + 1, z).isPassable()) {
                Location out = l.clone();
                out.setY(y == l.getBlockY() ? l.getY() : y);
                return out;
            }
        }
        return null;
    }

    private static void placeExitSign(Location spawn) {
        org.bukkit.util.Vector back = spawn.getDirection().setY(0);
        if (back.lengthSquared() < 1e-6) {
            return;
        }
        back.normalize().multiply(-2);
        Location at = onGround(spawn.clone().add(back).add(0, 3, 0));
        if (at == null || !at.getBlock().getType().isAir()) {
            return;
        }
        ExitSignService.place(at, facing(spawn.getYaw()));
    }

    private static org.bukkit.block.BlockFace facing(float yaw) {
        float y = ((yaw % 360) + 360) % 360;
        if (y < 45 || y >= 315) {
            return org.bukkit.block.BlockFace.SOUTH;
        }
        if (y < 135) {
            return org.bukkit.block.BlockFace.WEST;
        }
        return y < 225 ? org.bukkit.block.BlockFace.NORTH : org.bukkit.block.BlockFace.EAST;
    }

    public void release(UUID matchId) {
        Instance instance = active.remove(matchId);
        if (instance == null) {
            return;
        }
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> dispose(instance, true), 20L);
        } else {
            dispose(instance, false);
        }
    }

    private void dispose(Instance instance, boolean mayRetry) {
        World world = instance.world();
        Location fallback = spawns.resolve(SpawnStore.LOBBY);
        if (fallback == null) {
            fallback = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        for (Player p : world.getPlayers()) {
            p.teleport(fallback);
        }
        for (Entity e : world.getEntities()) {
            if (!(e instanceof Player)) {
                e.remove();
            }
        }
        File folder = world.getWorldFolder();
        if (Bukkit.unloadWorld(world, false)) {
            try {
                deleteRecursively(folder.toPath());
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось удалить " + folder.getName() + ": " + e.getMessage());
            }
            pool.release(instance.matchId());
        } else if (mayRetry && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> dispose(instance, false), 100L);
        } else {
            plugin.getLogger().warning("Мир " + world.getName() + " не выгружен, будет удалён при следующем запуске");
            pool.release(instance.matchId());
        }
    }

    public int free() {
        return pool.free();
    }

    static boolean regionNeeded(String fileName, int[] box) {
        Matcher m = REGION_FILE.matcher(fileName);
        if (box == null || !m.matches()) {
            return true;
        }
        int rx = Integer.parseInt(m.group(1));
        int rz = Integer.parseInt(m.group(2));
        return rx * 512 + 511 >= box[0] && rx * 512 <= box[1] && rz * 512 + 511 >= box[2] && rz * 512 <= box[3];
    }

    static void copyWorld(Path source, Path target) throws IOException {
        copyWorld(source, target, null);
    }

    static void copyWorld(Path source, Path target, int[] box) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(source) && SKIP.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                boolean regionFile = file.getParent() != null && file.getParent().getFileName() != null
                        && REGION_DIRS.contains(file.getParent().getFileName().toString());
                if (regionFile && !regionNeeded(fileName, box)) {
                    return FileVisitResult.CONTINUE;
                }
                if (!SKIP.contains(fileName) && !source.relativize(file).equals(PAPER_METADATA)) {
                    Files.copy(file, target.resolve(source.relativize(file).toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
