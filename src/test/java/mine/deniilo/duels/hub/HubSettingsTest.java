package mine.deniilo.duels.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class HubSettingsTest {

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return y;
    }

    @Test
    void withoutSectionHubIsMainWorldOfThisServer() {
        HubSettings s = HubSettings.from(yaml("a: 1"));
        assertEquals(HubSettings.Mode.LOCAL, s.mode());
        assertEquals("", s.command());
        assertEquals("", s.world());
    }

    @Test
    void localCommandTakesPlayerName() {
        HubSettings s = HubSettings.from(yaml("hub: {mode: local, command: '/warp lobby {player}'}"));
        assertEquals("warp lobby Steve", s.commandFor("Steve"));
        assertTrue(s.commandAsConsole());
    }

    @Test
    void proxyNeedsServerName() {
        assertEquals("hub-1", HubSettings.from(yaml("hub: {mode: PROXY, server: hub-1}")).server());
        assertThrows(IllegalArgumentException.class, () -> HubSettings.from(yaml("hub: {mode: proxy, server: ''}")));
    }

    @Test
    void unknownModeIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> HubSettings.from(yaml("hub: {mode: bungee}")));
        assertTrue(e.getMessage().contains("hub.mode"));
    }

    @Test
    void proxyConnectMessageIsBungeeFormat() throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(ProxyHubAdapter.connect("lobby")));
        assertEquals("Connect", in.readUTF());
        assertEquals("lobby", in.readUTF());
        assertEquals(0, in.available());
    }
}
