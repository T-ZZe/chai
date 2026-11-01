package fr.chainsawrpg.skill;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.demon.SkillDef;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Application des dégâts de compétences : plafonds PvP/PvE, fenêtre anti-combo entre deux coups,
 * et contexte "compétence en cours" lisible par les listeners (le dégât est appliqué de façon synchrone).
 */
public final class Combat {
    /** États temporaires d'un joueur (buffs de compétences). */
    public static final class Buffs {
        public int dodge;
        public long dodgeUntil;
        public Set<Material> weaponMats = new HashSet<>();
        public double weaponBonus;
        public long weaponUntil;
        public long leapUntil;
    }

    private final ChainsawPlugin pl;
    private SkillDef current;
    private boolean applyingDot;
    private final Map<UUID, Long> lastHit = new HashMap<>();
    private final Map<UUID, Buffs> buffs = new HashMap<>();

    public Combat(ChainsawPlugin pl) { this.pl = pl; }

    public SkillDef current() { return current; }
    public boolean isDot() { return applyingDot; }

    public Buffs buffs(UUID id) { return buffs.computeIfAbsent(id, k -> new Buffs()); }
    public Buffs peek(UUID id) { return buffs.get(id); }
    public void clear(UUID id) { buffs.remove(id); }

    /** @return false si le coup a été ignoré (fenêtre anti-combo ou dégât nul). */
    public boolean skillDamage(Player caster, LivingEntity target, double amount, SkillDef def) {
        double cap = target instanceof Player ? pl.settings.maxHitPvp : pl.settings.maxHitPve;
        amount = Math.min(amount, cap);
        if (amount <= 0 || !target.isValid() || target.isDead()) return false;
        long now = System.currentTimeMillis();
        Long last = lastHit.get(target.getUniqueId());
        if (last != null && now - last < pl.settings.hitGapMs) return false;
        lastHit.put(target.getUniqueId(), now);
        if (lastHit.size() > 512) lastHit.values().removeIf(t -> now - t > 5000);
        SkillDef prev = current;
        current = def;
        try {
            target.setNoDamageTicks(0);
            target.damage(amount, caster);
        } finally {
            current = prev;
        }
        return true;
    }

    /** Dégâts sur la durée (statuts) : pas de passifs offensifs, pas de fenêtre anti-combo. */
    public void dotDamage(Player source, LivingEntity target, double amount) {
        if (amount <= 0 || !target.isValid() || target.isDead()) return;
        double cap = target instanceof Player ? pl.settings.maxHitPvp : pl.settings.maxHitPve;
        applyingDot = true;
        try {
            target.setNoDamageTicks(0);
            if (source != null && source.isOnline()) target.damage(Math.min(amount, cap), source);
            else target.damage(Math.min(amount, cap));
        } finally {
            applyingDot = false;
        }
    }
}
