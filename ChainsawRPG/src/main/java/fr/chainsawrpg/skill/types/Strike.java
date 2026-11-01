package fr.chainsawrpg.skill.types;

import fr.chainsawrpg.skill.Cast;
import fr.chainsawrpg.skill.SkillManager;
import fr.chainsawrpg.util.Fx;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** RAY_STRIKE, SUMMON_BITE, MULTI_HIT, MELEE_ARC, LEAP. */
public final class Strike {
    private Strike() {}

    public static void register(SkillManager m) {
        m.register("RAY_STRIKE", Strike::ray);
        m.register("SUMMON_BITE", Strike::bite);
        m.register("MULTI_HIT", Strike::multi);
        m.register("MELEE_ARC", Strike::arc);
        m.register("LEAP", Strike::leap);
    }

    /** Frappe instantanée à distance sur la cible visée. params: knockback, pull, lift. radius>0 : éclaboussure à 60%. */
    static boolean ray(Cast c) {
        LivingEntity t = c.aim();
        if (t == null) return c.fail("no-target");
        Player p = c.p;
        Location from = p.getEyeLocation().add(0, -0.3, 0);
        Location to = t.getLocation().add(0, t.getHeight() / 2, 0);
        Fx.line(from, to, c.def.particle, 0.5);
        c.castFx(from);
        c.burstFx(to, 20, 0.4);
        c.hit(t);
        double kb = c.d("knockback", 0), pull = c.d("pull", 0), lift = c.d("lift", 0);
        if (pull > 0) {
            Vector v = p.getLocation().toVector().subtract(t.getLocation().toVector());
            v.setY(v.getY() * 0.3 + 0.25);
            if (v.lengthSquared() > 0.01) c.knock(t, v.normalize().multiply(pull));
        }
        if (kb > 0) c.knockFrom(t, p.getLocation(), kb, 0.25);
        if (lift > 0) c.knock(t, new Vector(0, lift, 0));
        c.effects(t);
        if (c.def.radius > 0) {
            for (LivingEntity o : c.enemies(t.getLocation(), c.def.radius)) if (!o.equals(t)) c.hit(o, c.dmg(o) * 0.6);
        }
        return true;
    }

