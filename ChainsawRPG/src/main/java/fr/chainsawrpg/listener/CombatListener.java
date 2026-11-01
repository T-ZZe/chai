package fr.chainsawrpg.listener;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.demon.PassiveProfile;
import fr.chainsawrpg.demon.SkillDef;
import fr.chainsawrpg.skill.Combat;
import fr.chainsawrpg.util.Fx;
import fr.chainsawrpg.util.Mc;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.concurrent.ThreadLocalRandom;

/** Passifs défensifs/offensifs, esquives, bonus d'arme, marques et ralentissements à l'impact. */
public final class CombatListener implements Listener {
    private final ChainsawPlugin pl;

    public CombatListener(ChainsawPlugin pl) { this.pl = pl; }

    private static Player resolve(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr) {
            ProjectileSource s = pr.getShooter();
            if (s instanceof Player p) return p;
        }
        return null;
    }

    /** Résistances par cause de dégâts et réduction globale. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PassiveProfile pp = pl.passives.profile(p);
        if (pp == null) return;
        Double m = pp.causeMult.get(e.getCause());
        if (m != null) {
            if (m <= 0) {
                e.setCancelled(true);
                return;
            }
            e.setDamage(e.getDamage() * m);
        }
        if (pp.resistAll > 0) e.setDamage(e.getDamage() * Math.max(0, 1 - pp.resistAll));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        if (pl.npcs.isNpc(victim)) {
            if (!(e.getDamager() instanceof Player a && a.hasPermission("chainsawman.admin"))) e.setCancelled(true);
            return;
        }
        long now = System.currentTimeMillis();
        boolean dot = pl.combat.isDot();

        if (victim instanceof Player v && !dot) {
            Combat.Buffs b = pl.combat.peek(v.getUniqueId());
            PassiveProfile vp = pl.passives.profile(v);
            boolean dodged = false;
            if (b != null && b.dodge > 0 && now < b.dodgeUntil) {
                b.dodge--;
                dodged = true;
            } else if (vp != null && vp.dodgeChance > 0 && ThreadLocalRandom.current().nextDouble() < vp.dodgeChance) {
                dodged = true;
            }
            if (dodged) {
                Fx.particle(v.getLocation().add(0, 1, 0), "CLOUD", 20, 0.4, 0.6, 0.4, 0.05);
                Fx.sound(v.getLocation(), "entity.enderman.teleport", 0.6f, 1.6f);
                e.setCancelled(true);
                return;
            }
        }

        Player attacker = resolve(e.getDamager());
        if (attacker == null || dot || attacker.equals(victim)) return;
        PassiveProfile ap = pl.passives.profile(attacker);
        SkillDef cur = pl.combat.current();
        Combat.Buffs ab = pl.combat.peek(attacker.getUniqueId());
        double mult = 1.0;
        if (ap != null) {
            double dsq = attacker.getLocation().distanceSquared(victim.getLocation());
            if (ap.closeRange > 0 && dsq <= ap.closeRangeDist * ap.closeRangeDist) mult += ap.closeRange;
            if (ap.airBonus > 0 && (!attacker.isOnGround() || (ab != null && ab.leapUntil > now))) mult += ap.airBonus;
            if (cur != null && ap.summonBonus > 0 && cur.tag("summon")) mult += ap.summonBonus;
            if (ap.fireBonus > 0 && ((cur != null && cur.tag("fire")) || victim.getFireTicks() > 0)) mult += ap.fireBonus;
            if (ap.markedBonus > 0 && pl.status.stacks(victim, ap.markedStatus, attacker.getUniqueId()) > 0) mult += ap.markedBonus;
            if (cur != null && ap.weaponBonus > 0 && cur.tag("weapon")) mult += ap.weaponBonus;
        }
        if (ab != null && ab.weaponUntil > now && cur == null && ab.weaponMats.contains(attacker.getInventory().getItemInMainHand().getType())) {
            mult += ab.weaponBonus;
            Fx.particle(victim.getLocation().add(0, 1, 0), "CRIT", 12, 0.3, 0.5, 0.3, 0.2);
        }
        if (mult != 1.0) e.setDamage(e.getDamage() * mult);

        if (ap != null && victim instanceof LivingEntity le && pl.protection.valid(attacker, le)) {
            if (ap.markOnHit > 0) pl.status.apply(le, ap.markStatus, attacker.getUniqueId(), ap.markOnHit, 5, 10_000, 0);
            if (ap.slowOnHitAmp >= 0 && ap.slowOnHitTicks > 0) {
                PotionEffectType slow = Mc.effect("slowness");
                if (slow != null) le.addPotionEffect(new PotionEffect(slow, ap.slowOnHitTicks, ap.slowOnHitAmp, false, true, true));
            }
        }
    }

    /** Immunités et résistances aux effets de potion. */
    @EventHandler(ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PassiveProfile pp = pl.passives.profile(p);
        if (pp == null) return;
        EntityPotionEffectEvent.Action a = e.getAction();
        if (a != EntityPotionEffectEvent.Action.ADDED && a != EntityPotionEffectEvent.Action.CHANGED) return;
        PotionEffect ne = e.getNewEffect();
        if (ne == null) return;
        String id = ne.getType().getKey().getKey();
        if (pp.immuneEffects.contains(id)) {
            e.setCancelled(true);
            return;
        }
        if (pp.slowResistMaxAmp >= 0 && id.equals("slowness") && ne.getAmplifier() <= pp.slowResistMaxAmp) e.setCancelled(true);
    }
}
