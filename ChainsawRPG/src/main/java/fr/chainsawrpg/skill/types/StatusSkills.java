package fr.chainsawrpg.skill.types;

import fr.chainsawrpg.skill.Cast;
import fr.chainsawrpg.skill.SkillManager;
import fr.chainsawrpg.util.Fx;
import fr.chainsawrpg.util.Mc;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** ROOT, MARK, MARK_STRIKE, REVEAL, CAGE. */
public final class StatusSkills {
    private StatusSkills() {}

    public static void register(SkillManager m) {
        m.register("ROOT", StatusSkills::root);
        m.register("MARK", StatusSkills::mark);
        m.register("MARK_STRIKE", StatusSkills::markStrike);
        m.register("REVEAL", StatusSkills::reveal);
        m.register("CAGE", StatusSkills::cage);
    }

    /**
     * Immobilisation (durée plafonnée en config, puis immunité). params: mode SINGLE|AREA, center, dot (dégâts/s), effects.
     */
    static boolean root(Cast c) {
        Player p = c.p;
        boolean area = c.s("mode", "SINGLE").equalsIgnoreCase("AREA");
        List<LivingEntity> ts = new ArrayList<>();
        if (!area) {
            LivingEntity t = c.aim();
            if (t == null) return c.fail("no-target");
            ts.add(t);
        } else {
            Location ctr = Area.center(c, c.s("center", "AIM"));
            if (ctr == null) return c.fail("no-target");
            ts = c.enemies(ctr, c.def.radius);
            if (ts.isEmpty()) return c.fail("no-target");
            Fx.ring(ctr, c.def.radius, c.def.particle, 40);
        }
        c.castFx(p.getLocation());
        double dot = c.d("dot", 0);
        boolean any = false;
        for (LivingEntity t : ts) {
            Location l = t.getLocation().add(0, t.getHeight() / 2, 0);
            if (!c.pl.status.root(t, p.getUniqueId(), c.def.duration, dot, c.def.particle)) {
                Fx.particle(l, "SMOKE", 8, 0.3, 0.4, 0.3, 0.02);
                continue;
            }
            any = true;
            if (c.def.dmgPvp > 0 || c.def.dmgPve > 0) c.hit(t);
            c.effects(t);
            Fx.line(p.getEyeLocation().add(0, -0.3, 0), l, c.def.particle, 0.6);
            c.burstFx(l, 25, 0.5);
        }
        return any || c.fail("target-immune");
    }

    /** Pose des marques. params: status (curse|judge|lock), stacks, max-stacks. radius>0 : toutes les cibles autour du lanceur. */
    static boolean mark(Cast c) {
        Player p = c.p;
        String key = c.s("status", "curse");
        int add = c.i("stacks", 1), max = c.i("max-stacks", 5);
        long ms = (long) (c.def.duration * 1000);
        List<LivingEntity> ts = new ArrayList<>();
        if (c.def.radius > 0) {
            ts = c.enemies(p.getLocation(), c.def.radius);
        } else {
            LivingEntity t = c.aim();
            if (t != null) ts.add(t);
        }
        if (ts.isEmpty()) return c.fail("no-target");
        c.castFx(p.getLocation());
        for (LivingEntity t : ts) {
            c.pl.status.apply(t, key, p.getUniqueId(), add, max, ms, 0);
            if (c.def.dmgPvp > 0 || c.def.dmgPve > 0) c.hit(t);
            Fx.line(p.getEyeLocation().add(0, -0.3, 0), t.getLocation().add(0, t.getHeight() / 2, 0), c.def.particle, 0.7);
            c.burstFx(t.getLocation().add(0, t.getHeight() + 0.4, 0), 12, 0.25);
        }
        return true;
    }

