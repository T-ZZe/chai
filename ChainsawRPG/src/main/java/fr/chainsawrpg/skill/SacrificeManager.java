package fr.chainsawrpg.skill;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.data.PlayerData;
import fr.chainsawrpg.demon.Cost;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/** Coûts d'utilisation des compétences. check() ne modifie rien ; pay() applique le coût. */
public final class SacrificeManager {
    private final ChainsawPlugin pl;

    public SacrificeManager(ChainsawPlugin pl) { this.pl = pl; }

    /** @return clé de message d'erreur, ou null si le coût peut être payé. */
    public String check(Player p, PlayerData d, Cost c) {
        if (c == null || c.amount() <= 0 || c.type().equals("NONE")) return null;
        switch (c.type()) {
            case "HEALTH": return p.getHealth() > c.amount() ? null : "cost-health";
            case "FOOD": return p.getFoodLevel() >= c.amount() ? null : "cost-food";
            case "EXP": return totalXp(p) >= c.amount() ? null : "cost-exp";
            case "BLOOD": return d != null && d.blood >= c.amount() ? null : "cost-blood";
            case "DURABILITY": {
                ItemStack it = p.getInventory().getItemInMainHand();
                if (it.getType().isAir() || it.getType().getMaxDurability() <= 0) return "cost-durability";
                ItemMeta m = it.getItemMeta();
                if (!(m instanceof Damageable dm)) return "cost-durability";
                return it.getType().getMaxDurability() - dm.getDamage() > c.amount() ? null : "cost-durability";
            }
            default: return null;
        }
    }

    public void pay(Player p, PlayerData d, Cost c) {
        if (c == null || c.amount() <= 0) return;
        switch (c.type()) {
            case "HEALTH": p.setHealth(Math.max(0.5, p.getHealth() - c.amount())); break;
            case "FOOD": p.setFoodLevel(Math.max(0, p.getFoodLevel() - (int) Math.ceil(c.amount()))); break;
            case "EXP": setTotalXp(p, totalXp(p) - (int) Math.ceil(c.amount())); break;
            case "BLOOD": pl.blood.take(d, (int) Math.ceil(c.amount())); break;
            case "DURABILITY": {
                ItemStack it = p.getInventory().getItemInMainHand();
                if (it.getItemMeta() instanceof Damageable dm) {
                    dm.setDamage(dm.getDamage() + (int) Math.ceil(c.amount()));
                    it.setItemMeta(dm);
                }
                break;
            }
            default: break;
        }
    }

    public String describe(Cost c) {
        if (c == null || c.amount() <= 0 || c.type().equals("NONE")) return "<green>Gratuit";
        String n = c.amount() == Math.floor(c.amount()) ? String.valueOf((int) c.amount()) : String.valueOf(c.amount());
        return switch (c.type()) {
            case "HEALTH" -> "<red>" + n + " PV";
            case "FOOD" -> "<gold>" + n + " faim";
            case "EXP" -> "<green>" + n + " XP";
            case "BLOOD" -> "<dark_red>" + n + " sang";
            case "DURABILITY" -> "<gray>" + n + " durabilite";
            default -> "<gray>" + n;
        };
    }

    // ---- experience vanilla (points totaux) ----
    private static int xpAtLevel(int l) {
        if (l <= 16) return l * l + 6 * l;
        if (l <= 31) return (int) (2.5 * l * l - 40.5 * l + 360);
        return (int) (4.5 * l * l - 162.5 * l + 2220);
    }

    private static int xpForNext(int l) {
        if (l >= 31) return 9 * l - 158;
        if (l >= 16) return 5 * l - 38;
        return 2 * l + 7;
    }

    public static int totalXp(Player p) { return xpAtLevel(p.getLevel()) + Math.round(p.getExp() * xpForNext(p.getLevel())); }

    public static void setTotalXp(Player p, int total) {
        p.setLevel(0);
        p.setExp(0f);
        if (total > 0) p.giveExp(total);
    }
}
