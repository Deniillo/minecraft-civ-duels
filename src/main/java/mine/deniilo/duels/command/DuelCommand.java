package mine.deniilo.duels.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mine.deniilo.duels.combat.CombatAdminCommands;
import mine.deniilo.duels.hub.HubAdapter;
import mine.deniilo.duels.kit.KitAdminCommands;
import mine.deniilo.duels.match.MatchAdminCommands;
import mine.deniilo.duels.menu.DuelMenu;
import mine.deniilo.duels.npc.NpcService;
import mine.deniilo.duels.profile.Kit;
import mine.deniilo.duels.profile.KitCatalog;
import mine.deniilo.duels.queue.QueueCoordinator;
import mine.deniilo.duels.rating.StatsCommands;
import mine.deniilo.duels.match.MatchManager;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.world.SpawnStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class DuelCommand implements TabExecutor {

    private static final String ADMIN_PERMISSION = "duels.admin";

    private final Messages messages;
    private final SpawnStore spawns;
    private final HubAdapter hub;
    private final KitAdminCommands kitCommands;
    private final CombatAdminCommands combatCommands;
    private final MatchAdminCommands matchCommands;
    private final MatchManager matches;
    private final java.util.Set<String> weaponIds;
    private final DuelMenu menu;
    private final QueueCoordinator queue;
    private final StatsCommands stats;
    private final NpcService npcs;
    private final KitCatalog kits;
    private final mine.deniilo.duels.board.LeaderboardService board;

    public DuelCommand(Messages messages, SpawnStore spawns, HubAdapter hub, KitAdminCommands kitCommands,
                       CombatAdminCommands combatCommands, java.util.Set<String> weaponIds,
                       MatchAdminCommands matchCommands, MatchManager matches, DuelMenu menu,
                       QueueCoordinator queue, StatsCommands stats, NpcService npcs, KitCatalog kits,
                       mine.deniilo.duels.board.LeaderboardService board) {
        this.board = board;
        this.menu = menu;
        this.queue = queue;
        this.stats = stats;
        this.npcs = npcs;
        this.kits = kits;
        this.matchCommands = matchCommands;
        this.matches = matches;
        this.kitCommands = kitCommands;
        this.combatCommands = combatCommands;
        this.weaponIds = weaponIds;
        this.messages = messages;
        this.spawns = spawns;
        this.hub = hub;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> {
                if (sender instanceof Player p) {
                    menu.open(p);
                } else {
                    messages.send(sender, "players-only");
                }
            }
            case "queue" -> queueCommand(sender, args);
            case "stats" -> stats.stats(sender, args);
            case "top" -> stats.top(sender, args);
            case "lobby" -> toLobby(sender);
            case "hub" -> toHub(sender);
            case "leave" -> leave(sender);
            case "admin" -> admin(sender, args);
            default -> messages.send(sender, "unknown-subcommand");
        }
        return true;
    }

    private void toLobby(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        Location lobby = spawns.resolve(SpawnStore.LOBBY);
        if (lobby == null) {
            messages.send(sender, "world-missing", "world", "lobby");
            return;
        }
        player.teleport(lobby);
        messages.send(sender, "to-lobby");
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        menu.leave(player);
    }

    private void queueCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "queue-usage", "kits", String.join(", ", kits.ids()));
            return;
        }
        java.util.Optional<Kit> kit = kits.get(args[1].toLowerCase(Locale.ROOT));
        if (kit.isEmpty()) {
            messages.send(sender, "kit-unknown", "kit", args[1], "kits", String.join(", ", kits.ids()));
            return;
        }
        queue.enqueue(player, kit.get(), true);
    }

    private void npcCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "create" -> {
                npcs.spawn(player.getLocation());
                messages.send(sender, "npc-created");
            }
            case "remove" -> messages.send(sender, "npc-removed",
                    "count", String.valueOf(npcs.removeAll(player.getWorld())));
            default -> messages.send(sender, "npc-usage");
        }
    }

    private void toHub(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        if (hub.sendToHub(player)) {
            messages.send(sender, "to-hub");
        } else {
            messages.send(sender, "world-missing", "world", "hub");
        }
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messages.send(sender, "no-permission");
            return;
        }
        String keys = String.join(", ", SpawnStore.KEYS);
        if (args.length < 2) {
            messages.send(sender, "admin-usage", "keys", keys);
            return;
        }
        String first = args[1].toLowerCase(Locale.ROOT);
        if (first.equals("give") || first.equals("clear") || first.equals("info")) {
            kitCommands.handle(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (first.equals("exitsign")) {
            if (sender instanceof Player p) {
                mine.deniilo.duels.world.ExitSignService.place(p.getLocation().getBlock().getLocation(),
                        p.getFacing().getOppositeFace());
                messages.send(sender, "exitsign-created");
            } else {
                messages.send(sender, "players-only");
            }
            return;
        }
        if (first.equals("board")) {
            if (!(sender instanceof Player p)) {
                messages.send(sender, "players-only");
                return;
            }
            String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
            Location hub = spawns.resolve(SpawnStore.HUB);
            if (action.equals("create") && hub != null && p.getWorld().equals(hub.getWorld())) {
                messages.send(sender, "board-not-in-hub");
            } else if (action.equals("create")) {
                board.spawn(p.getLocation().add(0, 1.5, 0));
                board.refresh();
                messages.send(sender, "board-created");
            } else if (action.equals("remove")) {
                messages.send(sender, "board-removed", "count", String.valueOf(board.removeAll(p.getWorld())));
            } else {
                messages.send(sender, "board-usage");
            }
            return;
        }
        if (first.equals("npc")) {
            npcCommand(sender, args);
            return;
        }
        if (first.equals("rating")) {
            stats.adminRating(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (MatchAdminCommands.NAMES.contains(first)) {
            matchCommands.handle(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (CombatAdminCommands.NAMES.contains(first)) {
            combatCommands.handle(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length < 3) {
            messages.send(sender, "admin-usage", "keys", keys);
            return;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String key = args[2].toLowerCase(Locale.ROOT);
        if (!spawns.isKnownKey(key)) {
            messages.send(sender, "spawn-unknown", "key", key, "keys", keys);
            return;
        }
        switch (action) {
            case "setspawn" -> {
                Location l = player.getLocation();
                try {
                    spawns.set(key, l);
                } catch (IOException e) {
                    sender.sendMessage("Не удалось сохранить spawns.yml: " + e.getMessage());
                    return;
                }
                String where = String.format(Locale.ROOT, "%s %.1f %.1f %.1f",
                        l.getWorld().getName(), l.getX(), l.getY(), l.getZ());
                messages.send(sender, "spawn-set", "key", key, "where", where);
                if (mine.deniilo.duels.world.ArenaInstanceService.onGround(l) == null) {
                    messages.send(sender, "spawn-no-ground");
                }
            }
            case "tp" -> {
                Location target = spawns.resolve(key);
                if (target == null) {
                    messages.send(sender, "world-missing", "world", key);
                    return;
                }
                player.teleport(target);
                messages.send(sender, "spawn-teleported", "key", key);
            }
            default -> messages.send(sender, "admin-usage", "keys", keys);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("menu");
            out.add("queue");
            out.add("stats");
            out.add("top");
            out.add("lobby");
            out.add("hub");
            out.add("leave");
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                out.add("admin");
            }
        } else if (args[0].equalsIgnoreCase("queue")) {
            if (args.length == 2) {
                out.addAll(kits.ids());
            }
        } else if (sender.hasPermission(ADMIN_PERMISSION) && args[0].equalsIgnoreCase("admin")
                && args.length >= 3 && (args[1].equalsIgnoreCase("npc") || args[1].equalsIgnoreCase("board"))) {
            out.add("create");
            out.add("remove");
        } else if (sender.hasPermission(ADMIN_PERMISSION) && args[0].equalsIgnoreCase("admin")
                && args.length >= 3 && args[1].equalsIgnoreCase("rating")) {
            if (args.length == 3) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            }
        } else if (sender.hasPermission(ADMIN_PERMISSION) && args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2) {
                out.add("setspawn");
                out.add("tp");
                out.add("give");
                out.add("clear");
                out.add("info");
                out.add("npc");
                out.add("exitsign");
                out.add("board");
                out.add("rating");
                out.addAll(CombatAdminCommands.NAMES);
                out.addAll(MatchAdminCommands.NAMES);
            } else if (MatchAdminCommands.NAMES.contains(args[1].toLowerCase(Locale.ROOT))) {
                out.addAll(matchCommands.complete(java.util.Arrays.copyOfRange(args, 1, args.length)));
            } else if (CombatAdminCommands.NAMES.contains(args[1].toLowerCase(Locale.ROOT))) {
                out.addAll(combatCommands.complete(java.util.Arrays.copyOfRange(args, 1, args.length), weaponIds));
            } else if (args.length == 3) {
                String a = args[1].toLowerCase(Locale.ROOT);
                if (a.equals("give")) {
                    out.addAll(kitCommands.kitIds());
                } else if (a.equals("setspawn") || a.equals("tp")) {
                    out.addAll(SpawnStore.KEYS);
                }
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(prefix));
        return out;
    }
}
