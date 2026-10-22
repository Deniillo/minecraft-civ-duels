package mine.deniilo.duels.combat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class CombatDebug {

    private final Set<UUID> chat = new HashSet<>();
    private final Path file;
    private final boolean fileLog;
    private final Logger logger;

    public CombatDebug(Path file, boolean fileLog, Logger logger) {
        this.file = file;
        this.fileLog = fileLog;
        this.logger = logger;
    }

    public void setChat(UUID player, boolean enabled) {
        if (enabled) {
            chat.add(player);
        } else {
            chat.remove(player);
        }
    }

    public boolean isChat(UUID player) {
        return chat.contains(player);
    }

    public void forget(UUID player) {
        chat.remove(player);
    }

    public void report(String text, UUID... who) {
        for (UUID id : who) {
            if (id != null && chat.contains(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.sendMessage("§7[бой] " + text);
                }
            }
        }
        if (fileLog) {
            try {
                Files.writeString(file, Instant.now() + " " + text + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                logger.warning("Не удалось записать combat-debug.log: " + e.getMessage());
            }
        }
    }
}
