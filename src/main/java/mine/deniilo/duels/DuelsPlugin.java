package mine.deniilo.duels;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import mine.deniilo.duels.combat.CombatAdminCommands;
import mine.deniilo.duels.combat.CombatDebug;
import mine.deniilo.duels.combat.CombatRegistry;
import mine.deniilo.duels.combat.DuelCombatEngine;
import mine.deniilo.duels.combat.DummyService;
import mine.deniilo.duels.item.ItemFactory;
import mine.deniilo.duels.match.MatchAdminCommands;
import mine.deniilo.duels.match.MatchListener;
import mine.deniilo.duels.match.MatchManager;
import mine.deniilo.duels.match.MatchSettings;
import mine.deniilo.duels.match.RestoreStore;
import mine.deniilo.duels.item.ItemKeys;
import mine.deniilo.duels.item.ItemReader;
import mine.deniilo.duels.kit.KitAdminCommands;
import mine.deniilo.duels.kit.KitService;
import mine.deniilo.duels.menu.DuelMenu;
import mine.deniilo.duels.npc.NpcListener;
import mine.deniilo.duels.npc.NpcService;
import mine.deniilo.duels.queue.QueueCoordinator;
import mine.deniilo.duels.queue.QueueListener;
import mine.deniilo.duels.queue.QueueService;
import mine.deniilo.duels.queue.QueueSettings;
import mine.deniilo.duels.rating.EloSettings;
import mine.deniilo.duels.rating.RatingRepository;
import mine.deniilo.duels.rating.RatingService;
import mine.deniilo.duels.rating.StatsCommands;
import mine.deniilo.duels.profile.ItemCatalog;
import mine.deniilo.duels.profile.KitCatalog;
import org.bukkit.configuration.file.YamlConfiguration;
import mine.deniilo.duels.command.DuelCommand;
import mine.deniilo.duels.config.DuelsSettings;
import mine.deniilo.duels.hub.HubAdapter;
import mine.deniilo.duels.hub.LocalHubAdapter;
import mine.deniilo.duels.listener.LobbyListener;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.world.SpawnStore;
import mine.deniilo.duels.world.WorldService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DuelsPlugin extends JavaPlugin {

    private MatchManager matchManager;
    private RatingRepository ratings;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        DuelsSettings settings;
        try {
            settings = DuelsSettings.from(getConfig());
        } catch (IllegalArgumentException e) {
            getLogger().severe("Ошибка в config.yml: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Messages messages = Messages.load(this);

        WorldService worlds = new WorldService(this, settings);
        worlds.loadAll();

        SpawnStore spawns = new SpawnStore(this, settings);
        HubAdapter hub = new LocalHubAdapter(spawns);

        ItemCatalog items;
        try (Reader reader = new InputStreamReader(
                Objects.requireNonNull(getResource("items.yml"), "items.yml отсутствует в JAR"), StandardCharsets.UTF_8)) {
            items = ItemCatalog.from(YamlConfiguration.loadConfiguration(reader));
        } catch (Exception e) {
            getLogger().severe("Ошибка в items.yml: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        File kitsFile = new File(getDataFolder(), "kits.yml");
        if (!kitsFile.exists()) {
            saveResource("kits.yml", false);
        } else {
            int shipped;
            try (Reader kr = new InputStreamReader(
                    Objects.requireNonNull(getResource("kits.yml")), StandardCharsets.UTF_8)) {
                shipped = YamlConfiguration.loadConfiguration(kr).getInt("version", 1);
            } catch (Exception e) {
                shipped = 0;
            }
            int installed = YamlConfiguration.loadConfiguration(kitsFile).getInt("version", 0);
            if (installed < shipped) {
                try {
                    java.nio.file.Files.move(kitsFile.toPath(), new File(getDataFolder(), "kits.yml.bak").toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    saveResource("kits.yml", false);
                    getLogger().warning("kits.yml обновлён до версии " + shipped
                            + ", прежний файл сохранён как kits.yml.bak");
                } catch (java.io.IOException e) {
                    getLogger().severe("Не удалось обновить kits.yml: " + e.getMessage());
                }
            }
        }
        KitCatalog kits = KitCatalog.from(YamlConfiguration.loadConfiguration(kitsFile), items);
        kits.errors().forEach(err -> getLogger().warning("kits.yml: " + err));
        getLogger().info("Загружено предметов: брони " + items.allArmor().size()
                + ", оружия " + items.allWeapons().size() + "; наборов: " + kits.ids().size());

        ItemKeys keys = new ItemKeys(this);
        ItemReader reader = new ItemReader(keys);
        KitService kitService = new KitService(this, items, new ItemFactory(keys), reader);
        KitAdminCommands kitCommands = new KitAdminCommands(messages, kits, kitService);

        CombatRegistry registry = new CombatRegistry();
        CombatDebug debug = new CombatDebug(new File(getDataFolder(), "combat-debug.log").toPath(),
                getConfig().getBoolean("debug.combat-file-log", false), getLogger());
        DummyService dummies = new DummyService(this, kitService, registry);
        CombatAdminCommands combatCommands = new CombatAdminCommands(
                messages, items, kits, kitService, registry, debug, dummies);
        getServer().getPluginManager().registerEvents(new DuelCombatEngine(
                this, items, reader, kitService, registry, debug, dummies, messages), this);
        java.util.Set<String> weaponIds = items.allWeapons().stream()
                .map(mine.deniilo.duels.profile.WeaponProfile::id)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));

        MatchSettings matchSettings;
        try {
            matchSettings = MatchSettings.from(getConfig());
        } catch (IllegalArgumentException e) {
            getLogger().severe("Ошибка в config.yml: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        int maxArenas = getConfig().getInt("arenas.max-concurrent", 4);
        int borderSize = getConfig().getInt("arenas.border-size", 0);
        int copyRadius = getConfig().getInt("arenas.copy-radius", 192);
        if (maxArenas < 1 || maxArenas > 32 || borderSize < 0) {
            getLogger().severe("Ошибка в config.yml: arenas.max-concurrent от 1 до 32, arenas.border-size не меньше 0");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        mine.deniilo.duels.world.ArenaInstanceService arenaInstances = new mine.deniilo.duels.world.ArenaInstanceService(
                this, settings, worlds, spawns, maxArenas, borderSize, copyRadius);
        arenaInstances.cleanupLeftovers();
        getServer().getPluginManager().registerEvents(new mine.deniilo.duels.world.ProtectionListener(worlds), this);
        matchManager = new MatchManager(this, matchSettings, kitService, registry, dummies, spawns,
                new RestoreStore(this), messages, arenaInstances);
        MatchAdminCommands matchCommands = new MatchAdminCommands(messages, kits, matchManager);
        getServer().getPluginManager().registerEvents(
                new MatchListener(matchManager, reader, items, spawns, messages, settings.arena().name()), this);

        EloSettings eloSettings;
        QueueSettings queueSettings;
        try {
            eloSettings = EloSettings.from(getConfig());
            queueSettings = QueueSettings.from(getConfig());
        } catch (IllegalArgumentException e) {
            getLogger().severe("Ошибка в config.yml: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            getDataFolder().mkdirs();
            ratings = new RatingRepository(new File(getDataFolder(), "duels.db").toPath(), eloSettings);
        } catch (java.sql.SQLException e) {
            getLogger().severe("Не удалось открыть базу duels.db: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        RatingService ratingService = new RatingService(ratings, messages, getLogger());

        QueueCoordinator queue = new QueueCoordinator(this, new QueueService(queueSettings), ratings, matchManager,
                kits, messages);
        queue.start();
        int roomCount = getConfig().getInt("training.rooms", 12);
        if (roomCount < 1 || roomCount > 36) {
            getLogger().severe("Ошибка в config.yml: training.rooms от 1 до 36");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        mine.deniilo.duels.training.TrainingRooms trainingRooms = new mine.deniilo.duels.training.TrainingRooms(roomCount);
        mine.deniilo.duels.training.TrainingCoordinator training = new mine.deniilo.duels.training.TrainingCoordinator(
                trainingRooms, matchManager, queue, kits, messages);
        matchManager.setSink((result, info) -> {
            ratingService.onFinished(result, info);
            training.onMatchFinished(result);
        });
        mine.deniilo.duels.scoreboard.SidebarService sidebar = new mine.deniilo.duels.scoreboard.SidebarService(
                this, messages, worlds, ratings, () -> queue.searching() + trainingRooms.waitingCount());
        sidebar.start();
        getServer().getPluginManager().registerEvents(new QueueListener(queue, training, sidebar), this);
        StatsCommands statsCommands = new StatsCommands(messages, ratings);
        DuelMenu menu = new DuelMenu(this, messages, kits, queue, training, matchManager, spawns, hub, ratings,
                statsCommands);
        menu.startRefresh();
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(
                new mine.deniilo.duels.world.ExitSignService(matchManager, spawns, messages), this);
        mine.deniilo.duels.board.LeaderboardService board = new mine.deniilo.duels.board.LeaderboardService(
                this, messages, ratings, spawns, getConfig().getInt("leaderboard.size", 10),
                Math.max(5, getConfig().getInt("leaderboard.update-seconds", 30)));
        board.start(getConfig().getBoolean("leaderboard.auto-create", true));
        getServer().getPluginManager().registerEvents(board, this);
        NpcService npcs = new NpcService(this, messages);
        getServer().getPluginManager().registerEvents(new NpcListener(npcs, menu), this);
        if (getConfig().getBoolean("npc.auto-create", true)) {
            for (String key : new String[] {SpawnStore.HUB, SpawnStore.LOBBY}) {
                org.bukkit.Location point = spawns.resolve(key);
                if (point != null) {
                    npcs.ensureLater(point);
                }
            }
        }

        PluginCommand command = Objects.requireNonNull(getCommand("duel"), "команда duel не описана в plugin.yml");
        DuelCommand executor = new DuelCommand(messages, spawns, hub, kitCommands, combatCommands, weaponIds,
                matchCommands, matchManager, menu, queue, statsCommands, npcs, kits, board);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getServer().getPluginManager().registerEvents(new LobbyListener(settings, worlds, spawns, hub), this);

        getLogger().info("Duels enabled");
    }

    @Override
    public void onDisable() {
        if (matchManager != null) {
            matchManager.shutdown();
        }
        if (ratings != null) {
            ratings.close();
        }
        getLogger().info("Duels disabled");
    }
}
