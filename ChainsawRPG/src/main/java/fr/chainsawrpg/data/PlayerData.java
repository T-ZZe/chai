package fr.chainsawrpg.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Données d'un joueur. Modifiées uniquement sur le thread principal ; copy() avant envoi au thread DB. */
public final class PlayerData {
    public static final class Contract {
        public final String demonId;
        public int level = 1;
        public long xp = 0;
        public boolean awakened = false;
        public long acquiredAt = System.currentTimeMillis();

        public Contract(String demonId) { this.demonId = demonId; }

        public Contract copy() {
            Contract c = new Contract(demonId);
            c.level = level;
            c.xp = xp;
            c.awakened = awakened;
            c.acquiredAt = acquiredAt;
            return c;
        }
    }

    public final UUID uuid;
    public String name;
    public String activeDemon;
    public int blood;
    public long playtimeMs;
    public long sessionStart = System.currentTimeMillis();
    public long firstSeen = System.currentTimeMillis();
    public int mobKills, demonKills, pvpWins, bossKills, skillsUsed;
    public final Map<String, Contract> contracts = new LinkedHashMap<>();

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public Contract active() { return activeDemon == null ? null : contracts.get(activeDemon); }

    public long totalPlaytimeMs() { return playtimeMs + (System.currentTimeMillis() - sessionStart); }

    /** Fige le temps de jeu de la session courante dans playtimeMs. */
    public void flushPlaytime() {
        long now = System.currentTimeMillis();
        playtimeMs += now - sessionStart;
        sessionStart = now;
    }

    public PlayerData copy() {
        PlayerData d = new PlayerData(uuid, name);
        d.activeDemon = activeDemon;
        d.blood = blood;
        d.playtimeMs = totalPlaytimeMs();
        d.firstSeen = firstSeen;
        d.mobKills = mobKills;
        d.demonKills = demonKills;
        d.pvpWins = pvpWins;
        d.bossKills = bossKills;
        d.skillsUsed = skillsUsed;
        for (Map.Entry<String, Contract> e : contracts.entrySet()) d.contracts.put(e.getKey(), e.getValue().copy());
        return d;
    }
}
