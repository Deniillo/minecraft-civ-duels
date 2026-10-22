package mine.deniilo.duels.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QueueService {

    public record Key(String kitId, boolean ranked) {
    }

    public record Pair(UUID first, UUID second, Key key) {
    }

    private record Entry(UUID player, int rating, long joinedAtMillis) {
    }

    private final QueueSettings settings;
    private final Map<Key, List<Entry>> queues = new LinkedHashMap<>();
    private final Map<UUID, Key> waiting = new HashMap<>();

    public QueueService(QueueSettings settings) {
        this.settings = settings;
    }

    public void join(UUID player, Key key, int rating, long nowMillis) {
        leave(player);
        queues.computeIfAbsent(key, k -> new ArrayList<>()).add(new Entry(player, rating, nowMillis));
        waiting.put(player, key);
    }

    public boolean leave(UUID player) {
        Key key = waiting.remove(player);
        if (key == null) {
            return false;
        }
        List<Entry> queue = queues.get(key);
        if (queue != null) {
            queue.removeIf(e -> e.player().equals(player));
        }
        return true;
    }

    public Optional<Key> keyOf(UUID player) {
        return Optional.ofNullable(waiting.get(player));
    }

    public int waitingTotal() {
        return waiting.size();
    }

    public int size(Key key) {
        List<Entry> queue = queues.get(key);
        return queue == null ? 0 : queue.size();
    }

    public List<Pair> match(long nowMillis) {
        List<Pair> pairs = new ArrayList<>();
        for (Map.Entry<Key, List<Entry>> e : queues.entrySet()) {
            Key key = e.getKey();
            List<Entry> queue = e.getValue();
            boolean progress = true;
            while (progress) {
                progress = false;
                for (int i = 0; i < queue.size() && !progress; i++) {
                    Entry a = queue.get(i);
                    int best = -1;
                    int bestDiff = Integer.MAX_VALUE;
                    for (int j = i + 1; j < queue.size(); j++) {
                        Entry b = queue.get(j);
                        int diff = Math.abs(a.rating() - b.rating());
                        if (compatible(key, a, b, diff, nowMillis) && diff < bestDiff) {
                            best = j;
                            bestDiff = diff;
                        }
                    }
                    if (best >= 0) {
                        Entry b = queue.get(best);
                        queue.remove(best);
                        queue.remove(i);
                        waiting.remove(a.player());
                        waiting.remove(b.player());
                        pairs.add(new Pair(a.player(), b.player(), key));
                        progress = true;
                    }
                }
            }
        }
        return pairs;
    }

    private boolean compatible(Key key, Entry a, Entry b, int diff, long now) {
        if (!key.ranked()) {
            return true;
        }
        int window = Math.max(
                settings.windowAfter(now - a.joinedAtMillis()),
                settings.windowAfter(now - b.joinedAtMillis()));
        return diff <= window;
    }

    public Iterator<UUID> waitingPlayers() {
        return List.copyOf(waiting.keySet()).iterator();
    }
}
