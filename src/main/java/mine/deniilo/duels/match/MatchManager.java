package mine.deniilo.duels.match;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import mine.deniilo.duels.combat.CombatRegistry;
import mine.deniilo.duels.combat.DummyService;
import mine.deniilo.duels.kit.KitService;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.profile.Kit;
import mine.deniilo.duels.world.ArenaInstanceService;
import mine.deniilo.duels.world.SpawnStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class MatchManager {

    public enum StartError {
        BUSY, PENDING_RESTORE, NO_ARENA, NO_SPAWNS, SAME_PLAYER, STORE_FAILED, ARENA_FAILED
    }

    public static String errorKey(StartError error) {
        return switch (error) {
            case BUSY -> "match-error-busy";
            case PENDING_RESTORE -> "match-error-pending";
            case NO_ARENA -> "match-error-arena";
            case NO_SPAWNS -> "match-error-spawns";
            case SAME_PLAYER -> "match-error-same";
            case STORE_FAILED -> "match-error-store";
            case ARENA_FAILED -> "match-error-arena-failed";
        };
    }

    private final Plugin plugin;
    private final MatchSettings settings;
    private final KitService kits;
    private final CombatRegistry registry;
    private final DummyService dummies;
    private final SpawnStore spawns;
    private final RestoreStore store;
    private final Messages messages;
    private final ArenaInstanceService arenas;
    private final Map<UUID, DuelMatch> byParticipant = new HashMap<>();
    private final Map<UUID, DuelMatch> byId = new HashMap<>();
    private MatchResultSink sink = MatchResultSink.NOOP;

    public MatchManager(Plugin plugin, MatchSettings settings, KitService kits, CombatRegistry registry,
                        DummyService dummies, SpawnStore spawns, RestoreStore store, Messages messages,
                        ArenaInstanceService arenas) {
        this.arenas = arenas;
        this.plugin = plugin;
        this.settings = settings;
        this.kits = kits;
        this.registry = registry;
        this.dummies = dummies;
        this.spawns = spawns;
        this.store = store;
        this.messages = messages;
    }

    Plugin plugin() {
        return plugin;
    }

    MatchSettings settings() {
        return settings;
    }

    KitService kits() {
        return kits;
    }

    CombatRegistry registry() {
        return registry;
    }

    Messages messages() {
        return messages;
    }

    DummyService dummies() {
        return dummies;
    }

    MatchResultSink sink() {
        return sink;
    }

    public void setSink(MatchResultSink sink) {
        this.sink = sink == null ? MatchResultSink.NOOP : sink;
    }

    public RestoreStore store() {
        return store;
    }

    public Optional<DuelMatch> matchOf(UUID participant) {
        return Optional.ofNullable(byParticipant.get(participant));
    }

    public int activeMatches() {
        return byId.size();
    }

    String nameOf(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) {
            return "Мишень";
        }
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? "Мишень" : name;
    }

    public Optional<StartError> start(Player first, Player second, Kit kit, boolean ranked) {
        if (first.getUniqueId().equals(second.getUniqueId())) {
            return Optional.of(StartError.SAME_PLAYER);
        }
        return launch(first, second, kit, ranked);
    }

    public Optional<StartError> startSolo(Player player, Kit kit) {
        return launch(player, null, kit, false);
    }

    private Optional<StartError> launch(Player first, Player second, Kit kit, boolean ranked) {
        if (byParticipant.containsKey(first.getUniqueId())
                || (second != null && byParticipant.containsKey(second.getUniqueId()))) {
            return Optional.of(StartError.BUSY);
        }
        if (store.has(first.getUniqueId()) || (second != null && store.has(second.getUniqueId()))) {
            return Optional.of(StartError.PENDING_RESTORE);
        }
        UUID matchId = UUID.randomUUID();
        ArenaInstanceService.Acquire acquired = arenas.acquire(matchId);
        if (!acquired.ok()) {
            return Optional.of(switch (acquired.failure()) {
                case NO_SPAWNS -> StartError.NO_SPAWNS;
                case NO_ARENA -> StartError.NO_ARENA;
                case CREATE_FAILED -> StartError.ARENA_FAILED;
            });
        }
        Location spawnA = acquired.instance().spawnA();
        Location spawnB = acquired.instance().spawnB();

        // Запись восстановления подтверждается до любой мутации игрока.
        try {
            store.save(first);
            if (second != null) {
                try {
                    store.save(second);
                } catch (IOException e) {
                    store.discard(first.getUniqueId());
                    throw e;
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось записать состояние игрока перед матчем: " + e.getMessage());
            arenas.release(matchId);
            return Optional.of(StartError.STORE_FAILED);
        }

        Entity bot = null;
        UUID secondId;
        if (second != null) {
            secondId = second.getUniqueId();
        } else {
            bot = dummies.spawnSparring(spawnB, kit, matchId);
            secondId = bot.getUniqueId();
        }

        MatchState state = new MatchState(matchId, first.getUniqueId(), secondId, ranked);
        DuelMatch match = new DuelMatch(this, state, kit, first.getUniqueId(), secondId, bot, spawnA, spawnB);
        byId.put(matchId, match);
        byParticipant.put(first.getUniqueId(), match);
        byParticipant.put(secondId, match);
        registry.join(first.getUniqueId(), matchId);
        registry.join(secondId, matchId);
        match.prepare();
        return Optional.empty();
    }

    public boolean leave(Player player) {
        DuelMatch match = byParticipant.get(player.getUniqueId());
        if (match == null) {
            return false;
        }
        match.onLeave(player.getUniqueId());
        return true;
    }

    public int cancelAll(EndReason reason) {
        int n = 0;
        for (DuelMatch match : java.util.List.copyOf(byId.values())) {
            match.cancel(reason);
            n++;
        }
        return n;
    }

    public void shutdown() {
        for (DuelMatch match : java.util.List.copyOf(byId.values())) {
            match.cancelNow(EndReason.SHUTDOWN);
        }
    }

    void restorePlayer(UUID id, boolean immediate) {
        byParticipant.remove(id);
        registry.leave(id);
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) {
            return;
        }
        Location lobby = spawns.resolve(SpawnStore.LOBBY);
        if (p.isDead()) {
            p.spigot().respawn();
        }
        if (immediate) {
            if (store.restore(p, lobby)) {
                messages.send(p, "match-restored");
            }
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && store.restore(p, lobby)) {
                messages.send(p, "match-restored");
            }
        });
    }

    void closed(DuelMatch match) {
        byId.remove(match.id());
        arenas.release(match.id());
    }

    public boolean restoreOnJoin(Player player) {
        if (byParticipant.containsKey(player.getUniqueId()) || !store.has(player.getUniqueId())) {
            return false;
        }
        return store.restore(player, spawns.resolve(SpawnStore.LOBBY));
    }
}
