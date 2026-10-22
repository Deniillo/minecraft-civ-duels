package mine.deniilo.duels.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.IntSupplier;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.rating.RatingRepository;
import mine.deniilo.duels.world.WorldService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class SidebarService {

    private static final int LINES = 6;

    private static final class Board {
        private final Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        private final Team[] teams = new Team[LINES];
    }

    private final Plugin plugin;
    private final Messages messages;
    private final WorldService worlds;
    private final RatingRepository ratings;
    private final IntSupplier searching;
    private final Map<UUID, Board> boards = new HashMap<>();

    public SidebarService(Plugin plugin, Messages messages, WorldService worlds, RatingRepository ratings,
                          IntSupplier searching) {
        this.plugin = plugin;
        this.messages = messages;
        this.worlds = worlds;
        this.ratings = ratings;
        this.searching = searching;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void forget(UUID id) {
        boards.remove(id);
    }

    private void tick() {
        int inDuels = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (worlds.isDuelsWorld(p.getWorld())) {
                inDuels++;
            }
        }
        int searchingNow = searching.getAsInt();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (worlds.isLobbyWorld(p.getWorld())) {
                update(p, inDuels, searchingNow);
            } else if (boards.remove(p.getUniqueId()) != null) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private void update(Player player, int inDuels, int searchingNow) {
        Board board = boards.computeIfAbsent(player.getUniqueId(), id -> create(player));
        if (player.getScoreboard() != board.scoreboard) {
            player.setScoreboard(board.scoreboard);
        }
        String best = "—";
        try {
            OptionalInt r = ratings.ratedRating(player.getUniqueId());
            if (r.isPresent()) {
                best = String.valueOf(r.getAsInt());
            }
        } catch (SQLException e) {
            best = "?";
        }
        Component[] lines = {
                Component.empty(),
                messages.raw("sidebar-player", "player", player.getName()),
                messages.raw("sidebar-best", "rating", best),
                Component.empty(),
                messages.raw("sidebar-online", "count", String.valueOf(inDuels)),
                messages.raw("sidebar-searching", "count", String.valueOf(searchingNow)),
        };
        for (int i = 0; i < LINES; i++) {
            board.teams[i].prefix(lines[i]);
        }
    }

    private Board create(Player player) {
        Board board = new Board();
        Objective objective = board.scoreboard.registerNewObjective("duels", Criteria.DUMMY, messages.raw("sidebar-title"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        for (int i = 0; i < LINES; i++) {
            String entry = "§" + Integer.toHexString(i) + "§r";
            Team team = board.scoreboard.registerNewTeam("l" + i);
            team.addEntry(entry);
            board.teams[i] = team;
            objective.getScore(entry).setScore(LINES - i);
        }
        return board;
    }
}
