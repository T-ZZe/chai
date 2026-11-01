package fr.chainsawrpg.data;

import fr.chainsawrpg.ChainsawPlugin;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PlayerDataManager {
    private final ChainsawPlugin pl;
    private final Map<UUID, PlayerData> map = new ConcurrentHashMap<>();

    public PlayerDataManager(ChainsawPlugin pl) { this.pl = pl; }

    public PlayerData get(UUID id) { return map.get(id); }
    public PlayerData get(Player p) { return map.get(p.getUniqueId()); }

    /** Appelé depuis AsyncPlayerPreLoginEvent (thread asynchrone : l'attente est autorisée). */
    public void load(UUID id, String name) throws Exception {
        PlayerData d = pl.db.loadPlayer(id, name);
        d.name = name;
        d.sessionStart = System.currentTimeMillis();
        map.put(id, d);
    }

    /** Repli (ex: /reload) : chargement bloquant bref. */
    public PlayerData ensure(Player p) {
        PlayerData d = map.get(p.getUniqueId());
        if (d != null) return d;
        try {
            load(p.getUniqueId(), p.getName());
        } catch (Exception e) {
            pl.getLogger().log(Level.SEVERE, "Chargement des donnees de " + p.getName(), e);
            return null;
        }
        return map.get(p.getUniqueId());
    }

    public void save(UUID id) {
        PlayerData d = map.get(id);
        if (d != null) pl.db.savePlayer(d.copy());
    }

    public void unload(UUID id) {
        PlayerData d = map.remove(id);
        if (d != null) pl.db.savePlayer(d.copy());
    }

    public void saveAll() {
        for (PlayerData d : map.values()) pl.db.savePlayer(d.copy());
    }
}
