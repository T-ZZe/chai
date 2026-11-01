package fr.chainsawrpg.item;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.Settings;
import fr.chainsawrpg.data.DatabaseManager.HeadLog;
import fr.chainsawrpg.data.DatabaseManager.HeadRecord;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Têtes de joueurs : unicité, anti-duplication et anti-farm.
 * Une tête n'est valable que si son identifiant figure dans le registre des têtes ACTIVES (mémoire + base).
 * Consommer une tête la retire du registre : une copie dupliquée devient invalide et est purgée.
 */
public final class HeadManager {
    public static final UUID ADMIN_VICTIM = UUID.nameUUIDFromBytes("chainsawrpg-admin".getBytes());
    private static final long DAY = 86_400_000L;

    private final ChainsawPlugin pl;
    private final Map<UUID, HeadRecord> active = new HashMap<>();
    private final Map<UUID, Long> victimLast = new HashMap<>();
    private final Map<String, Long> pairLast = new HashMap<>();
    private final Map<UUID, Deque<Long>> killerDaily = new HashMap<>();

    public HeadManager(ChainsawPlugin pl) { this.pl = pl; }

    public void load() throws Exception {
        active.clear();
        active.putAll(pl.db.loadActiveHeads());
        long now = System.currentTimeMillis();
        for (HeadLog l : pl.db.loadHeadLog(now - 2 * DAY)) record(l.victim(), l.killer(), l.ts());
        pl.db.purgeHeadLog(now - 7 * DAY);
        pl.getLogger().info(active.size() + " tete(s) active(s) enregistree(s).");
    }

    private void record(UUID victim, UUID killer, long ts) {
        victimLast.merge(victim, ts, Math::max);
        pairLast.merge(killer + ":" + victim, ts, Math::max);
        killerDaily.computeIfAbsent(killer, k -> new ArrayDeque<>()).addLast(ts);
    }

    // ---------- obtention ----------
    /** Appelé quand killer tue victim (PlayerDeathEvent). Toutes les vérifications sont côté serveur. */
    public void onKill(Player killer, Player victim) {
        Settings s = pl.settings;
        if (!s.headsEnabled || killer.equals(victim)) return;
        String denied = check(killer, victim);
        if (denied != null) {
            if (s.headsNotifyDenied) pl.msg.send(killer, "head-denied-" + denied, "victim", victim.getName());
            return;
        }
        long now = System.currentTimeMillis();
        HeadRecord rec = new HeadRecord(UUID.randomUUID(), victim.getUniqueId(), victim.getName(), killer.getUniqueId(), now);
        active.put(rec.id(), rec);
        record(victim.getUniqueId(), killer.getUniqueId(), now);
        pl.db.insertHead(rec);
        pl.db.logHead(victim.getUniqueId(), killer.getUniqueId(), now);
        deliver(killer, pl.items.createHead(rec), victim.getLocation());
        pl.msg.send(killer, "head-obtained", "victim", victim.getName());
    }

    private String check(Player killer, Player victim) {
        Settings s = pl.settings;
        if (s.headsDisabledWorlds.contains(victim.getWorld().getName())) return "world";
        if (pl.protection.inSpawn(victim.getLocation()) || pl.protection.inSpawn(killer.getLocation())) return "spawn";
        if (s.headsBlockSameIp && sameIp(killer, victim)) return "same-ip";
        var vd = pl.players.get(victim);
        if (vd == null || vd.totalPlaytimeMs() < s.headsMinVictimMinutes * 60_000L) return "new-account";
        long now = System.currentTimeMillis();
        Long v = victimLast.get(victim.getUniqueId());
        if (v != null && now - v < s.headsVictimCooldownMin * 60_000L) return "victim-cooldown";
        Long pr = pairLast.get(killer.getUniqueId() + ":" + victim.getUniqueId());
        if (pr != null && now - pr < s.headsPairCooldownH * 3_600_000L) return "pair-cooldown";
        Deque<Long> q = killerDaily.computeIfAbsent(killer.getUniqueId(), k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > DAY) q.pollFirst();
        if (s.headsMaxPerDay > 0 && q.size() >= s.headsMaxPerDay) return "daily-cap";
        return null;
    }

    private boolean sameIp(Player a, Player b) {
        if (a.getAddress() == null || b.getAddress() == null) return false;
        InetAddress x = a.getAddress().getAddress(), y = b.getAddress().getAddress();
        return x != null && x.equals(y);
    }

    private void deliver(Player to, ItemStack head, Location at) {
        if (pl.settings.headsDirect) {
            Map<Integer, ItemStack> left = to.getInventory().addItem(head);
            for (ItemStack it : left.values()) {
                Item drop = to.getWorld().dropItem(to.getLocation(), it);
                drop.setOwner(to.getUniqueId());
            }
        } else {
            Item drop = at.getWorld().dropItem(at, head);
            drop.setOwner(to.getUniqueId());
        }
    }

    // ---------- inventaire ----------
    /** Retire les objets "tête" invalides (forgés, dupliqués, déjà consommés). Retourne le nombre purgé. */
    public int purgeInvalid(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();
        Set<UUID> seen = new HashSet<>();
        int purged = 0;
        for (int i = 0; i < c.length; i++) {
            ItemStack it = c[i];
            if (it == null || !pl.items.is(it, ItemManager.T_HEAD)) continue;
            UUID id = pl.items.headId(it);
            if (id == null || !active.containsKey(id) || it.getAmount() != 1 || !seen.add(id)) {
                inv.setItem(i, null);
                purged++;
            }
        }
        if (purged > 0) pl.getLogger().warning("Purge de " + purged + " tete(s) invalide(s) chez " + p.getName() + " (duplication/forgerie suspectee).");
        return purged;
    }

    public int count(Player p) {
        purgeInvalid(p);
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (pl.items.is(it, ItemManager.T_HEAD)) n++;
        return n;
    }

    /** Consomme exactement n têtes valides, ou rien. */
    public boolean consume(Player p, int n) {
        if (n <= 0) return true;
        purgeInvalid(p);
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < c.length && slots.size() < n; i++) if (pl.items.is(c[i], ItemManager.T_HEAD)) slots.add(i);
        if (slots.size() < n) return false;
        for (int slot : slots) {
            UUID id = pl.items.headId(c[slot]);
            inv.setItem(slot, null);
            active.remove(id);
            pl.db.consumeHead(id, p.getUniqueId());
        }
        return true;
    }

    // ---------- admin ----------
    public int give(Player target, int amount) {
        int n = Math.max(0, Math.min(amount, 64));
        for (int i = 0; i < n; i++) {
            HeadRecord rec = new HeadRecord(UUID.randomUUID(), ADMIN_VICTIM, "Admin", ADMIN_VICTIM, System.currentTimeMillis());
            active.put(rec.id(), rec);
            pl.db.insertHead(rec);
            deliver(target, pl.items.createHead(rec), target.getLocation());
        }
        return n;
    }

    public int remove(Player target, int amount) {
        PlayerInventory inv = target.getInventory();
        ItemStack[] c = inv.getContents();
        int removed = 0;
        for (int i = 0; i < c.length && removed < amount; i++) {
            if (!pl.items.is(c[i], ItemManager.T_HEAD)) continue;
            UUID id = pl.items.headId(c[i]);
            inv.setItem(i, null);
            if (id != null && active.remove(id) != null) pl.db.consumeHead(id, null);
            removed++;
        }
        return removed;
    }
}
