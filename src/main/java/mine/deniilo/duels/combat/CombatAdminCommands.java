package mine.deniilo.duels.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import mine.deniilo.duels.kit.KitService;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.profile.ArmorSetStats;
import mine.deniilo.duels.profile.ItemCatalog;
import mine.deniilo.duels.profile.Kit;
import mine.deniilo.duels.profile.KitCatalog;
import mine.deniilo.duels.profile.WeaponKind;
import mine.deniilo.duels.profile.WeaponProfile;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CombatAdminCommands {

    public static final Set<String> NAMES = Set.of("dummy", "debug", "hitme");

    private final Messages messages;
    private final ItemCatalog items;
    private final KitCatalog kits;
    private final KitService kitService;
    private final CombatRegistry registry;
    private final CombatDebug debug;
    private final DummyService dummies;

    public CombatAdminCommands(Messages messages, ItemCatalog items, KitCatalog kits, KitService kitService,
                               CombatRegistry registry, CombatDebug debug, DummyService dummies) {
        this.messages = messages;
        this.items = items;
        this.kits = kits;
        this.kitService = kitService;
        this.registry = registry;
        this.debug = debug;
        this.dummies = dummies;
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "dummy" -> dummy(player, args);
            case "debug" -> debug(player, args);
            case "hitme" -> hitMe(player, args);
            default -> messages.send(sender, "combat-usage");
        }
    }

    private void dummy(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "combat-usage");
            return;
        }
        if (args[1].equalsIgnoreCase("clear")) {
            messages.send(player, "dummy-cleared", "count", String.valueOf(dummies.clear()));
            registry.leave(player.getUniqueId());
            return;
        }
        Optional<Kit> kit = kits.get(args[1].toLowerCase(Locale.ROOT));
        if (kit.isEmpty()) {
            messages.send(player, "kit-unknown", "kit", args[1], "kits", String.join(", ", kits.ids()));
            return;
        }
        if (!dummies.canSpawnIn(player.getWorld())) {
            messages.send(player, "dummy-peaceful");
            return;
        }
        registry.join(player.getUniqueId(), CombatRegistry.ADMIN_TEST);
        dummies.spawn(player, kit.get());
        messages.send(player, "dummy-spawned", "kit", kit.get().displayName());
    }

    private void debug(Player player, String[] args) {
        boolean on = args.length >= 2 && args[1].equalsIgnoreCase("on");
        boolean off = args.length >= 2 && args[1].equalsIgnoreCase("off");
        if (!on && !off) {
            messages.send(player, "combat-usage");
            return;
        }
        debug.setChat(player.getUniqueId(), on);
        messages.send(player, on ? "debug-on" : "debug-off");
    }

    private void hitMe(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "hitme-usage");
            return;
        }
        Optional<WeaponProfile> weapon = items.weapon(args[1].toLowerCase(Locale.ROOT));
        if (weapon.isEmpty()) {
            messages.send(player, "hitme-unknown-weapon", "weapon", args[1]);
            return;
        }
        WeaponProfile w = weapon.get();
        AttackType type = defaultType(w.kind());
        int attackLevel = 0;
        int piercing = 0;
        boolean crit = false;
        int number = 0;
        for (int i = 2; i < args.length; i++) {
            String a = args[i].toLowerCase(Locale.ROOT);
            switch (a) {
                case "melee" -> type = AttackType.MELEE;
                case "throw" -> type = AttackType.THROWN_TRIDENT;
                case "bow" -> type = AttackType.BOW;
                case "crossbow" -> type = AttackType.CROSSBOW;
                case "crit" -> crit = true;
                default -> {
                    try {
                        int v = Integer.parseInt(a);
                        if (number == 0) {
                            attackLevel = v;
                        } else {
                            piercing = v;
                        }
                        number++;
                    } catch (NumberFormatException e) {
                        messages.send(player, "hitme-usage");
                        return;
                    }
                }
            }
        }
        if ((type == AttackType.THROWN_TRIDENT && w.kind() != WeaponKind.TRIDENT)
                || ((type == AttackType.BOW) && w.kind() != WeaponKind.BOW)
                || ((type == AttackType.CROSSBOW) && w.kind() != WeaponKind.CROSSBOW)
                || (type == AttackType.MELEE && !w.kind().isMelee())) {
            messages.send(player, "hitme-mismatch");
            return;
        }
        AttackInput input = new AttackInput(type, w.kind(), w.baseAttack(), attackLevel, piercing, 1.0, crit);
        ArmorSetStats armor = kitService.stats(player);
        DamageBreakdown b = DamageCalculator.compute(input, armor.defense(), armor.soulT5Set());

        double health = player.getHealth();
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max == null ? 20.0 : max.getValue();
        String text = DamageReport.describe(input, b);
        if (b.finalDamage() >= health) {
            player.setHealth(maxHealth);
            messages.send(player, "hitme-lethal", "report", text);
        } else {
            player.setHealth(health - b.finalDamage());
            messages.send(player, "hitme-result", "report", text);
        }
        debug.report("hitme " + text, player.getUniqueId());
    }

    private static AttackType defaultType(WeaponKind kind) {
        return switch (kind) {
            case SWORD, AXE, TRIDENT -> AttackType.MELEE;
            case BOW -> AttackType.BOW;
            case CROSSBOW -> AttackType.CROSSBOW;
        };
    }

    public List<String> complete(String[] args, Set<String> weaponIds) {
        List<String> out = new ArrayList<>();
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "debug" -> out.addAll(List.of("on", "off"));
                case "dummy" -> {
                    out.addAll(kits.ids());
                    out.add("clear");
                }
                case "hitme" -> out.addAll(weaponIds);
                default -> { }
            }
        } else if (args.length >= 3 && sub.equals("hitme")) {
            out.addAll(List.of("melee", "throw", "bow", "crossbow", "crit"));
        }
        return out;
    }
}
