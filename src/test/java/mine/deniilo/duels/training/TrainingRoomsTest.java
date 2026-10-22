package mine.deniilo.duels.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import mine.deniilo.duels.training.TrainingRooms.State;
import org.junit.jupiter.api.Test;

class TrainingRoomsTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void roomsStartEmpty() {
        TrainingRooms rooms = new TrainingRooms(3);
        assertEquals(3, rooms.count());
        for (int i = 0; i < 3; i++) {
            assertEquals(State.EMPTY, rooms.room(i).state());
        }
        assertThrows(IllegalArgumentException.class, () -> new TrainingRooms(0));
    }

    @Test
    void hostWaitsThenGuestStartsMatch() {
        TrainingRooms rooms = new TrainingRooms(2);
        assertTrue(rooms.host(0, a, "t4_heavy"));
        assertEquals(State.WAITING, rooms.room(0).state());
        assertEquals(1, rooms.waitingCount());
        TrainingRooms.Room r = rooms.join(0, b).orElseThrow();
        assertEquals(State.PLAYING, r.state());
        assertEquals("t4_heavy", r.kitId());
        assertEquals(0, rooms.waitingCount());
        rooms.finished(b, a);
        assertEquals(State.EMPTY, rooms.room(0).state());
    }

    @Test
    void cannotJoinOwnOrBusyOrEmptyRoom() {
        TrainingRooms rooms = new TrainingRooms(2);
        assertTrue(rooms.join(0, a).isEmpty(), "пустая");
        rooms.host(0, a, "t4_heavy");
        assertTrue(rooms.join(0, a).isEmpty(), "своя");
        rooms.join(0, b);
        assertTrue(rooms.join(0, c).isEmpty(), "идёт матч");
        assertFalse(rooms.host(0, c, "t4_heavy"), "занята");
    }

    @Test
    void hostMovesBetweenRooms() {
        TrainingRooms rooms = new TrainingRooms(3);
        rooms.host(0, a, "t4_heavy");
        rooms.host(2, a, "t5_soul");
        assertEquals(State.EMPTY, rooms.room(0).state());
        assertEquals("t5_soul", rooms.room(2).kitId());
    }

    @Test
    void leaveCancelsWaitingButNotPlaying() {
        TrainingRooms rooms = new TrainingRooms(1);
        rooms.host(0, a, "t4_heavy");
        assertTrue(rooms.leave(a));
        assertFalse(rooms.leave(a));
        rooms.host(0, a, "t4_heavy");
        rooms.join(0, b);
        assertFalse(rooms.leave(a));
        assertTrue(rooms.isPlaying(a));
        assertFalse(rooms.host(0, a, "x"));
    }

    @Test
    void failedStartRevertsToWaiting() {
        TrainingRooms rooms = new TrainingRooms(1);
        rooms.host(0, a, "t4_heavy");
        rooms.join(0, b);
        rooms.revertToWaiting(0);
        assertEquals(State.WAITING, rooms.room(0).state());
        assertTrue(rooms.isWaiting(a));
        assertFalse(rooms.isPlaying(b));
    }
}
