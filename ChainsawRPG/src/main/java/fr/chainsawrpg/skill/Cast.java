package fr.chainsawrpg.skill;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.data.PlayerData;
import fr.chainsawrpg.demon.PassiveProfile;
import fr.chainsawrpg.demon.SkillDef;
import fr.chainsawrpg.util.Fx;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Contexte d'un lancement : ciblage, dégâts, effets. Tout se passe sur le thread principal. */
public final class Cast {
    public final ChainsawPlugin pl;
    public final Player p;
    public final SkillDef def;
    public final PlayerData pd;
    public final PassiveProfile pp;
    public String failKey = "skill-failed";

    public Cast(ChainsawPlugin pl, Player p, SkillDef def, PlayerData pd) {
        this.pl = pl;
        this.p = p;
        this.def = def;
        this.pd = pd;
        PassiveProfile prof = pl.passives.profile(p);
        this.pp = prof == null ? PassiveProfile.NONE : prof;
    }

    public boolean fail(String key) {
        this.failKey = key;
        return false;
    }

    // ---- parametres ----
    public double d(String k, double v) { return def.d(k, v); }
    public int i(String k, int v) { return def.i(k, v); }
    public String s(String k, String v) { return def.s(k, v); }
    public boolean b(String k, boolean v) { return def.b(k, v); }
    public double range() { return def.range * (1 + pp.rangeBonus); }

    // ---- degats ----
    private double levelMult(boolean pvp) {
        PlayerData.Contract c = pd == null ? null : pd.active();
        int lvl = c == null ? 1 : c.level;
        return 1 + Math.max(0, lvl - 1) * (pvp ? pl.settings.dmgPerLevelPvp : pl.settings.dmgPerLevelPve);
    }

    public double dmg(LivingEntity t) {
        boolean pvp = t instanceof Player;
        return (pvp ? def.dmgPvp : def.dmgPve) * levelMult(pvp);
    }

    public boolean hit(LivingEntity t, double amount) {
        if (!p.isOnline() || !pl.protection.valid(p, t)) return false;
        return pl.combat.skillDamage(p, t, amount, def);
    }

    public boolean hit(LivingEntity t) { return hit(t, dmg(t)); }

    /** Applique une impulsion bornée ; réduite par la résistance au recul des joueurs concernés. */
    public void knock(LivingEntity t, Vector v) {
        if (v == null || Double.isNaN(v.getX()) || Double.isNaN(v.getY()) || Double.isNaN(v.getZ())) return;
        Vector k = v.clone();
        if (t instanceof Player tp) {
            PassiveProfile tpp = pl.passives.profile(tp);
            if (tpp != null && tpp.knockbackResist > 0) k.multiply(Math.max(0, 1 - tpp.knockbackResist));
        }
        if (k.length() > 2.5) k.normalize().multiply(2.5);
        t.setVelocity(t.getVelocity().add(k));
    }

    public void knockFrom(LivingEntity t, Location from, double power, double up) {
        if (power <= 0 && up <= 0) return;
        Vector v = t.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (v.lengthSquared() < 0.0001) v = p.getLocation().getDirection().setY(0);
        v.normalize().multiply(power).setY(up);
        knock(t, v);
    }

    /** Effets de potion sur une cible ; durée plafonnée en PvP. */
    public void effects(LivingEntity t, String key) {
        List<PotionEffect> list = def.effects(key);
        int cap = t instanceof Player ? pl.settings.maxEffectPvp * 20 : Integer.MAX_VALUE;
        for (PotionEffect e : list) t.addPotionEffect(new PotionEffect(e.getType(), Math.min(e.getDuration(), cap), e.getAmplifier(), e.isAmbient(), e.hasParticles(), e.hasIcon()));
    }

    public void effects(LivingEntity t) { effects(t, "effects"); }

    // ---- ciblage ----
    public boolean los(Location from, LivingEntity t) {
        if (!pl.settings.requireLos) return true;
        Location to = t.getLocation().add(0, t.getHeight() * 0.5, 0);
        Vector d = to.toVector().subtract(from.toVector());
        double len = d.length();
        if (len < 0.5) return true;
        RayTraceResult r = from.getWorld().rayTraceBlocks(from, d.normalize(), len, FluidCollisionMode.NEVER, true);
        return r == null;
    }

    /** Ennemis valides autour de c (sphère), triés par distance, limités à maxTargets, murs vérifiés. */
    public List<LivingEntity> enemies(Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        if (c.getWorld() == null) return out;
        Location from = c.clone().add(0, 0.6, 0);
        for (LivingEntity le : c.getWorld().getNearbyLivingEntities(c, r)) {
            if (le.getLocation().distanceSquared(c) > r * r + 4) continue;
            if (pl.protection.valid(p, le) && los(from, le)) out.add(le);
        }
        out.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(c)));
        return out.size() > def.maxTargets ? new ArrayList<>(out.subList(0, def.maxTargets)) : out;
    }

    public LivingEntity aim() { return aim(range()); }

    public LivingEntity aim(double range) {
        Location eye = p.getEyeLocation();
        World w = eye.getWorld();
        Vector dir = eye.getDirection();
        RayTraceResult r = w.rayTraceEntities(eye, dir, range, 0.35, e -> pl.protection.valid(p, e));
        if (r != null && r.getHitEntity() instanceof LivingEntity le && los(eye, le)) return le;
        double cos = Math.cos(Math.toRadians(pl.settings.aimAssist + pp.aimBonus));
        LivingEntity best = null;
        double bestDot = cos;
        for (LivingEntity le : w.getNearbyLivingEntities(eye, range)) {
            if (!pl.protection.valid(p, le)) continue;
            Vector to = le.getEyeLocation().toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist > range || dist < 0.3) continue;
            double dot = to.normalize().dot(dir);
            if (dot > bestDot && los(eye, le)) {
                best = le;
                bestDot = dot;
            }
        }
        return best;
    }

    /** Point visé : cible, sinon bloc touché, sinon point à portée maximale. */
    public Location aimPoint() {
        Location eye = p.getEyeLocation();
        double range = range();
        LivingEntity t = aim(range);
        if (t != null) return t.getLocation();
        World w = eye.getWorld();
        RayTraceResult r = w.rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true);
        if (r != null) return r.getHitPosition().toLocation(w);
        return eye.clone().add(eye.getDirection().multiply(range));
    }

    // ---- fx ----
    public void castFx(Location l) {
        Fx.sound(l, def.sound, 1f, (float) def.pitch);
    }

    public void burstFx(Location l, int count, double spread) {
        Fx.particle(l, def.particle, count, spread, spread, spread, 0.05);
    }
}
