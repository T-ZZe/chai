package fr.chainsawrpg.skill.types;

import fr.chainsawrpg.skill.Cast;
import fr.chainsawrpg.skill.SkillManager;
import fr.chainsawrpg.util.Fx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** AOE_BURST, EFFECT_AREA, ZONE. */
public final class Area {
    private Area() {}

    public static void register(SkillManager m) {
        m.register("AOE_BURST", Area::aoe);
        m.register("EFFECT_AREA", Area::effectArea);
        m.register("ZONE", Area::zone);
    }

    static Location center(Cast c, String mode) {
        switch (mode.toUpperCase(Locale.ROOT)) {
            case "SELF": return c.p.getLocation();
            case "TARGET": {
                LivingEntity t = c.aim();
                return t == null ? null : t.getLocation();
            }
            default: return c.aimPoint();
        }
    }

    // ---------------- visuels ----------------
    static void style(Cast c, String style, Location l, double r) {
        String part = c.def.particle;
        Location up = l.clone().add(0, 0.5, 0);
        switch (style.toUpperCase(Locale.ROOT)) {
            case "EXPLOSION" -> {
                Fx.particle(up, "EXPLOSION_EMITTER", 1, 0, 0, 0, 0);
                Fx.particle(up, part, 40, r * 0.4, 0.5, r * 0.4, 0.1);
                Fx.ring(l, r, part, 36);
                Fx.sound(l, "entity.generic.explode", 1f, 1f);
            }
            case "PILLAR" -> {
                Fx.column(l, 9, part);
                Fx.ring(l, r, part, 36);
                Fx.particle(l.clone().add(0, 1, 0), part, 40, r * 0.3, 1.0, r * 0.3, 0.05);
            }
            case "SLAM" -> {
                for (int k = 1; k <= 3; k++) Fx.ring(l, r * k / 3.0, part, 20 + 8 * k);
                Fx.particle(up, "BLOCK:STONE", 40, r * 0.5, 0.3, r * 0.5, 0.1);
            }
            case "HEAD" -> {
                for (int h = 0; h < 4; h++) Fx.ring(l.clone().add(0, h, 0), r * (1 - h * 0.2), part, 30);
                Fx.column(l.clone().add(r * 0.6, 0, r * 0.6), 5, part);
                Fx.column(l.clone().add(-r * 0.6, 0, r * 0.6), 5, part);
                Fx.particle(l.clone().add(r * 0.3, 2.6, r * 0.7), "END_ROD", 6, 0.1, 0.1, 0.1, 0);
                Fx.particle(l.clone().add(-r * 0.3, 2.6, r * 0.7), "END_ROD", 6, 0.1, 0.1, 0.1, 0);
            }
            case "SPIN" -> {
                for (int k = 1; k <= 3; k++) Fx.ring(l.clone().add(0, 0.6 * k, 0), r * k / 3.0, part, 28);
            }
            default -> {
                Fx.ring(l, r, part, 36);
                Fx.particle(up, part, 30, r * 0.4, 0.4, r * 0.4, 0.05);
            }
        }
    }

    /** Explosion de zone instantanée : dégâts, recul radial, feu, effets sur les ennemis valides. */
    public static void burstAt(Cast c, Location l, double r, double mult, String style, double kb, double up, int fireTicks) {
        style(c, style, l, r);
        Fx.sound(l, c.def.sound, 1f, (float) c.def.pitch);
        for (LivingEntity e : c.enemies(l, r)) {
            c.hit(e, c.dmg(e) * mult);
            if (kb > 0 || up > 0) c.knockFrom(e, l, kb, up);
            if (fireTicks > 0) e.setFireTicks(Math.max(e.getFireTicks(), fireTicks));
            c.effects(e);
        }
    }

