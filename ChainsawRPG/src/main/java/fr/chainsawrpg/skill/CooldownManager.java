package fr.chainsawrpg.skill;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Cooldowns côté serveur, en millisecondes absolues (conservés à la reconnexion pour empêcher le contournement). */
public final class CooldownManager {
    private final Map<UUID, Map<String, Long>> cd = new HashMap<>();
    private final Map<UUID, Long> gcd = new HashMap<>();

    public long remainingMs(UUID id, String skillId) {
        Map<String, Long> m = cd.get(id);
        if (m == null) return 0;
        Long until = m.get(skillId);
        if (until == null) return 0;
        long r = until - System.currentTimeMillis();
        if (r <= 0) m.remove(skillId);
        return Math.max(0, r);
    }

    public void set(UUID id, String skillId, long ms) {
        cd.computeIfAbsent(id, k -> new HashMap<>()).put(skillId, System.currentTimeMillis() + ms);
    }

    public boolean onGcd(UUID id) {
        Long until = gcd.get(id);
        return until != null && System.currentTimeMillis() < until;
    }

    public void markGcd(UUID id, long ms) { gcd.put(id, System.currentTimeMillis() + ms); }

    public void clear(UUID id) {
        cd.remove(id);
        gcd.remove(id);
    }

    /** Supprime les entrées expirées (appelé périodiquement). */
    public void purge() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Map<String, Long>>> it = cd.entrySet().iterator(); it.hasNext(); ) {
            Map<String, Long> m = it.next().getValue();
            m.values().removeIf(t -> t <= now);
            if (m.isEmpty()) it.remove();
        }
        gcd.values().removeIf(t -> t <= now);
    }
}
