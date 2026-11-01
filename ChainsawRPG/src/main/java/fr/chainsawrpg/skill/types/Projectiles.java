package fr.chainsawrpg.skill.types;

import fr.chainsawrpg.skill.Cast;
import fr.chainsawrpg.skill.SkillManager;
import fr.chainsawrpg.util.Fx;
import fr.chainsawrpg.util.Mc;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PROJECTILE : mode FORWARD (devant soi), AUTO (vers les ennemis proches), SKY (pluie depuis le ciel sur la zone visée).
 * params: count, stagger, spread, fan, speed, gravity, pierce, width, life, explode, explode-mult, hit-mult,
 * homing, fire-ticks, knockback, item, block, height, allow-empty.
 */
public final class Projectiles {
    private Projectiles() {}

    public static void register(SkillManager m) { m.register("PROJECTILE", Projectiles::cast); }

    private static final class P {
        Location pos;
        Vector vel;
        int pierce, life, startTick;
        double yawOff;
        LivingEntity target;
        Location skyPos;
        boolean started, done;
        final Set<UUID> hit = new HashSet<>();
        Display display;
    }

    static boolean cast(Cast c) {
        Player p = c.p;
        String mode = c.s("mode", "FORWARD").toUpperCase(Locale.ROOT);
        int count = Math.max(1, Math.min(c.i("count", 1), c.pl.settings.maxProjectiles));
        int stagger = Math.max(0, c.i("stagger", 0));
        double speed = Math.max(0.2, c.d("speed", 1.2));
        double spread = Math.toRadians(c.d("spread", 0));
        double gravity = c.d("gravity", 0), width = c.d("width", 0.6), homing = Math.min(0.5, c.d("homing", 0));
        int pierceCfg = c.i("pierce", 0), lifeCfg = Math.max(5, Math.min(c.i("life", 40), 120));
        double explode = c.d("explode", 0), explodeMult = c.d("explode-mult", 0.6), hitMult = c.d("hit-mult", 1.0), kb = c.d("knockback", 0);
        int fire = c.i("fire-ticks", 0);
        boolean fan = c.b("fan", false);
        String itemName = c.s("item", ""), blockName = c.s("block", "");
        double height = c.d("height", 9);

        List<LivingEntity> pool = new ArrayList<>();
        Location skyCenter = null;
        if (mode.equals("AUTO")) {
            LivingEntity aimed = c.aim();
            if (aimed != null) pool.add(aimed);
            for (LivingEntity e : c.enemies(p.getLocation(), c.range())) if (!pool.contains(e)) pool.add(e);
            if (pool.isEmpty() && !c.b("allow-empty", false)) return c.fail("no-target");
        } else if (mode.equals("SKY")) {
            skyCenter = c.aimPoint().clone();
        }

        List<P> ps = new ArrayList<>();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            P pr = new P();
            pr.startTick = i * stagger;
            pr.pierce = pierceCfg;
            pr.life = lifeCfg;
            if (mode.equals("AUTO") && !pool.isEmpty()) pr.target = pool.get(i % pool.size());
            if (fan && count > 1) pr.yawOff = spread * (2.0 * i / (count - 1) - 1);
            else if (spread > 0) pr.yawOff = (rnd.nextDouble() * 2 - 1) * spread;
            if (mode.equals("SKY")) {
                double r = Math.max(1, c.def.radius);
                double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
                Location ground = skyCenter.clone().add(Math.cos(a) * d, 0.5, Math.sin(a) * d);
                RayTraceResult up = ground.getWorld().rayTraceBlocks(ground, new Vector(0, 1, 0), height, FluidCollisionMode.NEVER, true);
                double h = up == null ? height : Math.max(1.5, ground.distance(up.getHitPosition().toLocation(ground.getWorld())) - 0.6);
                pr.skyPos = ground.clone().add(0, h, 0);
            }
            ps.add(pr);
        }
        c.castFx(p.getLocation());

