package mine.deniilo.duels.hub;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ProxyHubAdapter implements HubAdapter {

    public static final String CHANNEL = "BungeeCord";

    private final Plugin plugin;
    private final String server;

    public ProxyHubAdapter(Plugin plugin, String server) {
        this.plugin = plugin;
        this.server = server;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    @Override
    public boolean sendToHub(Player player) {
        player.sendPluginMessage(plugin, CHANNEL, connect(server));
        return true;
    }

    static byte[] connect(String server) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(server);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }
}
