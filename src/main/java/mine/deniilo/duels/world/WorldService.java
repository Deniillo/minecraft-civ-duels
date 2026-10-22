package mine.deniilo.duels.world;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import mine.deniilo.duels.config.DuelsSettings;
import mine.deniilo.duels.config.DuelsSettings.WorldSettings;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

public final class WorldService {

    public static final int PLATFORM_Y = 64;
    private static final int PLATFORM_RADIUS = 12;
    private static final int WALL_HEIGHT = 12;
    private static final String MARKER = "duels-generated";

    private final JavaPlugin plugin;
    private final DuelsSettings settings;

    public WorldService(JavaPlugin plugin, DuelsSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void loadAll() {
        load(settings.hub(), true);
        load(settings.lobby(), true);
        load(settings.arena(), false);
    }

    public boolean isLobbyWorld(World world) {
        String n = world.getName();
        return n.equals(settings.hub().name()) || n.equals(settings.lobby().name());
    }

    public boolean isHubWorld(World world) {
        return world.getName().equals(settings.hub().name());
    }

    public boolean isDuelsWorld(World world) {
        return world.getName().equals(settings.lobby().name()) || isArenaWorld(world);
    }

    public boolean isGenerated(World world) {
        return new File(Bukkit.getWorldContainer(), world.getName() + File.separator + MARKER).isFile()
                || new File(world.getWorldFolder(), MARKER).isFile();
    }

    public boolean isArenaWorld(World world) {
        String n = world.getName();
        String arena = settings.arena().name();
        return n.equals(arena) || n.startsWith(arena + "_");
    }

    public void configureArena(World world) {
        configure(world, false);
        world.setTime(6000);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
    }

    private World load(WorldSettings ws, boolean peaceful) {
        World existing = Bukkit.getWorld(ws.name());
        if (existing != null) {
            return existing;
        }

        File dir = new File(Bukkit.getWorldContainer(), ws.name());
        boolean exists = worldExists(ws.name(), dir);
        boolean hasMarker = new File(dir, MARKER).isFile();
        boolean generated = hasMarker || (!exists && ws.generateIfMissing());

        if (!generated && !exists) {
            plugin.getLogger().severe("Мир '" + ws.name() + "' не найден и generate-if-missing выключен");
            return null;
        }

        WorldCreator creator = new WorldCreator(ws.name());
        if (generated) {
            try {
                Files.createDirectories(dir.toPath());
                File marker = new File(dir, MARKER);
                if (!marker.exists()) {
                    Files.writeString(marker.toPath(), "Мир создан плагином Duels\n");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать папку мира " + dir + ": " + e.getMessage());
                return null;
            }
            creator.generator(new VoidChunkGenerator());
            creator.generateStructures(false);
        }

        World world = creator.createWorld();
        if (world == null) {
            plugin.getLogger().severe("Не удалось загрузить мир '" + ws.name() + "'");
            return null;
        }

        if (generated) {
            buildPlatformIfMissing(world, ws.platformMaterial());
            buildWallsIfMissing(world);
            if (!peaceful) {
                placeExitSignIfMissing(world);
            }
            world.setSpawnLocation(0, PLATFORM_Y + 1, 0);
            world.setTime(6000);
            plugin.getLogger().info("Создан пустой мир '" + ws.name() + "' с платформой");
        } else {
            plugin.getLogger().info("Загружена готовая карта '" + ws.name() + "'");
        }
        configure(world, peaceful);
        return world;
    }

    /**
     * Мир уже есть на диске. В Paper 26 миры лежат в {@code <мир-по-умолчанию>/dimensions/minecraft/<имя>},
     * а папка из корня сервера (старый формат) при первой загрузке переносится туда.
     * Поэтому проверяем оба места.
     */
    private boolean worldExists(String name, File legacyDir) {
        if (new File(legacyDir, "level.dat").isFile()) {
            return true;
        }
        List<File> roots = new ArrayList<>();
        if (!Bukkit.getWorlds().isEmpty()) {
            roots.add(Bukkit.getWorlds().get(0).getWorldFolder());
        }
        roots.add(new File(Bukkit.getWorldContainer(), "world"));
        for (File root : roots) {
            File dimension = new File(root, "dimensions" + File.separator + "minecraft" + File.separator + name);
            if (new File(dimension, "region").isDirectory() || new File(dimension, "data").isDirectory()) {
                return true;
            }
        }
        return false;
    }

    private void configure(World world, boolean peaceful) {
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setStorm(false);
        world.setDifficulty(peaceful ? Difficulty.PEACEFUL : Difficulty.NORMAL);
        if (!peaceful) {
            world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        }
    }

    private void buildPlatformIfMissing(World world, String materialName) {
        if (!world.getBlockAt(0, PLATFORM_Y, 0).getType().isAir()) {
            return;
        }
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isBlock()) {
            plugin.getLogger().warning("Неизвестный platform-material '" + materialName + "', взят STONE_BRICKS");
            material = Material.STONE_BRICKS;
        }
        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                world.getBlockAt(dx, PLATFORM_Y, dz).setType(material, false);
            }
        }
    }

    private void buildWallsIfMissing(World world) {
        if (world.getBlockAt(PLATFORM_RADIUS, PLATFORM_Y + 2, 0).getType() == Material.BARRIER) {
            return;
        }
        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != PLATFORM_RADIUS) {
                    continue;
                }
                for (int y = PLATFORM_Y + 1; y <= PLATFORM_Y + WALL_HEIGHT; y++) {
                    world.getBlockAt(dx, y, dz).setType(Material.BARRIER, false);
                }
            }
        }
        plugin.getLogger().info("Барьерные стены построены в мире " + world.getName() + "");
    }

    private void placeExitSignIfMissing(World world) {
        org.bukkit.block.Block block = world.getBlockAt(0, PLATFORM_Y + 1, PLATFORM_RADIUS - 2);
        if (block.getState() instanceof org.bukkit.block.Sign) {
            return;
        }
        ExitSignService.place(block.getLocation(), org.bukkit.block.BlockFace.NORTH);
    }
}
