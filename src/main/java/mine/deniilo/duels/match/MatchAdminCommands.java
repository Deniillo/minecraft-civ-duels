package mine.deniilo.duels.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.profile.Kit;
import mine.deniilo.duels.profile.KitCatalog;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MatchAdminCommands {

    public static final Set<String> NAMES = Set.of("match");

    private final Messages messages;
    private final KitCatalog kits;
    private final MatchManager manager;

    public MatchAdminCommands(Messages messages, KitCatalog kits, MatchManager manager) {
        this.messages = messages;
        this.kits = kits;
        this.manager = manager;
    }

    public void handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "match-usage");
            return;
        }
        String second = args[1].toLowerCase(Locale.ROOT);
        if (second.equals("cancel")) {
            messages.send(sender, "match-admin-cancelled", "count", String.valueOf(manager.cancelAll(EndReason.ADMIN)));
            return;
        }
        if (second.equals("solo")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "players-only");
                return;
            }
            Optional<Kit> kit = args.length >= 3 ? kits.get(args[2].toLowerCase(Locale.ROOT)) : Optional.empty();
            if (kit.isEmpty()) {
                messages.send(sender, "kit-unknown", "kit", args.length >= 3 ? args[2] : "", "kits", String.join(", ", kits.ids()));
                return;
            }
            report(sender, manager.startSolo(player, kit.get()));
            return;
        }
        if (args.length < 4) {
            messages.send(sender, "match-usage");
            return;
        }
        Player p1 = Bukkit.getPlayerExact(args[1]);
        Player p2 = Bukkit.getPlayerExact(args[2]);
        if (p1 == null || p2 == null) {
            messages.send(sender, "match-offline");
            return;
        }
        Optional<Kit> kit = kits.get(args[3].toLowerCase(Locale.ROOT));
        if (kit.isEmpty()) {
            messages.send(sender, "kit-unknown", "kit", args[3], "kits", String.join(", ", kits.ids()));
            return;
        }
        boolean ranked = args.length >= 5 && args[4].equalsIgnoreCase("ranked");
        report(sender, manager.start(p1, p2, kit.get(), ranked));
    }

    private void report(CommandSender sender, Optional<MatchManager.StartError> error) {
        if (error.isEmpty()) {
            messages.send(sender, "match-started");
            return;
        }
        String key = MatchManager.errorKey(error.get());
        messages.send(sender, key);
    }

    public List<String> complete(String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 2) {
            out.add("solo");
            out.add("cancel");
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 3 && args[1].equalsIgnoreCase("solo")) {
            out.addAll(kits.ids());
        } else if (args.length == 3) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 4) {
            out.addAll(kits.ids());
        } else if (args.length == 5) {
            out.add("ranked");
        }
        return out;
    }
}
