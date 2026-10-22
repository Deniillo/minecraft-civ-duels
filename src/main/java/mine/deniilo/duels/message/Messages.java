package mine.deniilo.duels.message;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class Messages {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<String, String> texts = new HashMap<>();

    public static Messages load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration user = YamlConfiguration.loadConfiguration(file);
        Messages result = new Messages();
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                    for (String key : defaults.getKeys(false)) {
                        result.texts.put(key, user.getString(key, defaults.getString(key, key)));
                    }
                }
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Не удалось прочитать messages.yml из JAR: " + e.getMessage());
        }
        return result;
    }

    public Component get(String key, String... pairs) {
        String text = texts.getOrDefault(key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        String prefix = texts.getOrDefault("prefix", "");
        return mm.deserialize(prefix + text);
    }

    public Component raw(String key, String... pairs) {
        String text = texts.getOrDefault(key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return mm.deserialize(text).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    public void send(CommandSender to, String key, String... pairs) {
        to.sendMessage(get(key, pairs));
    }
}
