package fr.chainsawrpg.listener;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.data.PlayerData;
import fr.chainsawrpg.demon.PassiveProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;

/** Cycle de vie des joueurs, têtes, protections d'objets, PNJ, immobilisation, gains de sang/XP. */
public final class CoreListener implements Listener {
    private final ChainsawPlugin pl;

    public CoreListener(ChainsawPlugin pl) { this.pl = pl; }

    // ---------- connexion ----------
    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        try {
            pl.players.load(e.getUniqueId(), e.getName());
        } catch (Exception ex) {
            pl.getLogger().log(Level.SEVERE, "Chargement des donnees de " + e.getName(), ex);
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, pl.msg.component("<red>Tes données ne sont pas disponibles. Réessaie dans un instant."));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData pd = pl.players.ensure(p);
        if (pd == null) return;
        pd.name = p.getName();
        pl.heads.purgeInvalid(p);
        pl.passives.refresh(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        pl.contracts.interrupt(p, "ritual-interrupted-quit");
        pl.passives.clear(p);
        pl.status.clearAll(p);
        pl.combat.clear(p.getUniqueId());
        pl.skills.forget(p.getUniqueId());
        pl.players.unload(p.getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(pl, () -> {
            if (p.isOnline()) pl.passives.refresh(p);
        });
    }

    // ---------- morts ----------
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        Player k = v.getKiller();
        pl.status.clearAll(v);
        pl.combat.clear(v.getUniqueId());
        pl.contracts.interrupt(v, "ritual-interrupted-death");
        PlayerData vd = pl.players.get(v);
        if (vd != null) {
            int lost = pl.blood.loseOnDeath(vd);
            if (lost > 0) pl.msg.send(v, "blood-lost", "amount", lost);
        }
        if (k != null && !k.equals(v)) {
            pl.heads.onKill(k, v);
            PlayerData kd = pl.players.get(k);
            if (kd != null) {
                kd.pvpWins++;
                pl.blood.add(kd, pl.settings.bloodPerKill);
                pl.contracts.addXp(k, 25);
            }
            healOnKill(k);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        LivingEntity ent = e.getEntity();
        if (ent instanceof Player) return;
        pl.status.clearAll(ent);
        Player k = ent.getKiller();
        if (k == null) return;
        PlayerData kd = pl.players.get(k);
        if (kd == null) return;
        if (ent instanceof Enemy) {
            kd.mobKills++;
            pl.blood.add(kd, pl.settings.bloodPerMob);
            pl.contracts.addXp(k, pl.settings.xpPerMob);
            healOnKill(k);
        }
    }

    private void healOnKill(Player k) {
        PassiveProfile pp = pl.passives.profile(k);
        if (pp == null || pp.healOnKill <= 0 || k.isDead()) return;
        double max = fr.chainsawrpg.util.Mc.maxHealth(k);
        k.setHealth(Math.min(max, k.getHealth() + pp.healOnKill));
    }

    // ---------- rituel ----------
    @EventHandler(ignoreCancelled = true)
    public void onDamageRitual(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getFinalDamage() > 0 && pl.contracts.inRitual(p.getUniqueId())) {
            if (pl.settings.ritualCancelOnMove) pl.contracts.interrupt(p, "ritual-interrupted-damage");
        }
    }

    // ---------- objets ----------
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (pl.items.isPluginItem(e.getItemInHand())) e.setCancelled(true);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (it != null && pl.items.isPluginItem(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    // ---------- PNJ ----------
    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent e) {
        if (!pl.npcs.isNpc(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) pl.npcs.interact(e.getPlayer(), e.getRightClicked());
    }

    // ---------- immobilisation ----------
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!pl.status.isRooted(e.getPlayer())) return;
        Location f = e.getFrom(), t = e.getTo();
        if (t == null) return;
        if (f.getX() != t.getX() || f.getZ() != t.getZ() || t.getY() > f.getY()) {
            Location n = f.clone();
            n.setYaw(t.getYaw());
            n.setPitch(t.getPitch());
            e.setTo(n);
        }
    }

    // ---------- blocs temporaires ----------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (pl.tempBlocks.isTemp(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> pl.tempBlocks.isTemp(b));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> pl.tempBlocks.isTemp(b));
    }
}
