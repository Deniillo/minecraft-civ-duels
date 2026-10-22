package mine.deniilo.duels.api;

import org.bukkit.entity.Player;

public interface DuelsApi {

    boolean join(Player player);

    boolean exit(Player player);

    boolean isInDuels(Player player);

    boolean isInMatch(Player player);
}
