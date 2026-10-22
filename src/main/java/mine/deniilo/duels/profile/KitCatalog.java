package mine.deniilo.duels.profile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;

public final class KitCatalog {

    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();

    private KitCatalog() {
    }

    public static KitCatalog from(ConfigurationSection root, ItemCatalog items) {
        KitCatalog catalog = new KitCatalog();
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("kits");
        if (section == null) {
            catalog.errors.add("В kits.yml нет раздела kits");
            return catalog;
        }
        for (String id : section.getKeys(false)) {
            try {
                Kit kit = readKit(id, section.getConfigurationSection(id));
                List<String> problems = KitValidator.validate(kit, items);
                if (problems.isEmpty()) {
                    catalog.kits.put(id, kit);
                } else {
                    for (String p : problems) {
                        catalog.errors.add("набор " + id + ": " + p);
                    }
                }
            } catch (IllegalArgumentException e) {
                catalog.errors.add("набор " + id + ": " + e.getMessage());
            }
        }
        return catalog;
    }

    private static Kit readKit(String id, ConfigurationSection s) {
        if (s == null) {
            throw new IllegalArgumentException("должен быть разделом");
        }
        ConfigurationSection armor = s.getConfigurationSection("armor");
        if (armor == null) {
            throw new IllegalArgumentException("нет раздела armor");
        }
        ArmorClass armorClass;
        try {
            armorClass = ArmorClass.valueOf(String.valueOf(armor.getString("class")).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("armor.class неизвестен: " + armor.getString("class"));
        }
        int tier = armor.getInt("tier", 0);
        List<String> weapons = s.getStringList("weapons");
        return new Kit(
                id,
                s.getString("display-name", id),
                armorClass,
                tier,
                armor.getInt("protection-level", 0),
                List.copyOf(weapons),
                s.getInt("attack-level", 0),
                s.getInt("piercing-level", 0));
    }

    public Optional<Kit> get(String id) {
        return Optional.ofNullable(kits.get(id));
    }

    public Collection<Kit> all() {
        return kits.values();
    }

    public List<String> ids() {
        return List.copyOf(kits.keySet());
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }
}