        final int[] displays = {0};
        new BukkitRunnable() {
            int t = 0;

            @Override public void run() {
                if (!p.isOnline()) {
                    for (P pr : ps) kill(c, pr);
                    cancel();
                    return;
                }
                boolean any = false;
                for (P pr : ps) {
                    if (pr.done) continue;
                    any = true;
                    if (t < pr.startTick) continue;
                    if (!pr.started) {
                        pr.started = true;
                        launch(c, pr, mode, speed);
                        if (displays[0] < 10) {
                            pr.display = makeDisplay(c, pr, itemName, blockName);
                            if (pr.display != null) displays[0]++;
                        }
                        if (count <= 6 || pr.startTick == 0) Fx.sound(pr.pos, c.def.sound, 0.6f, (float) c.def.pitch);
                    }
                    step(c, pr, speed, gravity, width, homing, explode, explodeMult, hitMult, kb, fire);
                }
                t++;
                if (!any) cancel();
            }
        }.runTaskTimer(c.pl, 0, 1);
        return true;
    }

    private static void launch(Cast c, P pr, String mode, double speed) {
        Player p = c.p;
        Location eye = p.getEyeLocation();
        Vector dir;
        if (mode.equals("SKY")) {
            pr.pos = pr.skyPos.clone();
            pr.vel = new Vector(0, -speed, 0);
            return;
        }
        if (pr.target != null && pr.target.isValid()) {
            dir = pr.target.getEyeLocation().toVector().subtract(eye.toVector());
        } else {
            dir = eye.getDirection();
        }
        if (dir.lengthSquared() < 0.0001) dir = eye.getDirection();
        dir.normalize();
        if (pr.yawOff != 0) dir.rotateAroundY(pr.yawOff);
        pr.pos = eye.clone().add(dir.clone().multiply(0.6)).add(0, -0.2, 0);
        pr.vel = dir.multiply(speed);
    }

    private static Display makeDisplay(Cast c, P pr, String itemName, String blockName) {
        try {
            World w = pr.pos.getWorld();
            Display d = null;
            if (!itemName.isEmpty()) {
                Material m = Mc.material(itemName, null);
                if (m != null && !m.isAir()) {
                    d = w.spawn(pr.pos, ItemDisplay.class, x -> {
                        x.setItemStack(new ItemStack(m));
                        x.setPersistent(false);
                        x.setTeleportDuration(1);
                    });
                }
            } else if (!blockName.isEmpty()) {
                Material m = Mc.material(blockName, null);
                if (m != null && m.isBlock()) {
                    d = w.spawn(pr.pos, BlockDisplay.class, x -> {
                        x.setBlock(Bukkit.createBlockData(m));
                        x.setPersistent(false);
                        x.setTeleportDuration(1);
                    });
                }
            }
            if (d != null) c.pl.temps.add(d);
            return d;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void kill(Cast c, P pr) {
        pr.done = true;
        if (pr.display != null) {
            pr.display.remove();
            c.pl.temps.remove(pr.display);
            pr.display = null;
        }
    }

    private static void step(Cast c, P pr, double speed, double gravity, double width, double homing, double explode,
                             double explodeMult, double hitMult, double kb, int fire) {
        Player p = c.p;
        Location prev = pr.pos.clone();
        World w = prev.getWorld();
        if (homing > 0 && pr.target != null && pr.target.isValid() && !pr.target.isDead()) {
            Vector to = pr.target.getEyeLocation().toVector().subtract(prev.toVector());
            if (to.lengthSquared() > 0.01) pr.vel = pr.vel.clone().normalize().multiply(1 - homing).add(to.normalize().multiply(homing)).normalize().multiply(speed);
        }
        if (gravity != 0) pr.vel.setY(pr.vel.getY() - gravity);
        double len = pr.vel.length();
        if (len < 0.01) {
            end(c, pr, prev, explode, explodeMult);
            return;
        }
        Vector dirN = pr.vel.clone().normalize();
        RayTraceResult br = w.rayTraceBlocks(prev, dirN, len, FluidCollisionMode.NEVER, true);
        double seg = br != null ? Math.max(0.01, prev.distance(br.getHitPosition().toLocation(w))) : len;

        List<LivingEntity> hits = new ArrayList<>();
        Location mid = prev.clone().add(dirN.clone().multiply(seg / 2));
        for (LivingEntity e : w.getNearbyLivingEntities(mid, seg / 2 + width + 1.5)) {
            if (pr.hit.contains(e.getUniqueId()) || !c.pl.protection.valid(p, e)) continue;
            BoundingBox bb = e.getBoundingBox().expand(width);
            if (bb.contains(prev.toVector()) || bb.rayTrace(prev.toVector(), dirN, seg) != null) hits.add(e);
        }
        hits.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(prev)));
        for (LivingEntity e : hits) {
            pr.hit.add(e.getUniqueId());
            c.hit(e, c.dmg(e) * hitMult);
            if (kb > 0) c.knockFrom(e, prev, kb, 0.2);
            if (fire > 0) e.setFireTicks(Math.max(e.getFireTicks(), fire));
            c.effects(e);
            c.burstFx(e.getLocation().add(0, e.getHeight() / 2, 0), 12, 0.3);
            if (pr.pierce <= 0) {
                end(c, pr, e.getLocation().add(0, e.getHeight() / 2, 0), explode, explodeMult);
                return;
            }
            pr.pierce--;
        }
        if (br != null) {
            end(c, pr, br.getHitPosition().toLocation(w), explode, explodeMult);
            return;
        }
        pr.pos = prev.clone().add(pr.vel);
        int trail = Math.max(2, (int) (width * 6));
        Fx.particle(pr.pos, c.def.particle, trail, width * 0.35, width * 0.35, width * 0.35, 0.01);
        if (pr.display != null && pr.display.isValid()) {
            Location dl = pr.pos.clone();
            dl.setDirection(dirN);
            if (pr.display instanceof BlockDisplay) dl.add(-0.5, -0.5, -0.5);
            pr.display.teleport(dl);
        }
        if (--pr.life <= 0) end(c, pr, pr.pos, explode, explodeMult);
    }

    private static void end(Cast c, P pr, Location at, double explode, double explodeMult) {
        kill(c, pr);
        if (explode > 0) {
            Area.style(c, "RING", at, explode);
            Fx.sound(at, "entity.generic.explode", 0.7f, 1.3f);
            for (LivingEntity e : c.enemies(at, explode)) {
                if (pr.hit.contains(e.getUniqueId())) continue;
                c.hit(e, c.dmg(e) * explodeMult);
                c.effects(e);
            }
        } else {
            c.burstFx(at, 10, 0.25);
        }
    }
}
