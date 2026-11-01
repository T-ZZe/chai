package fr.chainsawrpg.demon;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.util.Mc;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Charge raretés, compétences et démons. Les identifiants internes (clés YAML) sont stables. */
public final class DemonManager {
    private final ChainsawPlugin pl;
    private final Map<String, Rarity> rarities = new LinkedHashMap<>();
    private final Map<String, DemonDef> demons = new LinkedHashMap<>();
    private final Map<String, SkillDef> skillsById = new HashMap<>();
    private final Map<String, PassiveProfile> profiles = new HashMap<>();
    private final Random random = new Random();

    public DemonManager(ChainsawPlugin pl) { this.pl = pl; }

    public void load() {
        rarities.clear();
        demons.clear();
        skillsById.clear();
        profiles.clear();

        ConfigurationSection rs = pl.cfg.get("rarities.yml").getConfigurationSection("rarities");
        int order = 0;
        if (rs != null) {
            for (String id : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(id);
                rarities.put(id, new Rarity(id, s.getString("name", id), s.getString("color", "<white>"), s.getDouble("chance", 0), order++));
            }
        }

        YamlConfiguration cc = pl.cfg.get("cooldowns.yml");
        YamlConfiguration kc = pl.cfg.get("sacrifices.yml");
        ConfigurationSection ss = pl.cfg.get("skills.yml").getConfigurationSection("skills");
        if (ss != null) {
            for (String id : ss.getKeys(false)) {
                SkillDef sd = new SkillDef(id, ss.getConfigurationSection(id));
                sd.cooldown = cc.getDouble("cooldowns." + id, 10);
                sd.cost = new Cost(kc.getString("costs." + id + ".type", "NONE").toUpperCase(), kc.getDouble("costs." + id + ".amount", 0));
                skillsById.put(id, sd);
            }
        }

        ConfigurationSection ds = pl.cfg.get("demons.yml").getConfigurationSection("demons");
        if (ds != null) {
            for (String id : ds.getKeys(false)) {
                ConfigurationSection s = ds.getConfigurationSection(id);
                Rarity r = rarities.get(s.getString("rarity", ""));
                if (r == null) {
                    pl.getLogger().warning("Demon '" + id + "' : rarete inconnue, ignore.");
                    continue;
                }
                DemonDef d = new DemonDef(id, s.getString("name", id), s.getString("description", ""), r,
                        Mc.material(s.getString("icon", "PAPER"), Material.PAPER), s.getBoolean("enabled", true), s.getBoolean("secret", false));
                List<String> ids = s.getStringList("skills");
                boolean complete = ids.size() == 4;
                for (int i = 0; i < ids.size() && i < 4; i++) {
                    SkillDef sd = skillsById.get(ids.get(i));
                    if (sd == null) {
                        complete = false;
                        pl.getLogger().warning("Demon '" + id + "' : competence introuvable '" + ids.get(i) + "'.");
                        continue;
                    }
                    d.skills[i + 1] = sd;
                }
                String pid = s.getString("passive", "");
                d.skills[0] = skillsById.get(pid);
                if (d.skills[0] == null) complete = false;

                if (d.enabled) {
                    if (!complete) {
                        d.enabled = false;
                        d.disabledReason = "definition incomplete";
                    } else {
                        for (SkillDef sk : d.activeSkills()) {
                            if (!pl.skills.has(sk.type)) {
                                d.enabled = false;
                                d.disabledReason = "type de competence non disponible : " + sk.type;
                                break;
                            }
                        }
                    }
                    if (!d.enabled) pl.getLogger().warning("Demon '" + id + "' desactive (" + d.disabledReason + ").");
                } else {
                    d.disabledReason = s.getString("disabled-reason", "pas encore disponible");
                }
                demons.put(id, d);
                profiles.put(id, PassiveProfile.from(d.passive()));
            }
        }
        pl.getLogger().info(demons.values().stream().filter(x -> x.enabled).count() + "/" + demons.size() + " demons actifs, "
                + skillsById.size() + " competences chargees.");
    }

    public DemonDef get(String id) { return id == null ? null : demons.get(id); }
    public Collection<DemonDef> all() { return demons.values(); }
    public SkillDef skill(String id) { return skillsById.get(id); }
    public Rarity rarity(String id) { return rarities.get(id); }
    public Collection<Rarity> rarities() { return rarities.values(); }
    public PassiveProfile profile(String demonId) { return profiles.getOrDefault(demonId, PassiveProfile.NONE); }

    /** Démons proposés au Marchand (actifs et non secrets). */
    public List<DemonDef> shop() {
        List<DemonDef> l = new ArrayList<>();
        for (DemonDef d : demons.values()) if (d.enabled && !d.secret) l.add(d);
        return l;
    }

    /** Tirage pondéré par rareté puis uniforme dans la rareté (n'empêche pas l'obtention directe). */
    public DemonDef roll() {
        List<DemonDef> pool = shop();
        if (pool.isEmpty()) return null;
        Map<Rarity, List<DemonDef>> by = new LinkedHashMap<>();
        for (DemonDef d : pool) by.computeIfAbsent(d.rarity, k -> new ArrayList<>()).add(d);
        double total = 0;
        for (Rarity r : by.keySet()) total += r.chance();
        if (total <= 0) return pool.get(random.nextInt(pool.size()));
        double x = random.nextDouble() * total;
        for (Map.Entry<Rarity, List<DemonDef>> e : by.entrySet()) {
            x -= e.getKey().chance();
            if (x <= 0) return e.getValue().get(random.nextInt(e.getValue().size()));
        }
        return pool.get(random.nextInt(pool.size()));
    }
}
