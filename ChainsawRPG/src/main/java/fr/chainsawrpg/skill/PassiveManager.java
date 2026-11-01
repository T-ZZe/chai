package fr.chainsawrpg.skill;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.data.PlayerData;
import fr.chainsawrpg.demon.PassiveProfile;
import fr.chainsawrpg.util.Mc;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Applique les passifs du contrat actif : attributs, effets permanents, régénération, vitesse aquatique. */
public final class PassiveManager {
    private static final Set<String> FLAT_ATTRIBUTES = Set.of("knockback_resistance", "armor", "armor_toughness", "max_health",
            "water_movement_efficiency", "attack_damage");

    private final ChainsawPlugin pl;
    private final Map<UUID, PassiveProfile> active = new HashMap<>();
    private final Map<UUID, Set<PotionEffectType>> appliedEffects = new HashMap<>();
    private BukkitTask task;

    public PassiveManager(ChainsawPlugin pl) { this.pl = pl; }

    public PassiveProfile profile(Player p) { return active.get(p.getUniqueId()); }

    public PassiveProfile profile(Entity e) { return e instanceof Player p ? active.get(p.getUniqueId()) : null; }

    public void start() {
        task = new BukkitRunnable() {
            @Override public void run() { cycle(); }
        }.runTaskTimer(pl, 40, 40);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    /** Recalcule les passifs d'un joueur (connexion, respawn, changement de contrat). */
    public void refresh(Player p) {
        clear(p);
        PlayerData pd = pl.players.get(p);
        if (pd == null || pd.activeDemon == null) return;
        var d = pl.demons.get(pd.activeDemon);
        if (d == null || !d.enabled) return;
        PassiveProfile prof = pl.demons.profile(d.id);
        active.put(p.getUniqueId(), prof);
        for (Map.Entry<String, Double> e : prof.attributes.entrySet()) {
            Attribute a = Mc.attribute(e.getKey());
            AttributeInstance inst = a == null ? null : p.getAttribute(a);
            if (inst == null) continue;
            NamespacedKey key = pl.key("passive_" + e.getKey().replace('.', '_'));
            // attributs dont la base vaut 0 : un modificateur proportionnel n'aurait aucun effet -> additif
            boolean flat = FLAT_ATTRIBUTES.contains(e.getKey());
            inst.addModifier(new AttributeModifier(key, e.getValue(),
                    flat ? AttributeModifier.Operation.ADD_NUMBER : AttributeModifier.Operation.ADD_SCALAR));
        }
        Set<PotionEffectType> applied = new HashSet<>();
        for (PotionEffect pe : prof.permEffects) {
            p.addPotionEffect(pe);
            applied.add(pe.getType());
        }
        appliedEffects.put(p.getUniqueId(), applied);
    }

    public void clear(Player p) {
        active.remove(p.getUniqueId());
        for (Attribute a : knownAttributes()) {
            AttributeInstance inst = a == null ? null : p.getAttribute(a);
            if (inst == null) continue;
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                if (m.getKey().getNamespace().equals(pl.key("x").getNamespace()) && m.getKey().getKey().startsWith("passive_")) inst.removeModifier(m);
            }
        }
        Set<PotionEffectType> applied = appliedEffects.remove(p.getUniqueId());
        if (applied != null) for (PotionEffectType t : applied) p.removePotionEffect(t);
    }

    public void clearAll() {
        for (Player p : pl.getServer().getOnlinePlayers()) clear(p);
    }

    private List<Attribute> knownAttributes() {
        List<Attribute> l = new ArrayList<>();
        for (String id : new String[]{"movement_speed", "attack_speed", "knockback_resistance", "max_health", "water_movement_efficiency",
                "attack_damage", "armor", "jump_strength", "step_height", "safe_fall_distance", "scale"}) {
            Attribute a = Mc.attribute(id);
            if (a != null) l.add(a);
        }
        return l;
    }

    /** Effets périodiques : régénération rapide, vitesse dans l'eau. */
    private void cycle() {
        for (Map.Entry<UUID, PassiveProfile> e : active.entrySet()) {
            Player p = pl.getServer().getPlayer(e.getKey());
            if (p == null || p.isDead()) continue;
            PassiveProfile prof = e.getValue();
            if (prof.regenPerCycle > 0) {
                double max = Mc.maxHealth(p);
                if (p.getHealth() < max) p.setHealth(Math.min(max, p.getHealth() + prof.regenPerCycle));
            }
            if (prof.waterSpeedAmp >= 0 && p.isInWater()) {
                PotionEffectType t = Mc.effect("dolphins_grace");
                if (t != null) p.addPotionEffect(new PotionEffect(t, 80, (int) prof.waterSpeedAmp, true, false, false));
            }
        }
    }
}
