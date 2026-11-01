package fr.chainsawrpg.demon;

import fr.chainsawrpg.util.Mc;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Passif d'un démon, composé de briques optionnelles (toutes lues dans skills.yml &gt; params du passif).
 * Voir docs/GUIDE_CONFIGURATION.md pour la liste des clés.
 */
public final class PassiveProfile {
    public final Map<DamageCause, Double> causeMult = new HashMap<>();
    public final Set<String> immuneEffects = new HashSet<>();
    public final Map<String, Double> attributes = new LinkedHashMap<>();
    public final List<PotionEffect> permEffects = new ArrayList<>();
    public final Map<String, Double> effectDurationMult = new HashMap<>();
    public double resistAll, dodgeChance, healOnKill, closeRange, closeRangeDist = 4.5, airBonus, summonBonus, markedBonus, fireBonus;
    public double weaponBonus, rangeBonus, aimBonus, regenPerCycle, knockbackResist, infectRamp;
    public double waterSpeedAmp = -1, neutralChance;
    public int markOnHit, slowOnHitTicks, slowOnHitAmp = -1, slowResistMaxAmp = -1;
    public String markStatus = "curse", markedStatus = "judge";

    public static final PassiveProfile NONE = new PassiveProfile();

    public static PassiveProfile from(SkillDef passive) {
        PassiveProfile p = new PassiveProfile();
        if (passive == null || passive.params == null) return p;
        ConfigurationSection s = passive.params;
        ConfigurationSection rc = s.getConfigurationSection("resist-causes");
        if (rc != null) {
            for (String k : rc.getKeys(false)) {
                try {
                    p.causeMult.put(DamageCause.valueOf(k.toUpperCase(Locale.ROOT)), rc.getDouble(k));
                } catch (IllegalArgumentException ignored) {
                    // cause inconnue
                }
            }
        }
        p.resistAll = s.getDouble("resist-all", 0);
        p.dodgeChance = s.getDouble("dodge-chance", 0);
        p.healOnKill = s.getDouble("heal-on-kill", 0);
        p.closeRange = s.getDouble("close-range-bonus", 0);
        p.closeRangeDist = s.getDouble("close-range-distance", 4.5);
        p.airBonus = s.getDouble("air-bonus", 0);
        p.summonBonus = s.getDouble("summon-bonus", 0);
        p.markedBonus = s.getDouble("marked-bonus", 0);
        p.markedStatus = s.getString("marked-status", "judge");
        p.fireBonus = s.getDouble("fire-bonus", 0);
        p.weaponBonus = s.getDouble("weapon-bonus", 0);
        p.rangeBonus = s.getDouble("range-bonus", 0);
        p.aimBonus = s.getDouble("aim-bonus", 0);
        p.regenPerCycle = s.getDouble("regen-per-cycle", 0);
        p.knockbackResist = s.getDouble("knockback-resist", 0);
        p.infectRamp = s.getDouble("infect-ramp", 0);
        p.waterSpeedAmp = s.getDouble("water-speed-amp", -1);
        p.neutralChance = s.getDouble("neutral-chance", 0);
        p.markOnHit = s.getInt("mark-on-hit", 0);
        p.markStatus = s.getString("mark-status", "curse");
        p.slowOnHitTicks = s.getInt("slow-on-hit-ticks", 0);
        p.slowOnHitAmp = s.getInt("slow-on-hit-amp", -1);
        p.slowResistMaxAmp = s.getInt("slow-resist-max-amp", -1);
        for (String e : s.getStringList("immune-effects")) p.immuneEffects.add(e.toLowerCase(Locale.ROOT));
        ConfigurationSection at = s.getConfigurationSection("attributes");
        if (at != null) for (String k : at.getKeys(false)) p.attributes.put(k.toLowerCase(Locale.ROOT), at.getDouble(k));
        for (String e : s.getStringList("perm-effects")) {
            PotionEffect pe = Mc.parseEffect(e);
            if (pe != null) p.permEffects.add(new PotionEffect(pe.getType(), PotionEffect.INFINITE_DURATION, pe.getAmplifier(), true, false, false));
        }
        ConfigurationSection dm = s.getConfigurationSection("effect-duration-mult");
        if (dm != null) for (String k : dm.getKeys(false)) p.effectDurationMult.put(k.toLowerCase(Locale.ROOT), dm.getDouble(k));
        return p;
    }
}
