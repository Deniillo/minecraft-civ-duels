package mine.deniilo.duels.profile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;

public final class ItemCatalog {

    private final Map<String, ArmorProfile> armor = new LinkedHashMap<>();
    private final Map<String, WeaponProfile> weapons = new LinkedHashMap<>();

    private ItemCatalog() {
    }

    public static ItemCatalog from(ConfigurationSection root) {
        ItemCatalog catalog = new ItemCatalog();
        catalog.readArmor(section(root, "armor"));
        catalog.readWeapons(section(root, "weapons"));
        return catalog;
    }

    private static ConfigurationSection section(ConfigurationSection parent, String key) {
        ConfigurationSection s = parent == null ? null : parent.getConfigurationSection(key);
        if (s == null) {
            throw new IllegalArgumentException("В items.yml нет раздела " + key);
        }
        return s;
    }

    private void readArmor(ConfigurationSection armorRoot) {
        for (ArmorClass armorClass : ArmorClass.values()) {
            ConfigurationSection classSection = armorRoot.getConfigurationSection(armorClass.name().toLowerCase(Locale.ROOT));
            if (classSection == null) {
                continue;
            }
            for (String tierKey : classSection.getKeys(false)) {
                int tier = parseTier(armorClass, tierKey);
                ConfigurationSection t = classSection.getConfigurationSection(tierKey);
                if (t == null) {
                    throw new IllegalArgumentException("armor." + armorClass + "." + tierKey + " должен быть разделом");
                }
                String base = requireString(t, "material", "armor." + armorClass + "." + tierKey);
                double defense = requireNumber(t, "defense", "armor." + armorClass + "." + tierKey);
                double hp = requireNumber(t, "hp", "armor." + armorClass + "." + tierKey);
                double speed = requireNumber(t, "speed", "armor." + armorClass + "." + tierKey);
                double bootsSpeed = t.isSet("boots-speed") ? t.getDouble("boots-speed") : speed;
                for (ArmorSlot slot : ArmorSlot.values()) {
                    String id = ArmorProfile.idOf(armorClass, tier, slot);
                    double slotSpeed = slot == ArmorSlot.BOOTS ? bootsSpeed : speed;
                    armor.put(id, new ArmorProfile(id, armorClass, tier, slot, base, defense, hp, slotSpeed));
                }
            }
        }
    }

    private static int parseTier(ArmorClass armorClass, String key) {
        if (key.length() == 2 && key.charAt(0) == 't' && Character.isDigit(key.charAt(1))) {
            int tier = key.charAt(1) - '0';
            if (tier >= 1 && tier <= 5) {
                return tier;
            }
        }
        throw new IllegalArgumentException("Неверный тир брони " + armorClass + "." + key + " (ожидается t1..t5)");
    }

    private void readWeapons(ConfigurationSection weaponsRoot) {
        for (String id : weaponsRoot.getKeys(false)) {
            ConfigurationSection w = weaponsRoot.getConfigurationSection(id);
            String where = "weapons." + id;
            if (w == null) {
                throw new IllegalArgumentException(where + " должен быть разделом");
            }
            String kindName = requireString(w, "kind", where);
            WeaponKind kind;
            try {
                kind = WeaponKind.valueOf(kindName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(where + ".kind неизвестен: " + kindName);
            }
            int tier = w.getInt("tier", 0);
            if (tier < 1 || tier > 5) {
                throw new IllegalArgumentException(where + ".tier должен быть 1..5");
            }
            String material = requireString(w, "material", where);
            double attack = requireNumber(w, "attack", where);
            weapons.put(id, new WeaponProfile(id, kind, tier, material, attack));
        }
    }

    private static String requireString(ConfigurationSection s, String key, String where) {
        String v = s.getString(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(where + "." + key + " не задан");
        }
        return v;
    }

    private static double requireNumber(ConfigurationSection s, String key, String where) {
        if (!s.isDouble(key) && !s.isInt(key)) {
            throw new IllegalArgumentException(where + "." + key + " должно быть числом");
        }
        double v = s.getDouble(key);
        if (!Double.isFinite(v)) {
            throw new IllegalArgumentException(where + "." + key + " должно быть конечным числом");
        }
        return v;
    }

    public Optional<ArmorProfile> armor(String id) {
        return Optional.ofNullable(armor.get(id));
    }

    public Optional<ArmorProfile> armor(ArmorClass armorClass, int tier, ArmorSlot slot) {
        return armor(ArmorProfile.idOf(armorClass, tier, slot));
    }

    public Optional<WeaponProfile> weapon(String id) {
        return Optional.ofNullable(weapons.get(id));
    }

    public Collection<ArmorProfile> allArmor() {
        return armor.values();
    }

    public Collection<WeaponProfile> allWeapons() {
        return weapons.values();
    }
}