    /** Tête invoquée qui voyage jusqu'à la cible puis mord. params: travel (ticks), knockback. */
    static boolean bite(Cast c) {
        LivingEntity t = c.aim();
        if (t == null) return c.fail("no-target");
        Player p = c.p;
        Location start = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.8));
        int travel = Math.max(2, c.i("travel", 6));
        double kb = c.d("knockback", 0);
        c.castFx(start);
        new BukkitRunnable() {
            int tick = 0;

            @Override public void run() {
                if (!t.isValid() || t.isDead() || !p.isOnline()) {
                    cancel();
                    return;
                }
                tick++;
                Location end = t.getEyeLocation();
                Vector v = end.toVector().subtract(start.toVector()).multiply((double) tick / travel);
                Location cur = start.clone().add(v);
                Fx.particle(cur, c.def.particle, 12, 0.3, 0.25, 0.3, 0.02);
                Fx.ring(cur.clone().add(0, -0.15, 0), 0.45, c.def.particle, 8);
                if (tick >= travel) {
                    cancel();
                    c.burstFx(end, 25, 0.5);
                    Fx.sound(end, c.def.sound, 1f, (float) c.def.pitch);
                    c.hit(t);
                    if (kb > 0) c.knockFrom(t, p.getLocation(), kb, 0.2);
                    c.effects(t);
                }
            }
        }.runTaskTimer(c.pl, 1, 1);
        return true;
    }

    /**
     * Plusieurs coups espacés. params: hits, interval (ticks, >=4), mode TARGET|NEAREST|AREA, style BURST|COLUMN,
     * knockback-last, effects (appliqués au dernier coup). AREA : chaque vague frappe tous les ennemis dans radius.
     */
    static boolean multi(Cast c) {
        String mode = c.s("mode", "TARGET").toUpperCase(Locale.ROOT);
        int hits = Math.max(1, Math.min(c.i("hits", 3), 12));
        int interval = Math.max(4, c.i("interval", 4));
        boolean area = mode.equals("AREA");
        Player p = c.p;
        List<LivingEntity> pool = new ArrayList<>();
        LivingEntity aimed = c.aim();
        if (mode.equals("TARGET")) {
            if (aimed == null) return c.fail("no-target");
            pool.add(aimed);
        } else if (mode.equals("NEAREST")) {
            if (aimed != null) pool.add(aimed);
            for (LivingEntity o : c.enemies(p.getLocation(), c.range())) if (!pool.contains(o) && pool.size() < c.def.maxTargets) pool.add(o);
            if (pool.isEmpty()) return c.fail("no-target");
        } else if (c.enemies(p.getLocation(), c.def.radius).isEmpty()) {
            return c.fail("no-target");
        }
        double kbLast = c.d("knockback-last", 0);
        boolean column = c.s("style", "BURST").equalsIgnoreCase("COLUMN");
        c.castFx(p.getLocation());
        new BukkitRunnable() {
            int i = 0;

            @Override public void run() {
                if (!p.isOnline() || i >= hits) {
                    cancel();
                    return;
                }
                boolean last = i == hits - 1;
                List<LivingEntity> now = area ? c.enemies(p.getLocation(), c.def.radius) : List.of(pool.get(i % pool.size()));
                for (LivingEntity e : now) {
                    if (!e.isValid() || e.isDead()) continue;
                    if (!area && e.getLocation().distanceSquared(p.getLocation()) > Math.pow(c.range() * 1.5, 2)) continue;
                    Location l = e.getLocation().add(0, e.getHeight() / 2, 0);
                    if (column) Fx.column(e.getLocation(), 3.5, c.def.particle);
                    c.burstFx(l, 14, 0.4);
                    Fx.sound(l, c.def.sound, 0.8f, (float) (c.def.pitch + 0.1 * (i % 3)));
                    c.hit(e);
                    if (last) {
                        if (kbLast > 0) c.knockFrom(e, p.getLocation(), kbLast, 0.3);
                        c.effects(e);
                    }
                }
                i++;
            }
        }.runTaskTimer(c.pl, 0, interval);
        return true;
    }

    /** Attaque en arc devant le joueur. params: angle (deg), knockback, lunge, up. */
    static boolean arc(Cast c) {
        Player p = c.p;
        double range = c.range();
        double half = Math.toRadians(c.d("angle", 90) / 2.0);
        double kb = c.d("knockback", 0.4), up = c.d("up", 0.2), lunge = c.d("lunge", 0);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().setY(0);
        if (dir.lengthSquared() < 0.0001) dir = new Vector(0, 0, 1);
        dir.normalize();
        c.castFx(eye);
        // visuel : balayage
        int steps = Math.max(6, (int) (Math.toDegrees(half) / 6));
        for (int s = -steps; s <= steps; s++) {
            double a = half * s / steps;
            Vector d = dir.clone().rotateAroundY(a);
            for (double r = range * 0.4; r <= range; r += range * 0.3) Fx.particle(eye.clone().add(d.clone().multiply(r)).add(0, -0.5, 0), c.def.particle, 1, 0.05, 0.05, 0.05, 0);
        }
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity e : c.enemies(p.getLocation(), range)) {
            Vector to = e.getLocation().toVector().subtract(p.getLocation().toVector());
            if (Math.abs(to.getY()) > 2.8) continue;
            to.setY(0);
            if (to.lengthSquared() < 0.01 || dir.angle(to.normalize()) <= half) targets.add(e);
        }
        if (lunge > 0) p.setVelocity(p.getVelocity().add(dir.clone().multiply(lunge)).setY(0.15));
        for (LivingEntity e : targets) {
            c.hit(e);
            if (kb > 0 || up > 0) c.knockFrom(e, p.getLocation(), kb, up);
            c.effects(e);
            c.burstFx(e.getLocation().add(0, e.getHeight() / 2, 0), 10, 0.3);
        }
        return true;
    }

    /**
     * Bond physique (vélocité, aucune téléportation). params: mode TARGET|FORWARD, speed, lift, max-ticks,
     * burst-start (rayon d'explosion au départ), land-radius (rayon à l'atterrissage), knockback.
     */
    static boolean leap(Cast c) {
        Player p = c.p;
        boolean toTarget = c.s("mode", "TARGET").equalsIgnoreCase("TARGET");
        LivingEntity t = null;
        Vector dir;
        if (toTarget) {
            t = c.aim();
            if (t == null) return c.fail("no-target");
            dir = t.getLocation().add(0, 0.5, 0).toVector().subtract(p.getLocation().toVector());
        } else {
            dir = p.getLocation().getDirection().setY(0);
        }
        if (dir.lengthSquared() < 0.0001) dir = new Vector(0, 0, 1);
        double speed = c.d("speed", 1.4);
        Vector v = dir.clone().normalize().multiply(speed);
        v.setY(v.getY() * 0.6 + c.d("lift", 0.35));
        p.setVelocity(v);
        c.pl.combat.buffs(p.getUniqueId()).leapUntil = System.currentTimeMillis() + 1800;
        c.castFx(p.getLocation());
        double startR = c.d("burst-start", 0), landR = c.d("land-radius", 0), kb = c.d("knockback", 0.5);
        if (startR > 0) Area.burstAt(c, p.getLocation(), startR, 1.0, "EXPLOSION", kb, 0.4, 0);
        final LivingEntity target = t;
        int max = c.i("max-ticks", 20);
        new BukkitRunnable() {
            int tick = 0;
            boolean done = false;

            @Override public void run() {
                tick++;
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                Fx.particle(p.getLocation().add(0, 0.8, 0), c.def.particle, 6, 0.25, 0.3, 0.25, 0.02);
                if (target != null && !done && target.isValid() && p.getLocation().distanceSquared(target.getLocation()) < 5.5) {
                    done = true;
                    c.hit(target);
                    c.knockFrom(target, p.getLocation(), kb, 0.3);
                    c.effects(target);
                    c.burstFx(target.getLocation().add(0, 1, 0), 25, 0.5);
                    Fx.sound(target.getLocation(), c.def.sound, 1f, (float) c.def.pitch);
                    if (c.def.radius > 0) for (LivingEntity o : c.enemies(target.getLocation(), c.def.radius)) if (!o.equals(target)) c.hit(o, c.dmg(o) * 0.6);
                }
                boolean landed = tick > 3 && p.isOnGround();
                if (landed || tick >= max) {
                    if (target == null && landR > 0 && landed) Area.burstAt(c, p.getLocation(), landR, 1.0, "EXPLOSION", kb, 0.4, 0);
                    cancel();
                }
            }
        }.runTaskTimer(c.pl, 2, 1);
        return true;
    }
}
