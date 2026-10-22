package mine.deniilo.duels.hub;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import mine.deniilo.duels.match.DuelMatch;
import mine.deniilo.duels.match.MatchManager;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.queue.QueueCoordinator;
import mine.deniilo.duels.training.TrainingCoordinator;
import mine.deniilo.duels.world.SpawnStore;
import mine.deniilo.duels.world.WorldService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DuelsGatewayTest {

    private final UUID id = UUID.randomUUID();
    private Player player;
    private SpawnStore spawns;
    private HubAdapter hub;
    private MatchManager matches;
    private QueueCoordinator queue;
    private TrainingCoordinator training;
    private DuelsGateway gateway;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        spawns = mock(SpawnStore.class);
        hub = mock(HubAdapter.class);
        matches = mock(MatchManager.class);
        queue = mock(QueueCoordinator.class);
        training = mock(TrainingCoordinator.class);
        when(matches.matchOf(id)).thenReturn(Optional.empty());
        gateway = new DuelsGateway(mock(Messages.class), spawns, mock(WorldService.class), hub, matches, queue,
                training);
    }

    @Test
    void joinTeleportsToDuelsLobby() {
        Location lobby = mock(Location.class);
        when(spawns.resolve(SpawnStore.LOBBY)).thenReturn(lobby);
        assertTrue(gateway.join(player));
        verify(player).teleport(lobby);
    }

    @Test
    void exitLeavesQueueAndRoomThenGoesToHub() {
        when(hub.sendToHub(player)).thenReturn(true);
        assertTrue(gateway.exit(player));
        verify(queue).leave(id);
        verify(training).cancel(id);
        verify(hub).sendToHub(player);
    }

    @Test
    void cannotEnterOrExitDuringMatch() {
        when(matches.matchOf(id)).thenReturn(Optional.of(mock(DuelMatch.class)));
        assertFalse(gateway.exit(player));
        assertFalse(gateway.join(player));
        verify(hub, never()).sendToHub(any());
        verify(player, never()).teleport(any(Location.class));
    }
}
