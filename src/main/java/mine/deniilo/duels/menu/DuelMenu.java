package mine.deniilo.duels.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mine.deniilo.duels.hub.DuelsGateway;
import mine.deniilo.duels.match.MatchManager;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.profile.Kit;
import mine.deniilo.duels.profile.KitCatalog;
import mine.deniilo.duels.queue.QueueCoordinator;
import mine.deniilo.duels.queue.QueueService;
import mine.deniilo.duels.rating.RatingRepository;
import mine.deniilo.duels.rating.StatsCommands;
import mine.deniilo.duels.training.TrainingCoordinator;
import mine.deniilo.duels.training.TrainingRooms;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.Plugin;

public final class DuelMenu implements Listener {

    enum Kind {
        MAIN, TRAINING, PICK_ROOM_KIT, PICK_BOT_KIT
    }

    static final int FIRST_KIT_SLOT = 10;
    static final int MAX_KITS = 7;
    static final int TRAINING_SLOT = 26;
    static final int LOBBY_SLOT = 27;
    static final int HUB_SLOT = 29;
    static final int LEAVE_SLOT = 31;
    static final int STATS_SLOT = 33;
    static final int TOP_SLOT = 35;
    static final int TRAINING_SIZE = 54;
    static final int TRAINING_BACK_SLOT = 45;
    static final int TRAINING_BOT_SLOT = 49;
    static final int MAX_ROOMS_SHOWN = 36;
    static final int PICK_SIZE = 27;
    static final int PICK_BACK_SLOT = 22;

    static int[] kitSlots(int n) {
        int[] slots = new int[n];
        if (n <= 4) {
            for (int i = 0; i < n; i++) {
                slots[i] = 13 - (n - 1) + 2 * i;
            }
        } else {
            int start = 13 - n / 2;
            for (int i = 0; i < n; i++) {
                slots[i] = start + i;
            }
        }
        return slots;
    }

