package mine.deniilo.duels.hub;

import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

public record HubSettings(Mode mode, String command, boolean commandAsConsole, String world, String server) {

    public enum Mode {
        LOCAL, PROXY
    }

    public static HubSettings from(ConfigurationSection root) {
        ConfigurationSection s = root == null ? null : root.getConfigurationSection("hub");
        if (s == null) {
            return new HubSettings(Mode.LOCAL, "", true, "", "lobby");
        }
        String rawMode = s.getString("mode", "local").trim().toUpperCase(Locale.ROOT);
        Mode mode;
        try {
            mode = Mode.valueOf(rawMode);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("hub.mode: local или proxy, указано " + rawMode.toLowerCase(Locale.ROOT));
        }
        String command = s.getString("command", "").trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        String server = s.getString("server", "lobby").trim();
        if (mode == Mode.PROXY && server.isEmpty()) {
            throw new IllegalArgumentException("hub.server не задан для режима proxy");
        }
        return new HubSettings(mode, command, s.getBoolean("command-as-console", true),
                s.getString("world", "").trim(), server);
    }

    public String commandFor(String playerName) {
        return command.replace("{player}", playerName);
    }
}
