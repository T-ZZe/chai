package fr.chainsawrpg.demon;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public final class DemonDef {
    public final String id, name, description;
    public final Rarity rarity;
    public final Material icon;
    public final boolean secret;
    public boolean enabled;
    public String disabledReason = "";
    /** index 1..4 = compétences, 0 = passif. */
    public final SkillDef[] skills = new SkillDef[5];

    public DemonDef(String id, String name, String description, Rarity rarity, Material icon, boolean enabled, boolean secret) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.rarity = rarity;
        this.icon = icon;
        this.enabled = enabled;
        this.secret = secret;
    }

    public SkillDef skill(int slot) { return slot >= 0 && slot <= 4 ? skills[slot] : null; }
    public SkillDef passive() { return skills[0]; }

    public List<SkillDef> activeSkills() {
        List<SkillDef> l = new ArrayList<>();
        for (int i = 1; i <= 4; i++) if (skills[i] != null) l.add(skills[i]);
        return l;
    }
}
