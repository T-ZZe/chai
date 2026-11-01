package fr.chainsawrpg.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Locale;

/** Effets visuels et sonores. Spec particule : "FLAME", "DUST:255,0,0:1.4", "BLOCK:STONE". */
public final class Fx {
    public static double density = 1.0;

    private Fx() {}

    private static int n(int count) { return Math.max(1, (int) Math.round(count * density)); }

    public static void sound(Location l, String key, float vol, float pitch) {
        if (key == null || key.isBlank() || l == null || l.getWorld() == null) return;
        try {
            l.getWorld().playSound(Sound.sound(Key.key(key), Sound.Source.PLAYER, vol, pitch), l.getX(), l.getY(), l.getZ());
        } catch (RuntimeException ignored) {
            // cle de son invalide : ignorer
        }
    }

    public static void soundTo(Player p, String key, float vol, float pitch) {
        if (key == null || key.isBlank()) return;
        try {
            p.playSound(Sound.sound(Key.key(key), Sound.Source.PLAYER, vol, pitch));
        } catch (RuntimeException ignored) {
            // ignorer
        }
    }

    public static void particle(Location l, String spec, int count, double dx, double dy, double dz, double speed) {
        spawn(null, l, spec, count, dx, dy, dz, speed);
    }

    /** Particule visible uniquement par le joueur donné. */
    public static void particleTo(Player viewer, Location l, String spec, int count, double dx, double dy, double dz, double speed) {
        spawn(viewer, l, spec, count, dx, dy, dz, speed);
    }

    private static void spawn(Player viewer, Location l, String spec, int count, double dx, double dy, double dz, double speed) {
        if (spec == null || spec.isBlank() || l == null) return;
        World w = l.getWorld();
        if (w == null) return;
        String[] parts = spec.split(":");
        String name = parts[0].trim().toUpperCase(Locale.ROOT);
        Particle p = Mc.particle(name);
        if (p == null) return;
        int c = n(count);
        try {
            Object data = null;
            if (name.equals("DUST")) {
                Color col = parts.length > 1 ? Mc.color(parts[1]) : Color.RED;
                float size = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.2f;
                data = new Particle.DustOptions(col, size);
            } else if (name.equals("BLOCK") || name.equals("FALLING_DUST") || name.equals("BLOCK_MARKER")) {
                data = Bukkit.createBlockData(Mc.material(parts.length > 1 ? parts[1] : "STONE", Material.STONE));
            }
            if (viewer != null) {
                if (data != null) viewer.spawnParticle(p, l, c, dx, dy, dz, speed, data);
                else viewer.spawnParticle(p, l, c, dx, dy, dz, speed);
            } else {
                if (data != null) w.spawnParticle(p, l, c, dx, dy, dz, speed, data);
                else w.spawnParticle(p, l, c, dx, dy, dz, speed);
            }
        } catch (RuntimeException ignored) {
            // donnees de particule invalides : ignorer
        }
    }

    public static void line(Location a, Location b, String spec, double step) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.1) return;
        d.normalize();
        int steps = (int) Math.min(90, len / Math.max(0.2, step));
        Location p = a.clone();
        Vector inc = d.clone().multiply(step);
        for (int i = 0; i <= steps; i++) {
            particle(p, spec, 1, 0, 0, 0, 0);
            p.add(inc);
        }
    }

    public static void ring(Location c, double r, String spec, int points) {
        int pts = n(points);
        for (int i = 0; i < pts; i++) {
            double a = 2 * Math.PI * i / pts;
            Location l = c.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r);
            spawn(null, l, spec, 1, 0, 0, 0, 0);
        }
    }

    public static void column(Location base, double height, String spec) {
        for (double y = 0; y <= height; y += 0.5) spawn(null, base.clone().add(0, y, 0), spec, 1, 0.05, 0, 0.05, 0);
    }
}
