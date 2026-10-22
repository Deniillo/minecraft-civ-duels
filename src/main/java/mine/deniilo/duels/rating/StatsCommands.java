package mine.deniilo.duels.rating;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mine.deniilo.duels.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StatsCommands {

    public static final int TOP_SIZE = 10;

    private final Messages messages;
    private final RatingRepository repo;

    public StatsCommands(Messages messages, RatingRepository repo) {
        this.messages = messages;
        this.repo = repo;
    }

    public void stats(CommandSender sender, String[] args) {
        try {
            UUID target;
            String name;
            if (args.length >= 2) {
                Optional<UUID> found = repo.findByName(args[1]);
                if (found.isEmpty()) {
                    messages.send(sender, "stats-unknown-player", "player", args[1]);
                    return;
                }
                target = found.get();
                name = args[1];
            } else if (sender instanceof Player p) {
                target = p.getUniqueId();
                name = p.getName();
            } else {
                messages.send(sender, "players-only");
                return;
            }
            sendStats(sender, target, name);
        } catch (SQLException e) {
            messages.send(sender, "stats-error");
        }
    }

    public void sendStats(CommandSender sender, UUID target, String name) throws SQLException {
        Optional<RatingRepository.Stat> stats = repo.stats(target);
        if (stats.isEmpty()) {
            messages.send(sender, "stats-empty", "player", name);
            return;
        }
        RatingRepository.Stat s = stats.get();
        messages.send(sender, "stats-line", "player", name, "rating", String.valueOf(s.rating()),
                "wins", String.valueOf(s.wins()), "losses", String.valueOf(s.losses()),
                "draws", String.valueOf(s.draws()));
    }

    public void top(CommandSender sender, String[] args) {
        try {
            List<RatingRepository.TopEntry> top = repo.top(TOP_SIZE);
            messages.send(sender, "top-header");
            if (top.isEmpty()) {
                messages.send(sender, "top-empty");
                return;
            }
            int place = 1;
            for (RatingRepository.TopEntry e : top) {
                messages.send(sender, "top-line", "place", String.valueOf(place++), "player", e.name(),
                        "rating", String.valueOf(e.rating()), "wins", String.valueOf(e.wins()),
                        "losses", String.valueOf(e.losses()));
            }
        } catch (SQLException e) {
            messages.send(sender, "stats-error");
        }
    }

    public void adminRating(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "rating-usage");
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online == null) {
            messages.send(sender, "rating-usage");
            return;
        }
        try {
            if (args.length >= 3) {
                int value = Integer.parseInt(args[2]);
                if (value < 0) {
                    messages.send(sender, "rating-usage");
                    return;
                }
                repo.setRating(online.getUniqueId(), online.getName(), value);
            }
            messages.send(sender, "rating-show", "player", online.getName(),
                    "rating", String.valueOf(repo.rating(online.getUniqueId())));
        } catch (NumberFormatException e) {
            messages.send(sender, "rating-usage");
        } catch (SQLException e) {
            messages.send(sender, "stats-error");
        }
    }
}
