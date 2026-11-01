package fr.chainsawrpg.skill;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.demon.PassiveProfile;
import fr.chainsawrpg.util.Fx;
import fr.chainsawrpg.util.Mc;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
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

/**
 * Statuts sur entités : root (immobilisation), marques (curse/judge/lock), infected, decay.
 * Un seul ticker (toutes les 10 ticks) qui ne tourne que s'il existe au moins un statut actif.
 */
public final class StatusManager {
    public static final class Status {
        public final String key;
        public UUID source;
        public int stacks;
        public long expires;
        public double power;
        public String fx = "";

        Status(String key) { this.key = key; }
    }

    private static final int TICK = 10;

    private final ChainsawPlugin pl;
    private final Map<UUID, Map<String, Status>> map = new HashMap<>();
    private final Map<UUID, Long> rootImmune = new HashMap<>();
    private final Set<UUID> awareOff = new HashSet<>();
    private BukkitTask task;
    private int rootCount;

    public StatusManager(ChainsawPlugin pl) { this.pl = pl; }

    public Status apply(LivingEntity t, String key, UUID source, int addStacks, int maxStacks, long durMs, double power) {
        Map<String, Status> m = map.computeIfAbsent(t.getUniqueId(), k -> new HashMap<>());
        Status s = m.get(key);
        long now = System.currentTimeMillis();
        if (s == null || now > s.expires) {
            s = new Status(key);
            m.put(key, s);
        }
        s.source = source;
        s.stacks = Math.min(maxStacks, s.stacks + addStacks);
        s.expires = now + durMs;
        s.power = power;
        ensureTicker();
        return s;
    }

    public Status get(Entity e, String key) {
        Map<String, Status> m = map.get(e.getUniqueId());
        if (m == null) return null;
        Status s = m.get(key);
        if (s == null) return null;
        if (System.currentTimeMillis() > s.expires) return null;
        return s;
    }

    public boolean has(Entity e, String key) { return get(e, key) != null; }

    public int stacks(Entity e, String key, UUID source) {
        Status s = get(e, key);
        return s != null && (source == null || source.equals(s.source)) ? s.stacks : 0;
    }

    public void remove(Entity e, String key) {
        Map<String, Status> m = map.get(e.getUniqueId());
        if (m == null) return;
        Status s = m.remove(key);
        if (s != null && key.equals("root")) releaseRoot(e.getUniqueId(), e);
        if (m.isEmpty()) map.remove(e.getUniqueId());
    }

    public void clearAll(Entity e) {
        Map<String, Status> m = map.remove(e.getUniqueId());
        if (m != null && m.containsKey("root")) releaseRoot(e.getUniqueId(), e);
    }

    public boolean isRooted(Entity e) { return rootCount > 0 && has(e, "root"); }

    /** Immobilise (durée plafonnée, puis immunité temporaire). @return false si la cible est immunisée. */
    public boolean root(LivingEntity t, UUID source, double seconds, double dotPerSec, String fx) {
        boolean pvp = t instanceof Player;
        long now = System.currentTimeMillis();
        Long imm = rootImmune.get(t.getUniqueId());
        if (imm != null && now < imm) return false;
        double cap = pvp ? pl.settings.maxRootPvp : pl.settings.maxRootPve;
        long ms = (long) (Math.min(seconds, cap) * 1000);
        if (ms <= 0) return false;
        boolean had = has(t, "root");
        Status s = apply(t, "root", source, 1, 1, ms, dotPerSec);
        s.fx = fx == null ? "" : fx;
        if (!had) rootCount++;
        rootImmune.put(t.getUniqueId(), now + ms + pl.settings.rootImmunity * 1000L);
        if (rootImmune.size() > 256) rootImmune.values().removeIf(v -> v < now);
        if (t instanceof Mob m && m.isAware()) {
            m.setAware(false);
            awareOff.add(t.getUniqueId());
        }
        return true;
    }

