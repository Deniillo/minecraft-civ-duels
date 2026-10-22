package mine.deniilo.duels.match;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ArenaPool {

    private final int capacity;
    private final Set<UUID> inUse = new HashSet<>();

    public ArenaPool(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Нужна хотя бы одна арена");
        }
        this.capacity = capacity;
    }

    public boolean tryAcquire(UUID matchId) {
        if (inUse.contains(matchId)) {
            return true;
        }
        if (inUse.size() >= capacity) {
            return false;
        }
        return inUse.add(matchId);
    }

    public void release(UUID matchId) {
        inUse.remove(matchId);
    }

    public int free() {
        return capacity - inUse.size();
    }
}
