package fr.chainsawrpg;

import fr.chainsawrpg.command.ChainsawCommand;
import fr.chainsawrpg.contract.ContractManager;
import fr.chainsawrpg.data.DatabaseManager;
import fr.chainsawrpg.data.PlayerData;
import fr.chainsawrpg.data.PlayerDataManager;
import fr.chainsawrpg.demon.DemonDef;
import fr.chainsawrpg.demon.DemonManager;
import fr.chainsawrpg.demon.SkillDef;
import fr.chainsawrpg.gui.GUIManager;
import fr.chainsawrpg.item.HeadManager;
import fr.chainsawrpg.item.ItemManager;
import fr.chainsawrpg.listener.CombatListener;
import fr.chainsawrpg.listener.CoreListener;
import fr.chainsawrpg.listener.InputListener;
import fr.chainsawrpg.npc.NPCManager;
import fr.chainsawrpg.skill.BloodManager;
import fr.chainsawrpg.skill.Combat;
import fr.chainsawrpg.skill.CooldownManager;
import fr.chainsawrpg.skill.PassiveManager;
import fr.chainsawrpg.skill.Protection;
import fr.chainsawrpg.skill.SacrificeManager;
import fr.chainsawrpg.skill.SkillManager;
import fr.chainsawrpg.skill.StatusManager;
import fr.chainsawrpg.skill.TempBlocks;
import fr.chainsawrpg.util.Cfg;
import fr.chainsawrpg.util.Fx;
import fr.chainsawrpg.util.Msg;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class ChainsawPlugin extends JavaPlugin {
    public Cfg cfg;
    public Settings settings;
    public Msg msg;
    public DatabaseManager db;
    public PlayerDataManager players;
    public DemonManager demons;
    public ItemManager items;
    public HeadManager heads;
    public ContractManager contracts;
    public CooldownManager cooldowns;
    public BloodManager blood;
    public SacrificeManager sacrifices;
    public StatusManager status;
    public Protection protection;
    public TempBlocks tempBlocks;
    public SkillManager skills;
    public PassiveManager passives;
    public GUIManager gui;
    public NPCManager npcs;
    public AdminManager admin;
    public Combat combat;

    public NamespacedKey kNpc, kOwner;
    /** Entités temporaires (affichages, marqueurs) supprimées à l'arrêt. */
    public final Set<Entity> temps = new HashSet<>();
    private final Map<UUID, Long> hudMute = new HashMap<>();
    private BukkitTask autosave, hudTask;

    public NamespacedKey key(String k) { return new NamespacedKey(this, k); }

    public void hudMute(UUID id, long ms) { hudMute.put(id, System.currentTimeMillis() + ms); }

    @Override
    public void onEnable() {
        cfg = new Cfg(this);
        cfg.load();
        settings = new Settings(cfg.get("config.yml"));
        Fx.density = settings.fxDensity;
        kNpc = key("npc");
        kOwner = key("owner");
        msg = new Msg(this);

        db = new DatabaseManager(this);
        try {
            db.init();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Base de donnees indisponible : plugin desactive. "
                    + "(Verifie database.type dans config.yml ; SQLite/MySQL sont fournis par Paper.)", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        players = new PlayerDataManager(this);
        items = new ItemManager(this);
        combat = new Combat(this);
        status = new StatusManager(this);
        protection = new Protection(this);
        tempBlocks = new TempBlocks(this);
        cooldowns = new CooldownManager();
        blood = new BloodManager(this);
        sacrifices = new SacrificeManager(this);
        skills = new SkillManager(this);
        demons = new DemonManager(this);
        passives = new PassiveManager(this);
        heads = new HeadManager(this);
        contracts = new ContractManager(this);
        gui = new GUIManager(this);
        npcs = new NPCManager(this);
        admin = new AdminManager(this);

        skills.registerAll();
        demons.load();
        try {
            heads.load();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Chargement des tetes impossible : plugin desactive.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new CoreListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new InputListener(this), this);
        getServer().getPluginManager().registerEvents(gui, this);

        PluginCommand pc = getCommand("chainsaw");
        if (pc != null) {
            ChainsawCommand cc = new ChainsawCommand(this);
            pc.setExecutor(cc);
            pc.setTabCompleter(cc);
        }

        for (Player p : getServer().getOnlinePlayers()) {
            if (players.ensure(p) != null) passives.refresh(p);
        }
        passives.start();
        startTasks();
        getLogger().info("ChainsawRPG active.");
    }

    private void startTasks() {
        long period = settings.autosaveMinutes * 60L * 20L;
        autosave = new BukkitRunnable() {
            @Override public void run() {
                players.saveAll();
                cooldowns.purge();
                hudMute.values().removeIf(t -> t < System.currentTimeMillis());
            }
        }.runTaskTimer(this, period, period);
        if (settings.hud) {
            hudTask = new BukkitRunnable() {
                @Override public void run() { hudTick(); }
            }.runTaskTimer(this, 20, settings.hudInterval);
        }
    }

    private void hudTick() {
        long now = System.currentTimeMillis();
        for (Player p : getServer().getOnlinePlayers()) {
            Long mute = hudMute.get(p.getUniqueId());
            if (mute != null && now < mute) continue;
            PlayerData pd = players.get(p);
            DemonDef d = pd == null ? null : demons.get(pd.activeDemon);
            if (d == null) continue;
            PlayerData.Contract c = pd.active();
            int sel = skills.selected(p.getUniqueId());
            SkillDef s = d.skill(sel);
            long rem = s == null ? 0 : cooldowns.remainingMs(p.getUniqueId(), s.id);
            String cd = rem > 0 ? "<red>" + String.format(Locale.ROOT, "%.1fs", rem / 1000.0) : "<green>OK";
            p.sendActionBar(msg.component(d.rarity.color() + d.name + " <gray>Niv.<white>" + c.level + " <dark_gray>| <dark_red>Sang <white>" + pd.blood
                    + " <dark_gray>| <yellow>[" + sel + "] <white>" + (s == null ? "-" : s.name) + " " + cd));
        }
    }

    public void reloadAll() {
        cfg.load();
        settings = new Settings(cfg.get("config.yml"));
        Fx.density = settings.fxDensity;
        msg.reload();
        demons.load();
        for (Player p : getServer().getOnlinePlayers()) passives.refresh(p);
    }

    @Override
    public void onDisable() {
        if (autosave != null) autosave.cancel();
        if (hudTask != null) hudTask.cancel();
        if (contracts != null) contracts.cancelAll();
        if (status != null) status.shutdown();
        if (tempBlocks != null) tempBlocks.restoreAll();
        for (Entity e : new ArrayList<>(temps)) if (e.isValid()) e.remove();
        temps.clear();
        if (passives != null) {
            passives.stop();
            passives.clearAll();
        }
        if (players != null && db != null) players.saveAll();
        if (db != null) db.close();
    }
}