    public List<LivingEntity> withStatus(UUID source, String key, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<String, Status>> e : map.entrySet()) {
            Status s = e.getValue().get(key);
            if (s == null || now > s.expires || (source != null && !source.equals(s.source))) continue;
            Entity ent = Bukkit.getEntity(e.getKey());
            if (ent instanceof LivingEntity le && le.isValid() && le.getWorld().equals(c.getWorld())
                    && le.getLocation().distanceSquared(c) <= r * r) out.add(le);
        }
        return out;
    }

    private void releaseRoot(UUID id, Entity e) {
        if (rootCount > 0) rootCount--;
        if (awareOff.remove(id) && e instanceof Mob m) m.setAware(true);
    }

    private void ensureTicker() {
        if (task != null) return;
        task = new BukkitRunnable() {
            @Override public void run() { tick(); }
        }.runTaskTimer(pl, TICK, TICK);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(map.keySet())) {
            Map<String, Status> m = map.get(id);
            if (m == null) continue;
            Entity ent = Bukkit.getEntity(id);
            if (!(ent instanceof LivingEntity le) || !le.isValid() || le.isDead()) {
                if (m.containsKey("root")) releaseRoot(id, ent);
                map.remove(id);
                continue;
            }
            for (Status s : new ArrayList<>(m.values())) {
                if (now > s.expires) {
                    m.remove(s.key);
                    if (s.key.equals("root")) releaseRoot(id, le);
                    continue;
                }
                Player src = s.source == null ? null : Bukkit.getPlayer(s.source);
                switch (s.key) {
                    case "root" -> {
                        if (!s.fx.isEmpty()) Fx.particle(le.getLocation().add(0, le.getHeight() / 2, 0), s.fx, 6, 0.4, le.getHeight() / 3, 0.4, 0.01);
                        if (s.power > 0) pl.combat.dotDamage(src, le, s.power * TICK / 20.0);
                    }
                    case "infected" -> {
                        PassiveProfile pp = src == null ? null : pl.passives.profile(src);
                        double ramp = pp == null ? 0 : pp.infectRamp;
                        if (ramp > 0 && s.stacks < 12) s.stacks++;
                        double mult = 1 + ramp * Math.max(0, s.stacks - 1);
                        Fx.particle(le.getLocation().add(0, le.getHeight() / 2, 0), "DUST:90,150,40:1.0", 4, 0.3, 0.4, 0.3, 0);
                        pl.combat.dotDamage(src, le, s.power * mult * TICK / 20.0);
                    }
                    case "decay" -> {
                        if (s.stacks < 5) s.stacks++;
                        PotionEffectType slow = Mc.effect("slowness"), weak = Mc.effect("weakness");
                        if (slow != null) le.addPotionEffect(new PotionEffect(slow, 30, s.stacks - 1, false, true, true));
                        if (weak != null) le.addPotionEffect(new PotionEffect(weak, 30, (s.stacks - 1) / 2, false, true, true));
                        Fx.particle(le.getLocation().add(0, le.getHeight() / 2, 0), "DUST:120,100,80:1.0", 3, 0.3, 0.4, 0.3, 0);
                        if (s.power > 0) pl.combat.dotDamage(src, le, s.power * TICK / 20.0);
                    }
                    default -> {
                        // marques : simple affichage discret
                        if (s.key.equals("curse") || s.key.equals("judge") || s.key.equals("lock")) {
                            String p = s.key.equals("curse") ? "DUST:150,0,150:1.0" : s.key.equals("judge") ? "DUST:255,215,0:1.0" : "DUST:255,60,60:1.0";
                            Fx.particle(le.getLocation().add(0, le.getHeight() + 0.4, 0), p, 2 + s.stacks, 0.15, 0.1, 0.15, 0);
                        }
                    }
                }
            }
            if (m.isEmpty()) map.remove(id);
        }
        if (map.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (UUID id : new ArrayList<>(awareOff)) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof Mob m) m.setAware(true);
        }
        awareOff.clear();
        map.clear();
        rootCount = 0;
    }
}
