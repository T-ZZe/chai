package fr.chainsawrpg.command;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.data.PlayerData;
import fr.chainsawrpg.demon.DemonDef;
import fr.chainsawrpg.item.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ChainsawCommand implements CommandExecutor, TabCompleter {
    private static final List<String> PLAYER_SUBS = List.of("contrat", "demons", "skills", "heads", "stats", "shop", "cast", "book", "help");
    private static final List<String> ADMIN_SUBS = List.of("admin", "giveheads", "removeheads", "givecontract", "removecontract", "reset", "reload", "setlevel", "npc");

    private final ChainsawPlugin pl;

    public ChainsawCommand(ChainsawPlugin pl) { this.pl = pl; }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("chainsawman.admin")) return true;
        pl.msg.send(s, "no-permission");
        return false;
    }

    private Player online(CommandSender s, String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null) pl.msg.send(s, "player-not-found", "player", name);
        return t;
    }

    private static Integer num(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player pp ? pp : null;
        switch (sub) {
            case "menu" -> {
                if (p == null) return needPlayer(sender);
                pl.gui.openMain(p);
            }
            case "help" -> {
                pl.msg.send(sender, "help-header");
                for (String line : pl.msg.raw("help-lines").split("\\|")) sender.sendMessage(pl.msg.component(line));
                if (sender.hasPermission("chainsawman.admin")) for (String line : pl.msg.raw("help-admin-lines").split("\\|")) sender.sendMessage(pl.msg.component(line));
            }
            case "contrat", "contract" -> {
                if (p == null) return needPlayer(sender);
                PlayerData pd = pl.players.ensure(p);
                DemonDef d = pd == null ? null : pl.demons.get(pd.activeDemon);
                if (d == null) {
                    pl.msg.send(p, "no-contract");
                    return true;
                }
                PlayerData.Contract c = pd.active();
                pl.msg.send(p, "contract-info", "demon", pl.msg.plain(d.rarity.color() + d.name), "rarity", pl.msg.plain(d.rarity.tag()),
                        "level", c.level, "xp", c.xp, "next", c.level >= pl.settings.maxLevel ? "MAX" : String.valueOf(pl.contracts.xpToNext(c.level)));
            }
            case "demons" -> {
                if (p == null) return needPlayer(sender);
                pl.gui.openContracts(p, 0, true);
            }
            case "skills" -> {
                if (p == null) return needPlayer(sender);
                pl.gui.openSkills(p);
            }
            case "heads" -> {
                if (p == null) return needPlayer(sender);
                pl.msg.send(p, "heads-count", "amount", pl.heads.count(p), "need", pl.settings.headsRequired);
            }
            case "stats" -> {
                if (p == null) return needPlayer(sender);
                pl.gui.openStats(p);
            }
            case "shop" -> {
                if (p == null) return needPlayer(sender);
                if (pl.settings.requireNpc && !p.hasPermission("chainsawman.bypass.npc")) pl.msg.send(p, "need-npc");
                else pl.gui.openContracts(p, 0, false);
            }
            case "cast" -> {
                if (p == null) return needPlayer(sender);
                Integer n = a.length > 1 ? num(a[1]) : null;
                if (n == null || n < 1 || n > 4) pl.msg.send(p, "usage", "usage", "/chainsaw cast <1-4>");
                else pl.skills.cast(p, n);
            }
            case "book" -> {
                if (p == null) return needPlayer(sender);
                PlayerData pd = pl.players.get(p);
                if (pd == null || pd.activeDemon == null) {
                    pl.msg.send(p, "no-contract");
                    return true;
                }
                for (ItemStack it : p.getInventory().getContents()) {
                    if (pl.items.is(it, ItemManager.T_BOOK)) {
                        pl.msg.send(p, "book-already");
                        return true;
                    }
                }
                p.getInventory().addItem(pl.items.book()).values().forEach(it -> p.getWorld().dropItem(p.getLocation(), it));
            }
            case "admin" -> {
                if (p == null) return needPlayer(sender);
                pl.gui.openAdmin(p);
            }
            case "giveheads", "removeheads" -> {
                if (!admin(sender)) return true;
                Integer n = a.length > 2 ? num(a[2]) : null;
                if (a.length < 3 || n == null || n < 1) {
                    pl.msg.send(sender, "usage", "usage", "/chainsaw " + sub + " <joueur> <quantite>");
                    return true;
                }
                Player t = online(sender, a[1]);
                if (t == null) return true;
                if (sub.equals("giveheads")) pl.admin.giveHeads(sender, t, n);
                else pl.admin.removeHeads(sender, t, n);
            }
            case "givecontract" -> {
                if (!admin(sender)) return true;
                if (a.length < 3) {
                    pl.msg.send(sender, "usage", "usage", "/chainsaw givecontract <joueur> <demon|random>");
                    return true;
                }
                Player t = online(sender, a[1]);
                if (t != null) pl.admin.giveContract(sender, t, a[2]);
            }
            case "removecontract", "reset" -> {
                if (!admin(sender)) return true;
                if (a.length < 2) {
                    pl.msg.send(sender, "usage", "usage", "/chainsaw " + sub + " <joueur>");
                    return true;
                }
                Player t = online(sender, a[1]);
                if (t == null) return true;
                if (sub.equals("reset")) pl.admin.reset(sender, t);
                else pl.admin.removeContract(sender, t);
            }
            case "setlevel" -> {
                if (!admin(sender)) return true;
                Integer n = a.length > 2 ? num(a[2]) : null;
                if (n == null) {
                    pl.msg.send(sender, "usage", "usage", "/chainsaw setlevel <joueur> <niveau>");
                    return true;
                }
                Player t = online(sender, a[1]);
                if (t != null) pl.admin.setLevel(sender, t, n);
            }
            case "reload" -> {
                if (!admin(sender)) return true;
                pl.reloadAll();
                pl.msg.send(sender, "reloaded");
            }
            case "npc" -> {
                if (!admin(sender)) return true;
                if (p == null) return needPlayer(sender);
                String act = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
                if (act.equals("spawn")) {
                    pl.npcs.spawn(p.getLocation());
                    pl.msg.send(p, "npc-spawned");
                } else if (act.equals("remove")) {
                    pl.msg.send(p, "npc-removed", "amount", pl.npcs.removeNear(p.getLocation(), 4));
                } else {
                    pl.msg.send(p, "usage", "usage", "/chainsaw npc <spawn|remove>");
                }
            }
            default -> pl.msg.send(sender, "unknown-subcommand");
        }
        return true;
    }

    private boolean needPlayer(CommandSender s) {
        pl.msg.send(s, "players-only");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(PLAYER_SUBS);
            if (s.hasPermission("chainsawman.admin")) out.addAll(ADMIN_SUBS);
        } else if (s.hasPermission("chainsawman.admin")) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (a.length == 2 && Arrays.asList("giveheads", "removeheads", "givecontract", "removecontract", "reset", "setlevel").contains(sub)) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else if (a.length == 3 && sub.equals("givecontract")) {
                out.add("random");
                for (DemonDef d : pl.demons.all()) if (d.enabled) out.add(d.id);
            } else if (a.length == 2 && sub.equals("npc")) {
                out.addAll(List.of("spawn", "remove"));
            }
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("cast")) out.addAll(List.of("1", "2", "3", "4"));
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