    private static int indexOfSlot(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final Kind kind;
        private final int room;

        private Holder(Kind kind, int room) {
            this.kind = kind;
            this.room = room;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Plugin plugin;
    private final Messages messages;
    private final KitCatalog kits;
    private final QueueCoordinator queue;
    private final TrainingCoordinator training;
    private final MatchManager matches;
    private final DuelsGateway gateway;
    private final RatingRepository ratings;
    private final StatsCommands stats;

    public DuelMenu(Plugin plugin, Messages messages, KitCatalog kits, QueueCoordinator queue,
                    TrainingCoordinator training, MatchManager matches, DuelsGateway gateway,
                    RatingRepository ratings, StatsCommands stats) {
        this.plugin = plugin;
        this.messages = messages;
        this.kits = kits;
        this.queue = queue;
        this.training = training;
        this.matches = matches;
        this.gateway = gateway;
        this.ratings = ratings;
        this.stats = stats;
    }

    public void startRefresh() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder h && h.kind == Kind.TRAINING) {
                    renderTraining(h, p);
                }
            }
        }, 20L, 20L);
    }

    public void open(Player player) {
        Holder holder = new Holder(Kind.MAIN, 0);
        holder.inventory = Bukkit.createInventory(holder, 36, messages.raw("menu-title"));
        renderMain(holder, player);
        player.openInventory(holder.inventory);
    }

    private void openTraining(Player player) {
        Holder holder = new Holder(Kind.TRAINING, 0);
        holder.inventory = Bukkit.createInventory(holder, TRAINING_SIZE, messages.raw("menu-training-title"));
        renderTraining(holder, player);
        player.openInventory(holder.inventory);
    }

    private void openPicker(Player player, Kind kind, int room) {
        Holder holder = new Holder(kind, room);
        holder.inventory = Bukkit.createInventory(holder, PICK_SIZE, messages.raw(
                kind == Kind.PICK_BOT_KIT ? "menu-pick-bot-title" : "menu-pick-room-title", "room", String.valueOf(room + 1)));
        fill(holder.inventory);
        List<Kit> list = kitList();
        int[] slots = kitSlots(list.size());
        for (int i = 0; i < list.size(); i++) {
            holder.inventory.setItem(slots[i], kitItem(list.get(i), List.of()));
        }
        holder.inventory.setItem(PICK_BACK_SLOT, item(Material.ARROW, messages.raw("menu-back"), List.of()));
        player.openInventory(holder.inventory);
    }

    private List<Kit> kitList() {
        List<Kit> out = new ArrayList<>();
        for (String id : kits.ids()) {
            if (out.size() >= MAX_KITS) {
                break;
            }
            kits.get(id).ifPresent(out::add);
        }
        return out;
    }

    private void fill(Inventory inv) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private void renderMain(Holder holder, Player player) {
        Inventory inv = holder.inventory;
        fill(inv);
        List<Kit> list = kitList();
        int[] slots = kitSlots(list.size());
        Optional<QueueService.Key> waiting = queue.keyOf(player.getUniqueId());
        for (int i = 0; i < list.size(); i++) {
            Kit kit = list.get(i);
            List<Component> extra = new ArrayList<>();
            if (waiting.isPresent() && waiting.get().kitId().equals(kit.id())) {
                extra.add(messages.raw("menu-kit-waiting"));
            }
            extra.add(messages.raw("menu-kit-click-ranked"));
            inv.setItem(slots[i], kitItem(kit, extra));
        }
        inv.setItem(TRAINING_SLOT, item(Material.IRON_SWORD, messages.raw("menu-training"),
                List.of(messages.raw("menu-training-lore"))));
        inv.setItem(LOBBY_SLOT, item(Material.COMPASS, messages.raw("menu-lobby"), List.of()));
        inv.setItem(HUB_SLOT, item(Material.RED_BED, messages.raw("menu-hub"), List.of()));
        boolean busy = waiting.isPresent() || matches.matchOf(player.getUniqueId()).isPresent()
                || training.rooms().isWaiting(player.getUniqueId());
        inv.setItem(LEAVE_SLOT, item(Material.BARRIER, messages.raw("menu-leave"),
                List.of(messages.raw(busy ? "menu-leave-lore-busy" : "menu-leave-lore-idle"))));
        inv.setItem(STATS_SLOT, item(Material.BOOK, messages.raw("menu-stats"), List.of()));
        inv.setItem(TOP_SLOT, item(Material.NETHER_STAR, messages.raw("menu-top"), List.of()));
    }

    private void renderTraining(Holder holder, Player viewer) {
        Inventory inv = holder.inventory;
        fill(inv);
        TrainingRooms rooms = training.rooms();
        int shown = Math.min(rooms.count(), MAX_ROOMS_SHOWN);
        for (int i = 0; i < shown; i++) {
            TrainingRooms.Room r = rooms.room(i);
            String number = String.valueOf(i + 1);
            switch (r.state()) {
                case EMPTY -> inv.setItem(i, item(Material.GRAY_DYE, messages.raw("room-empty", "room", number),
                        List.of(messages.raw("room-empty-lore"))));
                case WAITING -> {
                    boolean own = r.host().equals(viewer.getUniqueId());
                    inv.setItem(i, item(Material.GREEN_DYE, messages.raw("room-waiting", "room", number), List.of(
                            messages.raw("room-host", "player", name(r.host())),
                            messages.raw("room-kit", "kit", kitName(r.kitId())),
                            messages.raw(own ? "room-waiting-lore-own" : "room-waiting-lore"))));
                }
                case PLAYING -> inv.setItem(i, item(Material.RED_DYE, messages.raw("room-playing", "room", number), List.of(
                        messages.raw("room-players", "a", name(r.host()), "b", name(r.guest())),
                        messages.raw("room-kit", "kit", kitName(r.kitId())))));
            }
        }
        inv.setItem(TRAINING_BACK_SLOT, item(Material.ARROW, messages.raw("menu-back"), List.of()));
        inv.setItem(TRAINING_BOT_SLOT, item(Material.ZOMBIE_HEAD, messages.raw("menu-bot"),
                List.of(messages.raw("menu-bot-lore"))));
    }

    private String name(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    private String kitName(String id) {
        return kits.get(id).map(Kit::displayName).orElse(id);
    }

    private ItemStack kitItem(Kit kit, List<Component> extra) {
        List<Component> lore = new ArrayList<>();
        lore.addAll(extra);
        ItemStack stack = item(icon(kit), Component.text(kit.displayName()).decoration(TextDecoration.ITALIC, false), lore);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(Color.RED);
        }
        if (kit.enhanced()) {
            meta.setEnchantmentGlintOverride(true);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static Material icon(Kit kit) {
        if (kit.armorClass() == mine.deniilo.duels.profile.ArmorClass.LIGHT) {
            return Material.LEATHER_CHESTPLATE;
        }
        return switch (kit.armorTier()) {
            case 3 -> Material.GOLDEN_CHESTPLATE;
            case 4 -> Material.DIAMOND_CHESTPLATE;
            case 5 -> Material.NETHERITE_CHESTPLATE;
            default -> Material.IRON_CHESTPLATE;
        };
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()
                || !(event.getWhoClicked() instanceof Player player) || !event.isLeftClick()) {
            return;
        }
        int slot = event.getSlot();
        switch (holder.kind) {
            case MAIN -> clickMain(player, slot);
            case TRAINING -> clickTraining(player, slot);
            case PICK_ROOM_KIT -> clickPicker(player, holder, slot);
            case PICK_BOT_KIT -> clickPicker(player, holder, slot);
        }
    }

    private void clickMain(Player player, int slot) {
        List<Kit> list = kitList();
        int kitIndex = indexOfSlot(kitSlots(list.size()), slot);
        if (kitIndex >= 0) {
            player.closeInventory();
            training.cancel(player.getUniqueId());
            queue.enqueue(player, list.get(kitIndex), true);
            return;
        }
        switch (slot) {
            case TRAINING_SLOT -> openTraining(player);
            case LOBBY_SLOT -> {
                player.closeInventory();
                gateway.join(player);
            }
            case HUB_SLOT -> {
                player.closeInventory();
                gateway.exit(player);
            }
            case LEAVE_SLOT -> {
                player.closeInventory();
                leave(player);
            }
            case STATS_SLOT -> {
                player.closeInventory();
                stats.stats(player, new String[] {"stats"});
            }
            case TOP_SLOT -> {
                player.closeInventory();
                stats.top(player, new String[] {"top"});
            }
            default -> { }
        }
    }

    private void clickTraining(Player player, int slot) {
        if (slot == TRAINING_BACK_SLOT) {
            open(player);
            return;
        }
        if (slot == TRAINING_BOT_SLOT) {
            openPicker(player, Kind.PICK_BOT_KIT, 0);
            return;
        }
        TrainingRooms rooms = training.rooms();
        if (slot < 0 || slot >= Math.min(rooms.count(), MAX_ROOMS_SHOWN)) {
            return;
        }
        TrainingRooms.Room room = rooms.room(slot);
        switch (room.state()) {
            case EMPTY -> openPicker(player, Kind.PICK_ROOM_KIT, slot);
            case WAITING -> {
                if (room.host().equals(player.getUniqueId())) {
                    training.cancel(player.getUniqueId());
                    messages.send(player, "training-room-cancelled");
                    renderTraining((Holder) player.getOpenInventory().getTopInventory().getHolder(), player);
                } else {
                    player.closeInventory();
                    training.join(player, slot);
                }
            }
            case PLAYING -> messages.send(player, "training-room-busy");
        }
    }

    private void clickPicker(Player player, Holder holder, int slot) {
        if (slot == PICK_BACK_SLOT) {
            openTraining(player);
            return;
        }
        List<Kit> list = kitList();
        int index = indexOfSlot(kitSlots(list.size()), slot);
        if (index < 0) {
            return;
        }
        Kit kit = list.get(index);
        player.closeInventory();
        if (holder.kind == Kind.PICK_ROOM_KIT) {
            training.host(player, holder.room, kit);
        } else {
            startBot(player, kit);
        }
    }

    private void startBot(Player player, Kit kit) {
        queue.leave(player.getUniqueId());
        training.cancel(player.getUniqueId());
        Optional<MatchManager.StartError> error = matches.startSolo(player, kit);
        if (error.isPresent()) {
            messages.send(player, MatchManager.errorKey(error.get()));
        } else {
            messages.send(player, "match-started");
        }
    }

    public void leave(Player player) {
        boolean wasQueued = queue.leave(player.getUniqueId());
        boolean hadRoom = training.cancel(player.getUniqueId());
        if (matches.leave(player)) {
            return;
        }
        if (wasQueued || hadRoom) {
            messages.send(player, wasQueued ? "queue-left" : "training-room-cancelled");
        } else {
            messages.send(player, "not-in-match");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
