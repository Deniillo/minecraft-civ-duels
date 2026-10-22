package mine.deniilo.duels.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class ItemKeys {

    public static final int SCHEMA_VERSION = 1;

    private final Plugin plugin;
    public final NamespacedKey itemId;
    public final NamespacedKey attackLevel;
    public final NamespacedKey protectionLevel;
    public final NamespacedKey piercingLevel;
    public final NamespacedKey sessionId;
    public final NamespacedKey schemaVersion;

    public ItemKeys(Plugin plugin) {
        this.plugin = plugin;
        this.itemId = new NamespacedKey(plugin, "item_id");
        this.attackLevel = new NamespacedKey(plugin, "attack_level");
        this.protectionLevel = new NamespacedKey(plugin, "protection_level");
        this.piercingLevel = new NamespacedKey(plugin, "piercing_level");
        this.sessionId = new NamespacedKey(plugin, "session_id");
        this.schemaVersion = new NamespacedKey(plugin, "schema_version");
    }

    public NamespacedKey modifier(String name) {
        return new NamespacedKey(plugin, name);
    }
}
