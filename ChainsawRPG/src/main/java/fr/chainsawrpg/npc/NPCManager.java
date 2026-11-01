package fr.chainsawrpg.npc;

import fr.chainsawrpg.ChainsawPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

/** PNJ : Marchand de contrats (villageois figé, invulnérable, persistant, identifié par PDC). */
public final class NPCManager {
    public static final String CONTRACT_MERCHANT = "contract_merchant";
    private final ChainsawPlugin pl;

    public NPCManager(ChainsawPlugin pl) { this.pl = pl; }

    public boolean isNpc(Entity e) { return e != null && e.getPersistentDataContainer().has(pl.kNpc, PersistentDataType.STRING); }

    public Villager spawn(Location l) {
        return l.getWorld().spawn(l, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setCollidable(false);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.customName(pl.msg.get("npc-name"));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(pl.kNpc, PersistentDataType.STRING, CONTRACT_MERCHANT);
        });
    }

    public int removeNear(Location l, double radius) {
        int n = 0;
        for (Entity e : l.getWorld().getNearbyEntities(l, radius, radius, radius)) {
            if (isNpc(e)) {
                e.remove();
                n++;
            }
        }
        return n;
    }

    public void interact(Player p, Entity npc) {
        if (!p.hasPermission("chainsawman.use")) {
            pl.msg.send(p, "no-permission");
            return;
        }
        if (CONTRACT_MERCHANT.equals(npc.getPersistentDataContainer().get(pl.kNpc, PersistentDataType.STRING))) pl.gui.openContracts(p, 0, false);
    }
}
