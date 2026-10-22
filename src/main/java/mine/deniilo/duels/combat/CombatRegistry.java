package mine.deniilo.duels.combat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CombatRegistry {

    public static final UUID ADMIN_TEST = new UUID(0L, 1L);

    private final Map<UUID, UUID> sessionOf = new HashMap<>();
    private final Set<UUID> active = new HashSet<>();
    private final Set<UUID> dummies = new HashSet<>();

    public CombatRegistry() {
        active.add(ADMIN_TEST);
    }

    public void join(UUID entity, UUID session) {
        sessionOf.put(entity, session);
    }

    public void leave(UUID entity) {
        sessionOf.remove(entity);
        dummies.remove(entity);
    }

    public Optional<UUID> session(UUID entity) {
        return Optional.ofNullable(sessionOf.get(entity));
    }

    public void setActive(UUID session, boolean value) {
        if (session.equals(ADMIN_TEST)) {
            return;
        }
        if (value) {
            active.add(session);
        } else {
            active.remove(session);
        }
    }

    public boolean isActive(UUID session) {
        return active.contains(session);
    }

    public void addDummy(UUID entity) {
        sessionOf.put(entity, ADMIN_TEST);
        dummies.add(entity);
    }

    public boolean isDummy(UUID entity) {
        return dummies.contains(entity);
    }

    public Set<UUID> dummies() {
        return Set.copyOf(dummies);
    }

    public Set<UUID> members(UUID session) {
        Set<UUID> out = new HashSet<>();
        sessionOf.forEach((entity, s) -> {
            if (s.equals(session)) {
                out.add(entity);
            }
        });
        return out;
    }

    public boolean mayDamage(UUID attacker, UUID victim) {
        UUID a = sessionOf.get(attacker);
        UUID v = sessionOf.get(victim);
        return a != null && a.equals(v) && active.contains(a);
    }
}
