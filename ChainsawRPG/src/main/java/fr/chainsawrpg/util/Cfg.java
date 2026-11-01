package fr.chainsawrpg.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Chargement des fichiers YAML (copie des valeurs par défaut au premier démarrage). */
public final class Cfg {
    public static final String[] FILES = {
            "config.yml", "demons.yml", "skills.yml", "cooldowns.yml", "messages.yml",
            "sacrifices.yml", "rarities.yml", "items.yml"
    };

    private final JavaPlugin pl;
    private final Map<String, YamlConfiguration> files = new HashMap<>();

    public Cfg(JavaPlugin pl) { this.pl = pl; }

    public void load() {
        if (!pl.getDataFolder().exists()) pl.getDataFolder().mkdirs();
        for (String name : FILES) {
            File f = new File(pl.getDataFolder(), name);
            if (!f.exists()) pl.saveResource(name, false);
            files.put(name, YamlConfiguration.loadConfiguration(f));
        }
    }

    public YamlConfiguration get(String name) { return files.get(name); }
}
