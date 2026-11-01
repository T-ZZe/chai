package fr.chainsawrpg.contract;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.Settings;
import fr.chainsawrpg.data.PlayerData;
import fr.chainsawrpg.demon.DemonDef;
import fr.chainsawrpg.item.ItemManager;
import fr.chainsawrpg.util.Fx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Achat, changement (rituel), attribution admin, expérience et niveaux des contrats. */
public final class ContractManager {
    private final ChainsawPlugin pl;
    private final Map<UUID, BukkitTask> rituals = new HashMap<>();

    public ContractManager(ChainsawPlugin pl) { this.pl = pl; }

    public boolean inRitual(UUID id) { return rituals.containsKey(id); }

    /** Point d'entrée du menu : première signature ou changement de contrat. */
    public void confirm(Player p, DemonDef d) {
        PlayerData pd = pl.players.ensure(p);
        if (pd == null) return;
        if (pd.activeDemon == null) purchase(p, pd, d);
        else if (pd.activeDemon.equals(d.id)) pl.msg.send(p, "contract-already");
        else startRitual(p, pd, d);
    }

    private boolean available(Player p, DemonDef d) {
        if (!d.enabled || d.secret) {
            pl.msg.send(p, "demon-unavailable");
            return false;
        }
        return true;
    }

    private void purchase(Player p, PlayerData pd, DemonDef d) {
        if (!available(p, d)) return;
        int need = pl.settings.headsRequired;
        int have = pl.heads.count(p);
        if (have < need || !pl.heads.consume(p, need)) {
            pl.msg.send(p, "not-enough-heads", "need", need, "have", have);
            return;
        }
        apply(p, pd, d, true);
    }

    /** Attribue le contrat (sans coût). */
    private void apply(Player p, PlayerData pd, DemonDef d, boolean announce) {
        pd.contracts.computeIfAbsent(d.id, PlayerData.Contract::new);
        pd.activeDemon = d.id;
        pl.passives.refresh(p);
        pl.players.save(p.getUniqueId());
        boolean hasBook = false;
        for (ItemStack it : p.getInventory().getContents()) if (pl.items.is(it, ItemManager.T_BOOK)) hasBook = true;
        if (!hasBook) p.getInventory().addItem(pl.items.book()).values().forEach(it -> p.getWorld().dropItem(p.getLocation(), it));
        Fx.sound(p.getLocation(), "entity.wither.spawn", 0.6f, 1.6f);
        Fx.ring(p.getLocation(), 1.5, "SOUL_FIRE_FLAME", 30);
        Fx.particle(p.getLocation().add(0, 1, 0), "SOUL", 40, 0.5, 1, 0.5, 0.05);
        pl.msg.send(p, "contract-signed", "demon", pl.msg.plain(d.rarity.color() + d.name), "level", pd.contracts.get(d.id).level);
        if (announce && d.rarity.order() >= 4) {
            Bukkit.broadcast(pl.msg.component(pl.msg.raw("prefix") + pl.msg.raw("contract-broadcast"), "player", p.getName(), "demon", pl.msg.plain(d.rarity.color() + d.name)));
        }
    }

    // ---------- rituel de changement ----------
    private void startRitual(Player p, PlayerData pd, DemonDef d) {
        if (!available(p, d)) return;
        UUID id = p.getUniqueId();
        if (rituals.containsKey(id)) {
            pl.msg.send(p, "ritual-running");
            return;
        }
        Settings s = pl.settings;
        int needHeads = s.headsRequired + s.changeExtraHeads;
        int have = pl.heads.count(p);
        if (have < needHeads) {
            pl.msg.send(p, "change-need-heads", "need", needHeads, "have", have);
            return;
        }
        if (p.getLevel() < s.changeExtraLevels) {
            pl.msg.send(p, "change-need-levels", "need", s.changeExtraLevels);
            return;
        }
        Location start = p.getLocation().clone();
        pl.msg.send(p, "ritual-start", "seconds", s.ritualSeconds);
        Fx.sound(start, "block.beacon.activate", 1f, 0.6f);
        BukkitTask t = new BukkitRunnable() {
            int ticks = 0;

            @Override public void run() {
                if (!p.isOnline() || p.isDead()) {
                    rituals.remove(id);
                    cancel();
                    return;
                }
                if (s.ritualCancelOnMove && p.getLocation().getWorld().equals(start.getWorld()) && p.getLocation().distanceSquared(start) > 2.25) {
                    interrupt(p, "ritual-interrupted-move");
                    return;
                }
                ticks += 10;
                Fx.ring(start, 1.6, "SOUL_FIRE_FLAME", 18);
                Fx.particle(start.clone().add(0, 0.2 + (ticks % 40) / 20.0, 0), "ENCHANT", 12, 0.6, 0.2, 0.6, 0.3);
                if (ticks % 20 == 0) pl.msg.feedback(p, "ritual-countdown", "seconds", Math.max(0, s.ritualSeconds - ticks / 20));
                if (ticks >= s.ritualSeconds * 20) {
                    rituals.remove(id);
                    cancel();
                    finishRitual(p, d, needHeads);
                }
            }
        }.runTaskTimer(pl, 10, 10);
        rituals.put(id, t);
    }

