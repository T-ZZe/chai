package fr.chainsawrpg.skill;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.data.PlayerData;

/** Ressource SANG : stockée dans PlayerData, plafonnée par blood.max. */
public final class BloodManager {
    private final ChainsawPlugin pl;

    public BloodManager(ChainsawPlugin pl) { this.pl = pl; }

    public int get(PlayerData d) { return d == null ? 0 : d.blood; }

    public int add(PlayerData d, int amount) {
        if (d == null || amount <= 0) return 0;
        int before = d.blood;
        d.blood = Math.min(pl.settings.bloodMax, d.blood + amount);
        return d.blood - before;
    }

    public boolean take(PlayerData d, int amount) {
        if (d == null || d.blood < amount) return false;
        d.blood -= amount;
        return true;
    }

    /** Perte à la mort (pourcentage configurable). */
    public int loseOnDeath(PlayerData d) {
        if (d == null || pl.settings.bloodLossDeathPct <= 0) return 0;
        int lost = (int) Math.floor(d.blood * pl.settings.bloodLossDeathPct / 100.0);
        d.blood -= lost;
        return lost;
    }
}
