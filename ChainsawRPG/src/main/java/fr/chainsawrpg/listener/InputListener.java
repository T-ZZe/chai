package fr.chainsawrpg.listener;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.data.PlayerData;
import fr.chainsawrpg.item.ItemManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Lancement des compétences : Shift + touche 1-4, ou Livre des contrats (clic droit = lancer, Shift+clic droit = changer). */
public final class InputListener implements Listener {
    private final ChainsawPlugin pl;

    public InputListener(ChainsawPlugin pl) { this.pl = pl; }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        if (!pl.settings.castSneak) return;
        Player p = e.getPlayer();
        int ns = e.getNewSlot();
        if (!p.isSneaking() || ns > 3) return;
        PlayerData pd = pl.players.get(p);
        if (pd == null || pd.activeDemon == null) return;
        e.setCancelled(true);
        pl.skills.cast(p, ns + 1);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !pl.items.is(e.getItem(), ItemManager.T_BOOK)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Action a = e.getAction();
        if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            pl.gui.openMain(p);
        } else if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            if (!pl.settings.castBook) return;
            if (p.isSneaking()) {
                int n = pl.skills.cycle(p.getUniqueId());
                pl.msg.feedback(p, "skill-selected", "slot", n);
            } else {
                pl.skills.cast(p, pl.skills.selected(p.getUniqueId()));
            }
        }
    }
}
