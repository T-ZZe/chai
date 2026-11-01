package fr.chainsawrpg.demon;

import fr.chainsawrpg.util.Mc;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Définition d'une compétence (slot 1-3 normales, 4 ultime, 0 passif). */
public final class SkillDef {
    public final String id, demonId, type, name, description, particle, sound;
    public final int slot, maxTargets;
    public final double dmgPvp, dmgPve, range, radius, duration, pitch;
    public final Set<String> tags = new HashSet<>();
    public final ConfigurationSection params;
    public double cooldown = 10;
    public Cost cost = Cost.NONE;
    private final Map<String, List<PotionEffect>> effectCache = new HashMap<>();

    public SkillDef(String id, ConfigurationSection s) {
        this.id = id;
        this.demonId = s.getString("demon", "");
        this.slot = s.getInt("slot", 1);
        this.type = s.getString("type", "NONE").toUpperCase(Locale.ROOT);
        this.name = s.getString("name", id);
        this.description = s.getString("description", "");
        this.dmgPvp = s.getDouble("damage-pvp", 0);
        this.dmgPve = s.getDouble("damage-pve", 0);
        this.range = s.getDouble("range", 12);
        this.radius = s.getDouble("radius", 0);
        this.duration = s.getDouble("duration", 0);
        this.maxTargets = Math.max(1, s.getInt("max-targets", 1));
        this.particle = s.getString("particle", "");
        this.sound = s.getString("sound", "");
        this.pitch = s.getDouble("sound-pitch", 1.0);
        for (String t : s.getStringList("tags")) tags.add(t.toLowerCase(Locale.ROOT));
        this.params = s.getConfigurationSection("params");
    }

    public boolean isUltimate() { return slot == 4; }
    public boolean isPassive() { return slot == 0; }
    public boolean tag(String t) { return tags.contains(t); }

    public double d(String k, double def) { return params == null ? def : params.getDouble(k, def); }
    public int i(String k, int def) { return params == null ? def : params.getInt(k, def); }
    public String s(String k, String def) { return params == null ? def : params.getString(k, def); }
    public boolean b(String k, boolean def) { return params == null ? def : params.getBoolean(k, def); }
    public List<String> list(String k) { return params == null ? new ArrayList<>() : params.getStringList(k); }

    /** Effets de potion parsés une seule fois ("id:ticks:amp"). */
    public List<PotionEffect> effects(String key) {
        return effectCache.computeIfAbsent(key, k -> {
            List<PotionEffect> out = new ArrayList<>();
            for (String sp : list(k)) {
                PotionEffect pe = Mc.parseEffect(sp);
                if (pe != null) out.add(pe);
            }
            return out;
        });
    }
}
