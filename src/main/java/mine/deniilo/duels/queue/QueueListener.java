package mine.deniilo.duels.queue;

import mine.deniilo.duels.scoreboard.SidebarService;
import mine.deniilo.duels.training.TrainingCoordinator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class QueueListener implements Listener {

    private final QueueCoordinator queue;
    private final TrainingCoordinator training;
    private final SidebarService sidebar;

    public QueueListener(QueueCoordinator queue, TrainingCoordinator training, SidebarService sidebar) {
        this.queue = queue;
        this.training = training;
        this.sidebar = sidebar;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        queue.leave(event.getPlayer().getUniqueId());
        training.cancel(event.getPlayer().getUniqueId());
        sidebar.forget(event.getPlayer().getUniqueId());
    }
}