    // ---------------- AOE_BURST ----------------
    /** params: center SELF|TARGET|AIM, delay (ticks), style, knockback, up, fire-ticks, self-damage, marker (bloc affiché). */
    static boolean aoe(Cast c) {
        double r = Math.max(0.5, c.def.radius);
        Location center = center(c, c.s("center", "AIM"));
        if (center == null) return c.fail("no-target");
        center = center.clone();
        int delay = c.i("delay", 0);
        String style = c.s("style", "RING");
        double kb = c.d("knockback", 0), up = c.d("up", 0), selfDmg = c.d("self-damage", 0);
        int fire = c.i("fire-ticks", 0);
        Player p = c.p;
        c.castFx(p.getLocation());
        final Location ctr = center;
        Runnable boom = () -> {
            if (!p.isOnline()) return;
            burstAt(c, ctr, r, 1.0, style, kb, up, fire);
            if (selfDmg > 0 && !p.isDead()) {
                double m = c.pp.causeMult.getOrDefault(DamageCause.ENTITY_EXPLOSION, 1.0);
                p.setHealth(Math.max(0.5, p.getHealth() - selfDmg * m));
            }
        };
        if (delay <= 0) {
            boom.run();
            return true;
        }
        BlockDisplay marker = null;
        String mat = c.s("marker", "");
        if (!mat.isEmpty()) {
            Material m = Material.matchMaterial(mat);
            if (m != null && m.isBlock()) {
                marker = ctr.getWorld().spawn(ctr.clone().add(-0.5, 0, -0.5), BlockDisplay.class, d -> {
                    d.setBlock(Bukkit.createBlockData(m));
                    d.setPersistent(false);
                });
                c.pl.temps.add(marker);
            }
        }
        final BlockDisplay mk = marker;
        new BukkitRunnable() {
            int t = 0;

            @Override public void run() {
                t += 2;
                if (t >= delay || !p.isOnline()) {
                    cancel();
                    if (mk != null) {
                        mk.remove();
                        c.pl.temps.remove(mk);
                    }
                    boom.run();
                } else if (t % 6 == 0) {
                    Fx.ring(ctr, r, "DUST:255,200,0:1.0", 24);
                    Fx.sound(ctr, "block.note_block.hat", 0.6f, 1.0f + t / 40f);
                }
            }
        }.runTaskTimer(c.pl, 0, 2);
        return true;
    }

    // ---------------- EFFECT_AREA ----------------
    /**
     * params: center, effects, status, status-seconds, status-power, spread (propage 'status'), spread-radius.
     * radius 0 = cible visée uniquement. Inflige aussi les dégâts de la compétence s'ils sont &gt; 0.
     */
    static boolean effectArea(Cast c) {
        Player p = c.p;
        double r = c.def.radius;
        boolean single = r <= 0;
        String status = c.s("status", "");
        long sMs = (long) (c.d("status-seconds", c.def.duration) * 1000);
        double sPow = c.d("status-power", 0);
        List<LivingEntity> targets = new ArrayList<>();
        Location center;
        if (c.b("spread", false)) {
            List<LivingEntity> src = c.pl.status.withStatus(p.getUniqueId(), status, p.getLocation(), c.range());
            if (src.isEmpty()) return c.fail("no-infected");
            double sr = c.d("spread-radius", 4);
            for (LivingEntity inf : src) {
                for (LivingEntity o : c.enemies(inf.getLocation(), sr)) {
                    if (!targets.contains(o) && !c.pl.status.has(o, status) && targets.size() < c.def.maxTargets) {
                        targets.add(o);
                        Fx.line(inf.getLocation().add(0, 1, 0), o.getLocation().add(0, 1, 0), c.def.particle, 0.5);
                    }
                }
            }
            if (targets.isEmpty()) return c.fail("no-target");
            center = p.getLocation();
        } else if (single) {
            LivingEntity t = c.aim();
            if (t == null) return c.fail("no-target");
            targets.add(t);
            center = t.getLocation();
        } else {
            center = center(c, c.s("center", "AIM"));
            if (center == null) return c.fail("no-target");
            targets = c.enemies(center, r);
            if (targets.isEmpty()) return c.fail("no-target");
            Fx.ring(center, r, c.def.particle, 40);
            Fx.particle(center.clone().add(0, 1, 0), c.def.particle, 60, r / 2, 1, r / 2, 0.02);
        }
        c.castFx(p.getLocation());
        boolean dmg = c.def.dmgPvp > 0 || c.def.dmgPve > 0;
        for (LivingEntity t : targets) {
            if (dmg) c.hit(t);
            c.effects(t);
            if (!status.isEmpty()) c.pl.status.apply(t, status, p.getUniqueId(), 1, 1, sMs, sPow);
            Fx.particle(t.getLocation().add(0, t.getHeight() / 2, 0), c.def.particle, 18, 0.35, 0.5, 0.35, 0.03);
        }
        return true;
    }