    private void finishRitual(Player p, DemonDef d, int needHeads) {
        PlayerData pd = pl.players.get(p);
        if (pd == null) return;
        if (p.getLevel() < pl.settings.changeExtraLevels || !pl.heads.consume(p, needHeads)) {
            pl.msg.send(p, "change-failed");
            return;
        }
        p.setLevel(p.getLevel() - pl.settings.changeExtraLevels);
        pl.cooldowns.clear(p.getUniqueId());
        pl.combat.clear(p.getUniqueId());
        apply(p, pd, d, true);
    }

    public void interrupt(Player p, String key) {
        BukkitTask t = rituals.remove(p.getUniqueId());
        if (t == null) return;
        t.cancel();
        pl.msg.send(p, key);
        Fx.sound(p.getLocation(), "block.beacon.deactivate", 1f, 1f);
    }

    public void cancelAll() {
        for (BukkitTask t : rituals.values()) t.cancel();
        rituals.clear();
    }

    // ---------- administration ----------
    public void give(Player p, DemonDef d) {
        PlayerData pd = pl.players.ensure(p);
        if (pd != null) apply(p, pd, d, false);
    }

    public void removeActive(Player p) {
        PlayerData pd = pl.players.get(p);
        if (pd == null) return;
        pd.activeDemon = null;
        pl.passives.refresh(p);
        pl.cooldowns.clear(p.getUniqueId());
        pl.players.save(p.getUniqueId());
    }

    public void resetPlayer(Player p) {
        PlayerData pd = pl.players.get(p);
        if (pd == null) return;
        pd.contracts.clear();
        pd.activeDemon = null;
        pd.blood = 0;
        pl.passives.refresh(p);
        pl.cooldowns.clear(p.getUniqueId());
        pl.players.save(p.getUniqueId());
    }

    public boolean setLevel(Player p, int level) {
        PlayerData pd = pl.players.get(p);
        PlayerData.Contract c = pd == null ? null : pd.active();
        if (c == null) return false;
        c.level = Math.max(1, Math.min(level, pl.settings.maxLevel));
        c.xp = 0;
        pl.players.save(p.getUniqueId());
        return true;
    }

    // ---------- progression ----------
    public long xpToNext(int level) { return Math.max(1, (long) (pl.settings.xpBase * Math.pow(level, pl.settings.xpExp))); }

    public void addXp(Player p, long amount) {
        PlayerData pd = pl.players.get(p);
        PlayerData.Contract c = pd == null ? null : pd.active();
        if (c == null || amount <= 0 || c.level >= pl.settings.maxLevel) return;
        c.xp += amount;
        boolean up = false;
        while (c.level < pl.settings.maxLevel && c.xp >= xpToNext(c.level)) {
            c.xp -= xpToNext(c.level);
            c.level++;
            up = true;
        }
        if (c.level >= pl.settings.maxLevel) c.xp = 0;
        if (up) {
            pl.msg.send(p, "level-up", "level", c.level);
            Fx.soundTo(p, "entity.player.levelup", 1f, 0.8f);
            Fx.particle(p.getLocation().add(0, 1, 0), "TOTEM_OF_UNDYING", 30, 0.4, 0.8, 0.4, 0.2);
        }
    }
}
