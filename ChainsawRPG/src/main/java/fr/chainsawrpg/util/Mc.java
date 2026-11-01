package fr.chainsawrpg.util;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Recherches tolérantes d'identifiants Minecraft (effets, attributs, particules).
 * Utilise la réflexion sur les constantes publiques : fonctionne que le type soit un enum
 * ou une classe a registre (changement fréquent de l'API Paper). Un nom inconnu renvoie null.
 */
public final class Mc {
    private static final Map<String, Optional<Object>> CACHE = Collections.synchronizedMap(new HashMap<>());

    private Mc() {}

    @SuppressWarnings("unchecked")
    private static <T> T field(Class<T> type, String id) {
        if (id == null) return null;
        String k = type.getSimpleName() + ":" + id;
        Optional<Object> hit = CACHE.get(k);
        if (hit == null) {
            Object v = null;
            try {
                v = type.getField(id.trim().toUpperCase(Locale.ROOT).replace('.', '_')).get(null);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // inconnu
            }
            hit = Optional.ofNullable(type.isInstance(v) ? v : null);
            CACHE.put(k, hit);
        }
        return (T) hit.orElse(null);
    }

    public static PotionEffectType effect(String id) { return field(PotionEffectType.class, id); }
    public static Attribute attribute(String id) { return field(Attribute.class, id); }
    public static Particle particle(String id) { return field(Particle.class, id); }

    public static Material material(String name, Material def) {
        if (name == null || name.isBlank()) return def;
        Material m = Material.matchMaterial(name.trim());
        return m != null ? m : def;
    }

    public static Color color(String rgb) {
        try {
            String[] p = rgb.split(",");
            return Color.fromRGB(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        } catch (Exception e) {
            return Color.RED;
        }
    }

    /** Format : "id:ticks:amplificateur" (ex: slowness:60:1). */
    public static PotionEffect parseEffect(String spec) {
        try {
            String[] p = spec.split(":");
            PotionEffectType t = effect(p[0]);
            if (t == null) return null;
            int ticks = Integer.parseInt(p[1].trim());
            int amp = p.length > 2 ? Integer.parseInt(p[2].trim()) : 0;
            return new PotionEffect(t, ticks, amp, false, true, true);
        } catch (Exception e) {
            return null;
        }
    }

    public static double maxHealth(LivingEntity e) {
        Attribute a = attribute("max_health");
        if (a != null && e.getAttribute(a) != null) return e.getAttribute(a).getValue();
        return 20.0;
    }
}
