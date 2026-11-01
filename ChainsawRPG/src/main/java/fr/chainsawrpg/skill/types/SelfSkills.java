package fr.chainsawrpg.skill.types;

import fr.chainsawrpg.skill.Cast;
import fr.chainsawrpg.skill.Combat;
import fr.chainsawrpg.skill.SkillManager;
import fr.chainsawrpg.util.Fx;
import fr.chainsawrpg.util.Mc;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BUFF_SELF : effets sur soi, absorption, soin, charges d'esquive, bonus d'arme, impulsion, aura offensive, vêtements fantômes.
 * params: effects, absorption, heal, dodge, weapon-items, weapon-bonus, launch, launch-forward,
 * aura-damage, aura-radius, armor (4 matériaux), armor-color.
 */
public final class SelfSkills {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private SelfSkills() {}

    public static void register(SkillManager m) { m.register("BUFF_SELF", SelfSkills::buff); }

    private static boolean matches(Material m, List<String> patterns) {
        for (String s : patterns) {
            if (s.startsWith("*") ? m.name().endsWith(s.substring(1).toUpperCase()) : m.name().equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    static boolean buff(Cast c) {
        Player p = c.p;
        long dur = (long) (c.def.duration * 20);
        List<String> weaponItems = c.def.list("weapon-items");
        if (!weaponItems.isEmpty() && !matches(p.getInventory().getItemInMainHand().getType(), weaponItems)) return c.fail("bad-item");

        c.castFx(p.getLocation());
        Fx.ring(p.getLocation(), 1.2, c.def.particle, 24);
        Fx.particle(p.getLocation().add(0, 1, 0), c.def.particle, 30, 0.4, 0.8, 0.4, 0.05);

        for (PotionEffect pe : c.def.effects("effects")) {
            double mult = c.pp.effectDurationMult.getOrDefault(pe.getType().getKey().getKey(), 1.0);
            p.addPotionEffect(new PotionEffect(pe.getType(), (int) (pe.getDuration() * mult), pe.getAmplifier(), false, false, true));
        }
        double absorb = c.d("absorption", 0);
        if (absorb > 0) {
            p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), absorb));
            new BukkitRunnable() {
                @Override public void run() {
                    if (p.isOnline() && p.getAbsorptionAmount() > 0) p.setAbsorptionAmount(Math.max(0, p.getAbsorptionAmount() - absorb));
                }
            }.runTaskLater(c.pl, Math.max(20, dur));
        }
        double heal = c.d("heal", 0);
        if (heal > 0) p.setHealth(Math.min(Mc.maxHealth(p), p.getHealth() + heal));

        Combat.Buffs b = c.pl.combat.buffs(p.getUniqueId());
        int dodge = c.i("dodge", 0);
        if (dodge > 0) {
            b.dodge = dodge;
            b.dodgeUntil = System.currentTimeMillis() + dur * 50;
        }
        if (!weaponItems.isEmpty()) {
            Set<Material> mats = new HashSet<>();
            for (Material m : Material.values()) if (!m.isLegacy() && matches(m, weaponItems)) mats.add(m);
            b.weaponMats = mats;
            b.weaponBonus = c.d("weapon-bonus", 0.5);
            b.weaponUntil = System.currentTimeMillis() + dur * 50;
        }
        double launch = c.d("launch", 0), lf = c.d("launch-forward", 0);
        if (launch > 0 || lf > 0) {
            Vector v = p.getVelocity();
            Vector d = p.getLocation().getDirection().setY(0);
            if (d.lengthSquared() > 0.01 && lf > 0) v.add(d.normalize().multiply(lf));
            if (launch > 0) v.setY(launch);
            p.setVelocity(v);
        }
        double aura = c.d("aura-damage", 0);
        List<String> armor = c.def.list("armor");
        if (aura > 0 || armor.size() == 4) startAura(c, dur, aura, c.d("aura-radius", 4), armor);
        return true;
    }

    private static ItemStack fake(Material m, String color) {
        ItemStack it = new ItemStack(m);
        if (it.getItemMeta() instanceof LeatherArmorMeta lm) {
            lm.setColor(Mc.color(color));
            it.setItemMeta(lm);
        }
        return it;
    }

    private static void sendArmor(Player p, ItemStack[] fakes, boolean restore) {
        for (Player v : p.getWorld().getPlayers()) {
            if (v.getLocation().distanceSquared(p.getLocation()) > 48 * 48) continue;
            for (int i = 0; i < 4; i++) {
                ItemStack it = restore ? p.getInventory().getItem(SLOTS[i]) : fakes[i];
                v.sendEquipmentChange(p, SLOTS[i], it == null ? new ItemStack(Material.AIR) : it);
            }
        }
    }

    private static void startAura(Cast c, long dur, double dps, double radius, List<String> armor) {
        Player p = c.p;
        ItemStack[] fakes = null;
        if (armor.size() == 4) {
            fakes = new ItemStack[4];
            String color = c.s("armor-color", "255,60,0");
            for (int i = 0; i < 4; i++) fakes[i] = fake(Mc.material(armor.get(i), Material.LEATHER_HELMET), color);
        }
        final ItemStack[] fk = fakes;
        new BukkitRunnable() {
            long el = 0;

            @Override public void run() {
                if (!p.isOnline() || p.isDead() || el >= dur) {
                    if (fk != null && p.isOnline()) sendArmor(p, null, true);
                    cancel();
                    return;
                }
                el += 10;
                Location l = p.getLocation();
                Fx.ring(l, 1.0, c.def.particle, 14);
                Fx.particle(l.clone().add(0, 1, 0), c.def.particle, 8, 0.4, 0.8, 0.4, 0.02);
                if (fk != null && el % 20 == 0) sendArmor(p, fk, false);
                if (dps > 0) {
                    Fx.ring(l, radius, c.def.particle, 28);
                    for (LivingEntity e : c.enemies(l, radius)) c.hit(e, dps * 0.5 * (e instanceof Player ? 1.0 : 1.5));
                }
            }
        }.runTaskTimer(c.pl, 0, 10);
    }
}
