package fr.chainsawrpg.skill;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.data.PlayerData;
import fr.chainsawrpg.demon.DemonDef;
import fr.chainsawrpg.demon.SkillDef;
import fr.chainsawrpg.skill.types.Area;
import fr.chainsawrpg.skill.types.Projectiles;
import fr.chainsawrpg.skill.types.SelfSkills;
import fr.chainsawrpg.skill.types.StatusSkills;
import fr.chainsawrpg.skill.types.Strike;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Pipeline de lancement : permissions, zones, cooldown, coût, handler, effets secondaires. */
public final class SkillManager {
    private final ChainsawPlugin pl;
    private final Map<String, SkillHandler> handlers = new HashMap<>();
    private final Map<UUID, Integer> selected = new HashMap<>();
    private final Map<UUID, Integer> zones = new HashMap<>();

    public SkillManager(ChainsawPlugin pl) { this.pl = pl; }

    public void registerAll() {
        Strike.register(this);
        Area.register(this);
        Projectiles.register(this);
        StatusSkills.register(this);
        SelfSkills.register(this);
    }

    public void register(String type, SkillHandler h) { handlers.put(type.toUpperCase(), h); }
    public boolean has(String type) { return handlers.containsKey(type); }

    // ---- selection (livre des contrats) ----
    public int selected(UUID id) { return selected.getOrDefault(id, 1); }

    public int cycle(UUID id) {
        int n = selected(id) % 4 + 1;
        selected.put(id, n);
        return n;
    }

    public void forget(UUID id) {
        selected.remove(id);
        zones.remove(id);
    }

    // ---- limite de zones persistantes ----
    public boolean zoneStart(UUID id) {
        int n = zones.getOrDefault(id, 0);
        if (n >= pl.settings.maxZones) return false;
        zones.put(id, n + 1);
        return true;
    }

    public void zoneEnd(UUID id) {
        zones.computeIfPresent(id, (k, v) -> v <= 1 ? null : v - 1);
    }

    // ---- lancement ----
    public void cast(Player p, int slot) {
        if (!p.hasPermission("chainsawman.use")) {
            pl.msg.send(p, "no-permission");
            return;
        }
        PlayerData pd = pl.players.get(p);
        DemonDef d = pd == null ? null : pl.demons.get(pd.activeDemon);
        if (d == null) {
            pl.msg.feedback(p, "no-contract");
            return;
        }
        SkillDef def = d.skill(slot);
        if (def == null || slot < 1) return;
        if (!d.enabled) {
            pl.msg.feedback(p, "demon-disabled");
            return;
        }
        UUID id = p.getUniqueId();
        if (!pl.protection.canCast(p)) {
            pl.msg.feedback(p, "zone-forbidden");
            return;
        }
        if (pl.cooldowns.onGcd(id)) return;
        long rem = pl.cooldowns.remainingMs(id, def.id);
        if (rem > 0 && !p.hasPermission("chainsawman.bypass.cooldown")) {
            pl.msg.feedback(p, "on-cooldown", "skill", def.name, "seconds", String.format("%.1f", rem / 1000.0));
            return;
        }
        String err = pl.sacrifices.check(p, pd, def.cost);
        if (err != null) {
            pl.msg.feedback(p, err, "skill", def.name);
            return;
        }
        SkillHandler h = handlers.get(def.type);
        if (h == null) {
            pl.msg.feedback(p, "demon-disabled");
            return;
        }
        Cast c = new Cast(pl, p, def, pd);
        boolean ok;
        try {
            ok = h.cast(c);
        } catch (RuntimeException e) {
            pl.getLogger().log(Level.SEVERE, "Erreur pendant la competence " + def.id, e);
            ok = false;
            c.failKey = "skill-error";
        }
        if (!ok) {
            pl.msg.feedback(p, c.failKey, "skill", def.name);
            return;
        }
        pl.sacrifices.pay(p, pd, def.cost);
        pl.cooldowns.set(id, def.id, (long) (def.cooldown * 1000));
        pl.cooldowns.markGcd(id, pl.settings.gcdMs);
        pd.skillsUsed++;
        pl.contracts.addXp(p, pl.settings.xpPerSkill * (def.isUltimate() ? 3 : 1));
    }
}