    /**
     * Frappe les cibles marquées par le lanceur. params: status, min-stacks, consume, all (toutes les cibles marquées à portée),
     * base-mult, per-stack (bonus de dégâts par marque), effects.
     */
    static boolean markStrike(Cast c) {
        Player p = c.p;
        UUID me = p.getUniqueId();
        String key = c.s("status", "curse");
        int min = c.i("min-stacks", 1);
        boolean consume = c.b("consume", true), all = c.b("all", false);
        double base = c.d("base-mult", 1.0), per = c.d("per-stack", 0.25);
        List<LivingEntity> ts = new ArrayList<>();
        if (all) {
            for (LivingEntity e : c.pl.status.withStatus(me, key, p.getLocation(), c.range())) {
                if (c.pl.status.stacks(e, key, me) >= min && c.pl.protection.valid(p, e) && ts.size() < c.def.maxTargets) ts.add(e);
            }
            ts.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation())));
        } else {
            LivingEntity t = c.aim();
            if (t != null && c.pl.status.stacks(t, key, me) >= min) ts.add(t);
        }
        if (ts.isEmpty()) return c.fail(all ? "no-marked" : "not-marked");
        c.castFx(p.getLocation());
        for (LivingEntity t : ts) {
            int st = c.pl.status.stacks(t, key, me);
            Fx.column(t.getLocation(), 7, c.def.particle);
            c.burstFx(t.getLocation().add(0, 1, 0), 30, 0.5);
            Fx.sound(t.getLocation(), c.def.sound, 1f, (float) c.def.pitch);
            c.hit(t, c.dmg(t) * (base + per * st));
            c.effects(t);
            if (consume) c.pl.status.remove(t, key);
        }
        return true;
    }

    /** Détection visible uniquement par le lanceur (particules envoyées à lui seul). params: players-only, arrows. */
    static boolean reveal(Cast c) {
        Player p = c.p;
        double r = c.def.radius;
        boolean playersOnly = c.b("players-only", false), arrows = c.b("arrows", false);
        int dur = (int) (c.def.duration * 20);
        c.castFx(p.getLocation());
        report(c, scan(c, r, playersOnly), arrows);
        new BukkitRunnable() {
            int t = 0;

            @Override public void run() {
                if (!p.isOnline() || p.isDead() || t >= dur) {
                    cancel();
                    return;
                }
                t += 10;
                List<LivingEntity> l = scan(c, r, playersOnly);
                for (LivingEntity e : l) Fx.particleTo(p, e.getLocation().add(0, e.getHeight() + 0.6, 0), c.def.particle, 3, 0.1, 0.1, 0.1, 0);
                if (t % 40 == 0) Fx.soundTo(p, c.def.sound, 0.4f, (float) c.def.pitch);
                if (arrows && t % 20 == 0) report(c, l, true);
            }
        }.runTaskTimer(c.pl, 10, 10);
        return true;
    }

    private static List<LivingEntity> scan(Cast c, double r, boolean playersOnly) {
        Player p = c.p;
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.getWorld().getNearbyLivingEntities(p.getLocation(), r)) {
            if (!c.pl.protection.valid(p, e)) continue;
            if (playersOnly && !(e instanceof Player)) continue;
            if (e.getLocation().distanceSquared(p.getLocation()) <= r * r) out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation())));
        return out.size() > 30 ? new ArrayList<>(out.subList(0, 30)) : out;
    }

    private static void report(Cast c, List<LivingEntity> l, boolean arrows) {
        Player p = c.p;
        if (l.isEmpty()) {
            c.pl.msg.feedback(p, "reveal-none");
            return;
        }
        LivingEntity n = l.get(0);
        Vector to = n.getLocation().toVector().subtract(p.getLocation().toVector());
        double ang = Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
        double rel = ((ang - p.getLocation().getYaw()) % 360 + 540) % 360 - 180;
        String[] dirs = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        String dir = arrows ? dirs[((int) Math.round(rel / 45.0) + 8) % 8] : "";
        c.pl.msg.feedback(p, "reveal-report", "count", l.size(), "dist", String.format(Locale.ROOT, "%.0f", p.getLocation().distance(n.getLocation())), "dir", dir);
    }

    /** Cage temporaire de blocs autour de la cible (uniquement dans l'air / blocs remplaçables, restaurée ensuite). params: material. */
    static boolean cage(Cast c) {
        LivingEntity t = c.aim();
        if (t == null) return c.fail("no-target");
        Material m = Mc.material(c.s("material", "STONE_BRICKS"), Material.STONE_BRICKS);
        long ticks = (long) (c.def.duration * 20);
        Location b = t.getLocation().getBlock().getLocation();
        int placed = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    boolean shell = Math.abs(dx) == 1 || Math.abs(dz) == 1 || dy == -1 || dy == 2;
                    if (shell && c.pl.tempBlocks.set(b.clone().add(dx, dy, dz), m, ticks)) placed++;
                }
            }
        }
        if (placed == 0) return c.fail("no-space");
        c.castFx(t.getLocation());
        Fx.ring(t.getLocation(), 1.5, c.def.particle, 24);
        if (c.def.dmgPvp > 0 || c.def.dmgPve > 0) c.hit(t);
        c.effects(t);
        return true;
    }
}
