package mine.deniilo.duels.training;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TrainingRooms {

    public enum State {
        EMPTY, WAITING, PLAYING
    }

    public record Room(int index, State state, UUID host, String kitId, UUID guest) {
    }

    private final List<Room> rooms = new ArrayList<>();

    public TrainingRooms(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Нужна хотя бы одна комната");
        }
        for (int i = 0; i < count; i++) {
            rooms.add(empty(i));
        }
    }

    private static Room empty(int i) {
        return new Room(i, State.EMPTY, null, null, null);
    }

    public int count() {
        return rooms.size();
    }

    public Room room(int index) {
        return rooms.get(index);
    }

    public boolean host(int index, UUID player, String kitId) {
        if (index < 0 || index >= rooms.size() || rooms.get(index).state() != State.EMPTY || isPlaying(player)) {
            return false;
        }
        leave(player);
        rooms.set(index, new Room(index, State.WAITING, player, kitId, null));
        return true;
    }

    public Optional<Room> join(int index, UUID player) {
        if (index < 0 || index >= rooms.size()) {
            return Optional.empty();
        }
        Room r = rooms.get(index);
        if (r.state() != State.WAITING || r.host().equals(player) || isPlaying(player)) {
            return Optional.empty();
        }
        leave(player);
        Room playing = new Room(index, State.PLAYING, r.host(), r.kitId(), player);
        rooms.set(index, playing);
        return Optional.of(playing);
    }

    public void revertToWaiting(int index) {
        Room r = rooms.get(index);
        if (r.state() == State.PLAYING) {
            rooms.set(index, new Room(index, State.WAITING, r.host(), r.kitId(), null));
        }
    }

    public boolean leave(UUID player) {
        for (int i = 0; i < rooms.size(); i++) {
            Room r = rooms.get(i);
            if (r.state() == State.WAITING && r.host().equals(player)) {
                rooms.set(i, empty(i));
                return true;
            }
        }
        return false;
    }

    public void finished(UUID a, UUID b) {
        for (int i = 0; i < rooms.size(); i++) {
            Room r = rooms.get(i);
            if (r.state() == State.PLAYING
                    && ((r.host().equals(a) && r.guest().equals(b)) || (r.host().equals(b) && r.guest().equals(a)))) {
                rooms.set(i, empty(i));
            }
        }
    }

    public boolean isPlaying(UUID player) {
        return rooms.stream().anyMatch(r -> r.state() == State.PLAYING
                && (r.host().equals(player) || r.guest().equals(player)));
    }

    public boolean isWaiting(UUID player) {
        return rooms.stream().anyMatch(r -> r.state() == State.WAITING && r.host().equals(player));
    }

    public int waitingCount() {
        return (int) rooms.stream().filter(r -> r.state() == State.WAITING).count();
    }
}
