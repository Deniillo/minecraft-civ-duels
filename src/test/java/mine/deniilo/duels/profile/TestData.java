package mine.deniilo.duels.profile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

final class TestData {

    private static ItemCatalog items;

    private TestData() {
    }

    static YamlConfiguration resource(String name) {
        try (InputStream in = TestData.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new IllegalStateException("нет ресурса " + name);
            }
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(r);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static synchronized ItemCatalog items() {
        if (items == null) {
            items = ItemCatalog.from(resource("items.yml"));
        }
        return items;
    }

    static YamlConfiguration yaml(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return y;
    }

    static List<ArmorPiece> fullSet(ArmorClass armorClass, int tier, int protectionLevel) {
        List<ArmorPiece> pieces = new ArrayList<>();
        for (ArmorSlot slot : ArmorSlot.values()) {
            pieces.add(new ArmorPiece(items().armor(armorClass, tier, slot).orElseThrow(), protectionLevel));
        }
        return pieces;
    }
}