    // ---------------- ZONE ----------------
    /**
     * Zone persistante. duration (s) = durée, radius = rayon, les dégâts sont PAR SECONDE.
     * params: center SELF|AIM|TARGET, follow, boundary (retient les ennemis présents au départ), tick,
     * pull, lift, style RING|VORTEX|DOME|THREADS|DARK, effects (ennemis), self-effects (lanceur).
     */
    static boolean zone(Cast c) {
        Player p = c.p;
        Location base = center(c, c.s("center", "AIM"));
        if (base == null) return c.fail("no-target");
        if (!c.pl.skills.zoneStart(p.getUniqueId())) return c.fail("too-many");
        final Location fixed = base.clone();
        boolean follow = c.b("follow", false), boundary = c.b("boundary", false);
        int tick = Math.max(4, c.i("tick", 10));
        long total = (long) (c.def.duration * 20);
        double r = Math.max(1, c.def.radius);
        double pull = c.d("pull", 0), lift = c.d("lift", 0);
        String style = c.s("style", "RING");
        List<PotionEffect> eff = c.def.effects("effects"), self = c.def.effects("self-effects");
        Set<UUID> captured = new HashSet<>();
        Map<UUID, Location> lastIn = new HashMap<>();
        if (boundary) {
            for (LivingEntity e : c.enemies(fixed, r)) {
                captured.add(e.getUniqueId());
                lastIn.put(e.getUniqueId(), e.getLocation());
            }
        }
        c.castFx(p.getLocation());
        new BukkitRunnable() {
            long el = 0;
            int phase = 0;

            @Override public void run() {
                if (!p.isOnline() || p.isDead() || el >= total) {
                    c.pl.skills.zoneEnd(p.getUniqueId());
                    cancel();
                    return;
                }
                Location ctr = follow ? p.getLocation() : fixed;
                el += tick;
                phase++;
                visual(c, style, ctr, r, phase);
                for (UUID id : captured) {
                    Entity en = Bukkit.getEntity(id);
                    if (!(en instanceof LivingEntity) || !en.isValid()) continue;
                    if (en.getLocation().distanceSquared(fixed) > r * r) {
                        Location back = lastIn.get(id);
                        if (back != null) en.teleport(back);
                    } else {
                        lastIn.put(id, en.getLocation());
                    }
                }
                for (LivingEntity e : c.enemies(ctr, r)) {
                    double d = c.dmg(e) * tick / 20.0;
                    if (d > 0) c.hit(e, d);
                    for (PotionEffect pe : eff) {
                        int cap = e instanceof Player ? c.pl.settings.maxEffectPvp * 20 : Integer.MAX_VALUE;
                        e.addPotionEffect(new PotionEffect(pe.getType(), Math.min(cap, Math.max(pe.getDuration(), tick + 5)), pe.getAmplifier(), false, true, true));
                    }
                    if (pull > 0) {
                        Vector v = ctr.toVector().subtract(e.getLocation().toVector());
                        v.setY(0);
                        if (v.lengthSquared() > 0.25) c.knock(e, v.normalize().multiply(pull));
                    }
                    if (lift > 0) c.knock(e, new Vector(0, lift, 0));
                }
                if (!self.isEmpty() && (follow || p.getLocation().distanceSquared(ctr) <= r * r)) {
                    for (PotionEffect pe : self) p.addPotionEffect(new PotionEffect(pe.getType(), Math.max(pe.getDuration(), tick + 5), pe.getAmplifier(), false, true, true));
                }
            }
        }.runTaskTimer(c.pl, 0, tick);
        return true;
    }

    static void visual(Cast c, String style, Location ctr, double r, int phase) {
        String part = c.def.particle;
        switch (style.toUpperCase(Locale.ROOT)) {
            case "VORTEX" -> {
                for (int k = 0; k < 24; k++) {
                    double h = (k / 24.0) * Math.min(7, Math.max(3, r));
                    double a = phase * 0.7 + k * 0.55;
                    double rad = 0.6 + (h / 7.0) * r * 0.9;
                    Fx.particle(ctr.clone().add(Math.cos(a) * rad, h, Math.sin(a) * rad), part, 1, 0, 0, 0, 0);
                }
                Fx.ring(ctr, r, part, 20);
            }
            case "DOME" -> {
                for (int h = 0; h < 4; h++) Fx.ring(ctr.clone().add(0, h * (r / 4), 0), r * Math.sqrt(1 - Math.pow(h / 4.0, 2)), part, 18);
            }
            case "THREADS" -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                for (int k = 0; k < 4; k++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    Location a1 = ctr.clone().add(Math.cos(a) * r, 0.4, Math.sin(a) * r);
                    Location b1 = ctr.clone().add(-Math.cos(a) * r, 0.4, -Math.sin(a) * r);
                    Fx.line(a1, b1, part, 0.8);
                }
            }
            case "DARK" -> {
                Fx.particle(ctr.clone().add(0, 1, 0), part, 50, r * 0.5, 1.2, r * 0.5, 0.01);
                Fx.ring(ctr, r, part, 30);
            }
            default -> {
                Fx.ring(ctr, r, part, 30);
                Fx.particle(ctr.clone().add(0, 0.3, 0), part, 14, r * 0.5, 0.2, r * 0.5, 0.01);
            }
        }
    }
}
