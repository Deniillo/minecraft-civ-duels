package mine.deniilo.duels.kit;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.profile.ArmorSetStats;
import mine.deniilo.duels.profile.Kit;
import mine.deniilo.duels.profile.KitCatalog;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class KitAdminCommands {

    private final Messages messages;
    private final KitCatalog kits;
    private final KitService service;

    public KitAdminCommands(Messages messages, KitCatalog kits, KitService service) {
        this.messages = messages;
        this.kits = kits;
        this.service = service;
    }

    public java.util.List<String> kitIds() {
        return kits.ids();
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(player, args);
            case "clear" -> {
                service.clear(player);
                messages.send(player, "kit-cleared");
            }
            case "info" -> info(player);
            default -> messages.send(sender, "admin-usage", "keys", "");
        }
    }

    private void give(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "kit-unknown", "kit", "", "kits", String.join(", ", kits.ids()));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        Optional<Kit> kit = kits.get(id);
        if (kit.isEmpty()) {
            messages.send(player, "kit-unknown", "kit", id, "kits", String.join(", ", kits.ids()));
            return;
        }
        if (service.give(player, kit.get(), UUID.randomUUID())) {
            messages.send(player, "kit-given", "kit", kit.get().displayName());
        } else {
            messages.send(player, "kit-broken", "kit", id);
        }
    }

    private void info(Player player) {
        ArmorSetStats s = service.stats(player);
        messages.send(player, "kit-info",
                "defense", fmt(s.defense()),
                "hp", fmt(s.maxHp()),
                "speed", fmt(s.speed() * 100),
                "light", String.valueOf(s.lightCount()),
                "heavy", String.valueOf(s.heavyCount()),
                "soul", String.valueOf(s.soulCount()),
                "soulset", s.soulSet() ? "да" : "нет",
                "soult5", s.soulT5Set() ? "да" : "нет");
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
