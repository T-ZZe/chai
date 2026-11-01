package fr.chainsawrpg;

import fr.chainsawrpg.demon.DemonDef;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Opérations administratives (appelées par la commande /chainsaw). */
public final class AdminManager {
    private final ChainsawPlugin pl;

    public AdminManager(ChainsawPlugin pl) { this.pl = pl; }

    public void giveHeads(CommandSender by, Player target, int amount) {
        int n = pl.heads.give(target, amount);
        pl.msg.send(by, "heads-given", "amount", n, "player", target.getName());
    }

    public void removeHeads(CommandSender by, Player target, int amount) {
        int n = pl.heads.remove(target, amount);
        pl.msg.send(by, "heads-removed", "amount", n, "player", target.getName());
    }

    public void giveContract(CommandSender by, Player target, String demonId) {
        DemonDef d = demonId.equalsIgnoreCase("random") ? pl.demons.roll() : pl.demons.get(demonId.toLowerCase());
        if (d == null || !d.enabled) {
            pl.msg.send(by, "unknown-demon", "demon", demonId);
            return;
        }
        pl.contracts.give(target, d);
        pl.msg.send(by, "contract-given", "player", target.getName(), "demon", pl.msg.plain(d.rarity.color() + d.name));
    }

    public void removeContract(CommandSender by, Player target) {
        pl.contracts.removeActive(target);
        pl.msg.send(by, "contract-removed", "player", target.getName());
    }

    public void reset(CommandSender by, Player target) {
        pl.contracts.resetPlayer(target);
        pl.msg.send(by, "player-reset", "player", target.getName());
    }

    public void setLevel(CommandSender by, Player target, int level) {
        if (pl.contracts.setLevel(target, level)) pl.msg.send(by, "level-set", "player", target.getName(), "level", level);
        else pl.msg.send(by, "target-no-contract", "player", target.getName());
    }
}
